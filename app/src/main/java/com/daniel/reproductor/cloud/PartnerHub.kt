package com.daniel.reproductor.cloud

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.playback.PlaybackItems
import com.daniel.reproductor.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

enum class ListenMode { NONE, INVITING, INVITED, ACTIVE }

data class ListenSession(val mode: ListenMode = ListenMode.NONE, val iAmDj: Boolean = false) {
    val active get() = mode == ListenMode.ACTIVE
    val following get() = active && !iAmDj
}

sealed interface HubEvent {
    data class IncomingDedication(val dedication: Dedication) : HubEvent
    data class LiveReaction(val emoji: String, val fromMe: Boolean) : HubEvent
    data class Message(val text: String) : HubEvent
}

/**
 * Todo lo de "Juntos": pareja, dedicatorias, reacciones y escuchar juntos en tiempo real.
 *
 * La conexión en vivo (WebSocket) se mantiene mientras la app está abierta o mientras hay una
 * sesión de "escuchar juntos". En esa sesión quien controla (DJ) envía qué suena y en qué
 * segundo; el otro celular reproduce lo mismo y corrige el desfase.
 */
class PartnerHub(
    private val context: Context,
    baseClient: OkHttpClient,
    private val api: CloudApi,
    private val session: Session,
    private val files: MusicFiles,
    private val scope: CoroutineScope,
) {
    private val client = baseClient.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()

    private val _partner = MutableStateFlow<PartnerStatus?>(null)
    val partner: StateFlow<PartnerStatus?> = _partner.asStateFlow()

    private val _dedications = MutableStateFlow<List<Dedication>>(emptyList())
    val dedications: StateFlow<List<Dedication>> = _dedications.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _listen = MutableStateFlow(ListenSession())
    val listen: StateFlow<ListenSession> = _listen.asStateFlow()

    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    private val foreground = MutableStateFlow(false)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var djJob: Job? = null
    private var latencyMs = 150L

    init {
        // Conectado solo cuando hace falta: sesión iniciada y (app abierta o escuchando juntos)
        scope.launch {
            combine(session.account, foreground, _listen) { account, fg, listen ->
                account != null && (fg || listen.mode != ListenMode.NONE)
            }.distinctUntilChanged().collect { wanted -> if (wanted) connect() else disconnect() }
        }
        scope.launch {
            session.account.collect { account ->
                if (account == null) {
                    _partner.value = null
                    _dedications.value = emptyList()
                    _listen.value = ListenSession()
                } else {
                    refresh()
                }
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value) refresh()
    }

    /** Vuelve a pedir pareja y dedicatorias al servidor. */
    fun refresh() {
        if (session.account.value == null) return
        scope.launch {
            runCatching { _partner.value = api.partner() }
            runCatching { _dedications.value = api.dedications() }
        }
    }

    // ---------- Pareja ----------

    suspend fun requestPartner(username: String) { _partner.value = api.requestPartner(username.trim()) }
    suspend fun acceptPartner() { _partner.value = api.acceptPartner() }
    suspend fun unlinkPartner() {
        leaveListen()
        _partner.value = api.unlinkPartner()
    }

    // ---------- Dedicatorias ----------

    /** Devuelve true si le llegó al instante (tenía la app abierta). */
    suspend fun sendDedication(song: Song, message: String, autoplay: Boolean): Boolean {
        val (dedication, delivered) = api.sendDedication(song, message, autoplay)
        _dedications.update { listOf(dedication) + it }
        return delivered
    }

    fun markSeen(dedication: Dedication) {
        _dedications.update { list -> list.map { if (it.id == dedication.id) it.copy(seenAt = System.currentTimeMillis()) else it } }
        scope.launch { runCatching { api.markSeen(dedication.id) } }
    }

    fun react(dedication: Dedication, emoji: String) {
        _events.tryEmit(HubEvent.LiveReaction(emoji, fromMe = true))
        scope.launch {
            runCatching { api.react(dedication.id, emoji) }.onSuccess(::upsert)
        }
    }

    /** Reproduce una dedicatoria en este celular. */
    fun playDedication(dedication: Dedication) {
        scope.launch { runCatching { playSong(dedication.song) } }
        if (dedication.incoming && dedication.seenAt == null) markSeen(dedication)
    }

    // ---------- Reacciones en vivo ----------

    fun sendLiveReaction(emoji: String) {
        _events.tryEmit(HubEvent.LiveReaction(emoji, fromMe = true))
        send(JSONObject().put("type", "reaction").put("emoji", emoji))
    }

    // ---------- Escuchar juntos ----------

    fun inviteListen() {
        _listen.value = ListenSession(ListenMode.INVITING)
        send(JSONObject().put("type", "listen.invite"))
    }

    fun acceptListen() {
        _listen.value = ListenSession(ListenMode.ACTIVE, iAmDj = false)
        send(JSONObject().put("type", "listen.join"))
    }

    /** Se tocó la notificación "quiere escuchar música contigo". */
    fun onListenInviteNotification() {
        if (_listen.value.mode == ListenMode.NONE) _listen.value = ListenSession(ListenMode.INVITED)
    }

    fun leaveListen() {
        if (_listen.value.mode == ListenMode.NONE) return
        send(JSONObject().put("type", "listen.leave"))
        _listen.value = ListenSession()
        stopDj()
    }

    /** El que escucha pide el control: ahora su celular manda. */
    fun takeControl() {
        if (!_listen.value.active) return
        send(JSONObject().put("type", "listen.takeControl"))
        _listen.value = ListenSession(ListenMode.ACTIVE, iAmDj = true)
        startDj()
    }

    // ---------- Conexión ----------

    private fun connect() {
        if (socket != null) return
        val token = session.account.value?.token ?: return
        val url = session.serverUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/ws"
        val request = try {
            Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        } catch (e: IllegalArgumentException) {
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                reconnectDelay = 2_000
                scope.launch { measureLatency(webSocket) }
                refresh()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                scope.launch(kotlinx.coroutines.Dispatchers.Main) { handle(msg) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onDisconnected(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response?.code == 401) session.signOut()
                onDisconnected(webSocket)
            }
        })
    }

    private var reconnectDelay = 2_000L

    private fun onDisconnected(ws: WebSocket) {
        if (socket !== ws) return
        socket = null
        _connected.value = false
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(30_000)
            val wanted = session.account.value != null && (foreground.value || _listen.value.mode != ListenMode.NONE)
            if (wanted) connect()
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        socket?.close(1000, null)
        socket = null
        _connected.value = false
    }

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }

    private var pingSentAt = 0L

    private suspend fun measureLatency(ws: WebSocket) {
        while (scope.isActive && socket === ws) {
            pingSentAt = System.currentTimeMillis()
            ws.send(JSONObject().put("type", "ping").put("t", pingSentAt).toString())
            delay(15_000)
        }
    }

    private suspend fun handle(msg: JSONObject) {
        when (msg.optString("type")) {
            "pong" -> latencyMs = ((System.currentTimeMillis() - msg.optLong("t")) / 2).coerceIn(0, 2_000)
            "hello", "partner.update" -> refresh()
            "presence" -> {
                val online = msg.optBoolean("online")
                _partner.update { p -> p?.copy(partner = p.partner?.copy(online = online)) }
                if (!online && _listen.value.mode != ListenMode.NONE) {
                    _events.tryEmit(HubEvent.Message("${partnerName()} se desconectó"))
                }
            }
            "dedication" -> {
                val d = CloudApi.parseDedication(msg.getJSONObject("dedication"))
                upsert(d)
                _events.tryEmit(HubEvent.IncomingDedication(d))
                if (d.autoplay) runCatching { playSong(d.song) }
            }
            "dedication.update" -> upsert(CloudApi.parseDedication(msg.getJSONObject("dedication")))
            "reaction" -> _events.tryEmit(HubEvent.LiveReaction(msg.optString("emoji"), fromMe = false))
            "listen.invite" -> {
                _listen.value = ListenSession(ListenMode.INVITED)
                _events.tryEmit(HubEvent.Message("${msg.optString("from")} quiere escuchar música contigo 🎧"))
            }
            "listen.join" -> if (_listen.value.mode == ListenMode.INVITING || _listen.value.active) {
                _listen.value = ListenSession(ListenMode.ACTIVE, iAmDj = true)
                _events.tryEmit(HubEvent.Message("${msg.optString("from")} se unió. ¡Están escuchando juntos!"))
                startDj()
            }
            "listen.leave" -> if (_listen.value.mode != ListenMode.NONE) {
                _listen.value = ListenSession()
                stopDj()
                _events.tryEmit(HubEvent.Message("${msg.optString("from")} salió de la sesión"))
            }
            "listen.takeControl" -> if (_listen.value.active) {
                _listen.value = ListenSession(ListenMode.ACTIVE, iAmDj = false)
                stopDj()
                _events.tryEmit(HubEvent.Message("Ahora ${msg.optString("from")} controla la música"))
            }
            "listen.state" -> if (_listen.value.following) follow(msg.getJSONObject("state"))
        }
    }

    private fun upsert(d: Dedication) {
        _dedications.update { list -> if (list.any { it.id == d.id }) list.map { if (it.id == d.id) d else it } else listOf(d) + list }
    }

    private fun partnerName() = _partner.value?.partner?.displayName ?: "Tu pareja"

    // ---------- Sincronización del reproductor ----------

    private var controller: MediaController? = null

    private suspend fun player(): MediaController {
        controller?.takeIf { it.isConnected }?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        return suspendCancellableCoroutine<MediaController> { cont ->
            future.addListener({
                runCatching { future.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) }
            }, ContextCompat.getMainExecutor(context))
        }.also { controller = it }
    }

    /** Reproduce una canción en este celular (dedicatorias). */
    suspend fun playSong(song: Song) {
        val p = player()
        p.setMediaItem(PlaybackItems.fromSong(song, files))
        p.prepare()
        p.play()
    }

    private val djListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                )
            ) broadcast(player)
        }
    }

    /** Como DJ: avisa cada cambio y cada 4 s para corregir el desfase del otro celular. */
    private fun startDj() {
        djJob?.cancel()
        djJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val p = runCatching { player() }.getOrNull() ?: return@launch
            p.addListener(djListener)
            try {
                while (isActive) {
                    broadcast(p)
                    delay(4_000)
                }
            } finally {
                p.removeListener(djListener)
            }
        }
    }

    private fun stopDj() {
        djJob?.cancel()
        djJob = null
    }

    private fun broadcast(p: Player) {
        val item = p.currentMediaItem ?: return
        val meta = p.mediaMetadata
        val state = JSONObject()
            .put("videoId", item.mediaId)
            .put("title", meta.title?.toString().orEmpty())
            .put("uploader", meta.artist?.toString().orEmpty())
            .put("durationSec", (p.duration.takeIf { it != C.TIME_UNSET } ?: 0L) / 1000)
            .put("thumbnailUrl", meta.artworkUri?.takeIf { it.scheme?.startsWith("http") == true }?.toString().orEmpty())
            .put("positionMs", p.currentPosition)
            .put("playing", p.playWhenReady)
        send(JSONObject().put("type", "listen.state").put("state", state))
    }

    /** Como oyente: reproduce lo mismo que el DJ y corrige si hay más de 1,5 s de diferencia. */
    private suspend fun follow(state: JSONObject) {
        val p = runCatching { player() }.getOrNull() ?: return
        val videoId = state.optString("videoId")
        if (videoId.isEmpty()) return
        val target = state.optLong("positionMs") + latencyMs
        val playing = state.optBoolean("playing")
        if (p.currentMediaItem?.mediaId != videoId) {
            val song = runCatching { CloudApi.parseSong(state) }.getOrNull() ?: return
            p.setMediaItem(PlaybackItems.fromSong(song, files), target)
            p.prepare()
        } else if (p.playbackState == Player.STATE_READY && abs(p.currentPosition - target) > 1_500) {
            p.seekTo(target)
        }
        p.playWhenReady = playing
    }

    /** Mensaje para la UI si este celular no puede controlar (sigue al otro). */
    fun lockedReason(): String? =
        if (_listen.value.following) "${partnerName()} controla la música. En Juntos puedes tomar el control." else null

    companion object {
        /** Lanza el error con un mensaje claro para la pantalla. */
        suspend fun <T> userAction(block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (e: UserFacingException) {
                Result.failure(e)
            }
    }
}
