package com.daniel.reproductor.ui.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.cloud.Account
import com.daniel.reproductor.cloud.AnniversaryGift
import com.daniel.reproductor.cloud.CloudApi
import com.daniel.reproductor.cloud.CloudSync
import com.daniel.reproductor.cloud.Capsule
import com.daniel.reproductor.cloud.CapsuleAnswer
import com.daniel.reproductor.cloud.DailyQuestion
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.JarNote
import com.daniel.reproductor.cloud.MinuContent
import com.daniel.reproductor.cloud.TouchKind
import com.daniel.reproductor.cloud.Memories
import com.daniel.reproductor.cloud.Mixtape
import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.HubEvent
import com.daniel.reproductor.cloud.ListenSession
import com.daniel.reproductor.cloud.PartnerHub
import com.daniel.reproductor.cloud.PartnerStatus
import com.daniel.reproductor.cloud.Push
import com.daniel.reproductor.cloud.Session
import com.daniel.reproductor.cloud.SyncStatus
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.app
import com.daniel.reproductor.ui.together.minu.MinuVoice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cuenta en la nube y todo lo de "Dúo". */
class TogetherViewModel(
    private val session: Session,
    private val api: CloudApi,
    private val sync: CloudSync,
    private val hub: PartnerHub,
    private val onSignedIn: suspend () -> Unit = {},
    private val onSigningOut: suspend () -> Unit = {},
) : ViewModel() {

    val account: StateFlow<Account?> = session.account
    val syncStatus: StateFlow<SyncStatus> = session.status
    val partner: StateFlow<PartnerStatus?> = hub.partner
    val dedications: StateFlow<List<Dedication>> = hub.dedications
    val listen: StateFlow<ListenSession> = hub.listen
    val connected: StateFlow<Boolean> = hub.connected
    val duo: StateFlow<DuoInfo?> = hub.duo
    val events: Flow<HubEvent> = hub.events

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var serverUrl: String
        get() = session.serverUrl
        set(value) { session.serverUrl = value }

    /** Ejecuta una acción mostrando "cargando" y el error en un mensaje. */
    private fun action(success: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
            success?.let { _messages.send(it) }
        } catch (e: UserFacingException) {
            _messages.send(e.message.orEmpty())
        } finally {
            _busy.value = false
        }
    }

    fun login(username: String, password: String) = action("¡Hola de nuevo! Sincronizando tu música…") {
        session.signIn(api.login(username.trim(), password))
        onSignedIn()
        sync.syncNow()
    }

    fun register(username: String, password: String, displayName: String, inviteCode: String) =
        action("Cuenta creada. Tu música ya está en la nube ☁️") {
            session.signIn(api.register(username.trim(), password, displayName.trim(), inviteCode.trim()))
            onSignedIn()
            sync.syncNow()
        }

    /** Con el token que entrega "Continuar con Google". */
    fun googleLogin(idToken: String) = action("¡Listo! Sincronizando tu música…") {
        session.signIn(api.googleLogin(idToken))
        onSignedIn()
        sync.syncNow()
    }

    fun showMessage(text: String) = viewModelScope.launch { _messages.send(text) }

    fun syncNow() = action("Sincronizado") { sync.syncNow() }

    fun logout() = viewModelScope.launch {
        hub.leaveListen()
        onSigningOut() // este celular deja de recibir notificaciones
        session.signOut()
        _messages.send("Sesión cerrada. Tu música sigue en este celular.")
    }

    fun requestPartner(username: String) = action { hub.requestPartner(username) }
    fun acceptPartner() = action("¡Ahora están vinculados! ❤️") { hub.acceptPartner() }
    fun unlinkPartner() = action { hub.unlinkPartner() }

    fun sendDedication(song: Song, message: String, autoplay: Boolean) = action {
        val delivered = hub.sendDedication(song, message, autoplay)
        _messages.send(
            when {
                delivered && autoplay -> "Está sonando en su celular ahora mismo 💘"
                delivered -> "Dedicatoria enviada 💌"
                else -> "Enviada. La verá cuando abra Minu 💌"
            }
        )
    }

    /** [startedOn] en AAAA-MM-DD. */
    fun saveOrigin(startedOn: String, song: Song?, story: String) =
        action("Guardado. Su hilo ya sabe desde cuándo los une 💞") { hub.saveOrigin(startedOn, song, story) }

    fun chooseRescue(type: String) = action {
        hub.chooseRescue(type)
        if (type == "capsule") _capsuleOpen.value = true
    }

    // ---------- Recuerdos: cápsula, Cinta del mes y misiones ----------

    private val _capsule = MutableStateFlow<Capsule?>(null)
    val capsule: StateFlow<Capsule?> = _capsule.asStateFlow()

    /** Pantalla de la cápsula abierta (se abre al elegir el reto o desde la tarjeta del rescate). */
    private val _capsuleOpen = MutableStateFlow(false)
    val capsuleOpen: StateFlow<Boolean> = _capsuleOpen.asStateFlow()
    fun showCapsule(open: Boolean) {
        _capsuleOpen.value = open
        if (open) loadCapsule()
    }

    fun loadCapsule() = viewModelScope.launch { runCatching { _capsule.value = api.capsule() } }

    fun sendCapsule(answers: List<CapsuleAnswer>) = action {
        val result = api.answerCapsule(answers)
        _capsule.value = result
        hub.refreshDuo()
        _messages.send(if (result.openedAt != null) "¡La cápsula se abrió! Salvaron el hilo ✨" else "Sellada 💌 Se abre cuando tu pareja responda.")
    }

    private val _memories = MutableStateFlow<Memories?>(null)
    val memories: StateFlow<Memories?> = _memories.asStateFlow()
    fun loadMemories() = viewModelScope.launch { runCatching { _memories.value = api.memories() } }

    private val _mixtape = MutableStateFlow<Mixtape?>(null)
    val mixtape: StateFlow<Mixtape?> = _mixtape.asStateFlow()
    fun loadMixtape(from: String, to: String) = viewModelScope.launch {
        _mixtape.value = null
        try {
            _mixtape.value = api.mixtape(from, to)
        } catch (e: UserFacingException) {
            _messages.send(e.message.orEmpty())
        }
    }

    /** Recuerdos abiertos desde fuera (el mesiversario): el número del mes cuya cinta se muestra. */
    private val _openTape = MutableStateFlow<Int?>(null)
    val openTape: StateFlow<Int?> = _openTape.asStateFlow()
    fun requestTape(month: Int?) { _openTape.value = month }

    fun markMission(month: Int, done: Boolean) = action(if (done) "Misión marcada 🎶" else null) { hub.markMission(month, done) }

    // ---------- Aniversario ----------

    /** El aniversario que está en pantalla (null = ninguno). */
    private val _anniversaryOpen = MutableStateFlow<Int?>(null)
    val anniversaryOpen: StateFlow<Int?> = _anniversaryOpen.asStateFlow()
    private val _anniversary = MutableStateFlow<AnniversaryGift?>(null)
    val anniversary: StateFlow<AnniversaryGift?> = _anniversary.asStateFlow()

    init {
        viewModelScope.launch {
            hub.events.collect { e ->
                if (e is HubEvent.AnniversaryChanged && e.years == _anniversaryOpen.value) loadAnniversary(e.years)
            }
        }
    }

    fun showAnniversary(years: Int?) {
        if (years != _anniversaryOpen.value) _anniversary.value = null
        _anniversaryOpen.value = years
        years?.let(::loadAnniversary)
    }

    fun loadAnniversary(years: Int) = viewModelScope.launch {
        runCatching { api.anniversary(years) }.onSuccess { if (_anniversaryOpen.value == years) _anniversary.value = it }
    }

    /** Un pulso del abrazo: mientras lo mantienen, la pantalla lo repite cada pocos segundos. */
    fun hug(years: Int) = viewModelScope.launch {
        try {
            _anniversary.value = api.hug(years)
        } catch (e: UserFacingException) {
            _messages.send(e.message.orEmpty())
        }
    }

    fun openAnniversaryAlone(years: Int) = action { _anniversary.value = api.openAnniversary(years); hub.refreshDuo() }
    fun voteSong(years: Int, videoId: String) = action { _anniversary.value = api.voteSong(years, videoId) }
    fun writeLetter(text: String) = action("Carta sellada hasta su próximo aniversario 💌") { hub.writeLetter(text) }

    // ---------- Minu, la mascota ----------

    fun acceptMinuTerms() = action { hub.acceptMinuTerms() }

    /** Proponer otro nombre ([kind] "name") u otra mascota ("species"); se aplica cuando tu pareja acepta. */
    fun proposePet(kind: String, value: String) = action("Listo, se lo propuse a tu pareja 💌") { hub.proposePet(kind, value) }
    fun acceptPetProposal(kind: String) = action { hub.acceptPetProposal(kind) }
    fun dropPetProposal(kind: String) = action { hub.dropPetProposal(kind) }
    fun eraseMinuData() = action("Borré lo que me contaste. Aquí estaré si vuelves 🧶") { hub.eraseMinuData() }

    // ---------- Conexión diaria con Minu ----------

    /** La animación del Toque que está en pantalla (Minu volando o los dos gestos encontrándose). */
    private val _touchFx = MutableStateFlow<TouchFx?>(null)
    val touchFx: StateFlow<TouchFx?> = _touchFx.asStateFlow()
    fun clearTouchFx() { _touchFx.value = null }
    fun showTouchMeet(mine: TouchKind, theirs: TouchKind, line: String) { _touchFx.value = TouchFx.Meet(mine, theirs, line) }

    fun sendTouch(kind: TouchKind) {
        _touchFx.value = TouchFx.Flight(kind)
        action { hub.sendTouch(kind) }
    }

    /** Devolver el toque [theirs] con [kind]: los dos gestos se encuentran sobre Minu. */
    fun replyTouch(id: String, kind: TouchKind, theirs: TouchKind) = action {
        hub.replyTouch(id, kind)
        _touchFx.value = TouchFx.Meet(kind, theirs, MinuVoice.TOUCH_MEET)
    }
    fun answerQuestion(text: String) = action { hub.answerQuestion(text) }
    fun reactQuestion(text: String) = action { hub.reactQuestion(text) }
    fun giveThanks(text: String) = action { hub.giveThanks(text) }
    fun shareNews(text: String) = action { hub.shareNews(text) }
    fun cheerNews(id: String, text: String) = action { hub.cheerNews(id, text) }

    /** Las preguntas que ya abrieron, para releerlas. */
    private val _pastQuestions = MutableStateFlow<List<DailyQuestion>?>(null)
    val pastQuestions: StateFlow<List<DailyQuestion>?> = _pastQuestions.asStateFlow()
    fun loadPastQuestions() = viewModelScope.launch { runCatching { _pastQuestions.value = api.openedQuestions() } }

    /** Frases que el servidor puede actualizar (respuestas entusiastas…); null = usar las de MinuVoice. */
    private val _minuContent = MutableStateFlow<MinuContent?>(null)
    val minuContent: StateFlow<MinuContent?> = _minuContent.asStateFlow()
    fun loadMinuContent() {
        if (_minuContent.value != null) return
        viewModelScope.launch { runCatching { _minuContent.value = api.minuContent() } }
    }

    /** Frascos de "Gracias por..." de meses pasados (para Recuerdos), por "AAAA-MM". */
    private val _jars = MutableStateFlow<Map<String, List<JarNote>>>(emptyMap())
    val jars: StateFlow<Map<String, List<JarNote>>> = _jars.asStateFlow()
    fun loadJar(month: String) = viewModelScope.launch {
        runCatching { api.jar(month) }.onSuccess { notes -> _jars.update { it + (month to notes) } }
    }

    fun playAll(songs: List<Song>) = viewModelScope.launch { runCatching { hub.playSongs(songs) } }
    fun giveUpRescue() = action { hub.giveUpRescue() }

    fun playSong(song: Song) = viewModelScope.launch { runCatching { hub.playSong(song) } }

    fun react(dedication: Dedication, emoji: String) = hub.react(dedication, emoji)
    fun play(dedication: Dedication) = hub.playDedication(dedication)
    fun markSeen(dedication: Dedication) = hub.markSeen(dedication)
    fun sendLiveReaction(emoji: String) = hub.sendLiveReaction(emoji)

    /** Latidos: varios corazones seguidos, tantos como fuerte lo sostuviste. */
    fun sendHeartbeats(count: Int) = viewModelScope.launch {
        repeat(count.coerceIn(1, 5)) {
            hub.sendLiveReaction("❤️")
            delay(160)
        }
    }
    fun onSongSaved(song: Song) = hub.notifySaved(song)

    fun inviteListen() = hub.inviteListen()
    fun acceptListen() = hub.acceptListen()
    fun leaveListen() = hub.leaveListen()
    fun takeControl() = hub.takeControl()
    fun refresh() = hub.refresh()

    sealed interface TouchFx {
        data class Flight(val kind: TouchKind) : TouchFx
        data class Meet(val mine: TouchKind, val theirs: TouchKind, val line: String) : TouchFx
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val minu = app
                TogetherViewModel(
                    minu.session, minu.cloudApi, minu.cloudSync, minu.partnerHub,
                    onSignedIn = { Push.register(minu) },
                    onSigningOut = { Push.unregister(minu) },
                )
            }
        }

        /** Reacciones para las dedicatorias: el servidor solo acepta estas. */
        val EMOJIS = listOf("❤️", "😍", "🔥", "😘", "💃", "🎶")

        /** Reacciones en vivo: el servidor las reenvía tal cual, así que pueden ser más. */
        val LIVE_EMOJIS = listOf("😘", "🥺", "🔥", "😍", "💃", "🎶", "😂", "✨")

        const val HUG = "🫂"
    }
}
