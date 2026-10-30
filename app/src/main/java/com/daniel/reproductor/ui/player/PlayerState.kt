package com.daniel.reproductor.ui.player

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.playback.PlaybackItems
import com.daniel.reproductor.playback.PlaybackService
import kotlinx.coroutines.delay

/** Se conecta al PlaybackService. Devuelve null mientras la conexión no está lista. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** Una canción de la cola. [index] es su posición en el reproductor, para saltar a ella o quitarla. */
@Immutable
data class QueueEntry(val index: Int, val song: Song, val artwork: Any?, val suggested: Boolean)

/** Estado observable del reproductor para Compose. */
@Stable
class PlayerState(val player: Player, private val files: MusicFiles) {
    var hasMedia by mutableStateOf(false); private set
    var metadata by mutableStateOf(MediaMetadata.EMPTY); private set
    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF); private set
    var hasNext by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var currentVideoId by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var currentIndex by mutableIntStateOf(0); private set
    var itemCount by mutableIntStateOf(0); private set
    /** La canción que suena la agregó la reproducción automática. */
    var suggested by mutableStateOf(false); private set
    /** La cola la está armando la reproducción automática. */
    var radioOn by mutableStateOf(false); private set
    /** Lo que viene después de la canción actual, en el orden en que va a sonar (respeta el aleatorio). */
    var upNext by mutableStateOf<List<QueueEntry>>(emptyList()); private set

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) refreshQueue()
        }
        override fun onPlayerError(error: PlaybackException) {
            this@PlayerState.error = error.cause?.message ?: error.message
        }
    }

    internal fun refresh() {
        hasMedia = player.currentMediaItem != null
        currentVideoId = player.currentMediaItem?.mediaId
        metadata = player.mediaMetadata
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        shuffle = player.shuffleModeEnabled
        repeatMode = player.repeatMode
        hasNext = player.hasNextMediaItem()
        currentIndex = player.currentMediaItemIndex
        itemCount = player.mediaItemCount
        suggested = PlaybackItems.isSuggested(player.currentMediaItem)
        radioOn = player.mediaItemCount > 0 && PlaybackItems.radioOf(player.getMediaItemAt(0)) != null
        updatePosition()
    }

    internal fun refreshQueue() {
        val timeline = player.currentTimeline
        val entries = ArrayList<QueueEntry>()
        if (!timeline.isEmpty) {
            // REPEAT_MODE_OFF a propósito: con "repetir todo" la cola no tiene fin y se mostraría dos veces
            var i = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            while (i != C.INDEX_UNSET && entries.size < QUEUE_SHOWN) {
                val item = player.getMediaItemAt(i)
                PlaybackItems.toSong(item)?.let { song ->
                    entries += QueueEntry(i, song, item.mediaMetadata.artworkUri, PlaybackItems.isSuggested(item))
                }
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            }
        }
        upNext = entries
    }

    internal fun updatePosition() {
        positionMs = player.currentPosition.coerceAtLeast(0)
        durationMs = player.duration.takeIf { it != C.TIME_UNSET }
            ?: metadata.durationMs ?: 0L
    }

    fun clearError() { error = null }

    /** La canción que suena ahora, lista para guardarla en una playlist. */
    fun currentSong(): Song? = player.currentMediaItem?.let { PlaybackItems.toSong(it, durationMs) }

    /** Si devuelve un mensaje, este celular no controla la música (sigue al de la pareja). */
    var lockedReason: () -> String? = { null }
    var onLocked: (String) -> Unit = {}

    private inline fun ifUnlocked(action: () -> Unit) {
        val reason = lockedReason()
        if (reason != null) onLocked(reason) else action()
    }

    fun playSongs(songs: List<Song>, startIndex: Int, shuffled: Boolean = false) = ifUnlocked {
        if (songs.isEmpty()) return@ifUnlocked
        error = null
        player.shuffleModeEnabled = shuffled
        player.setMediaItems(songs.map { PlaybackItems.fromSong(it, files) }, startIndex, 0L)
        player.prepare()
        player.play()
    }

    /** Pone [song] y deja que la reproducción automática siga con canciones parecidas. */
    fun playRadio(song: Song, radio: SongSource) = ifUnlocked {
        error = null
        player.shuffleModeEnabled = false
        player.setMediaItem(PlaybackItems.fromSong(song, files, radio = radio))
        player.prepare()
        player.play()
    }

    /**
     * Una sesión de recomendaciones: suena [songs] desde [startIndex] y, cuando se acerca el final,
     * la reproducción automática sigue con la radio de la primera.
     */
    fun playSession(songs: List<Song>, startIndex: Int, radio: SongSource) = ifUnlocked {
        val picked = songs.drop(startIndex)
        if (picked.isEmpty()) return@ifUnlocked
        error = null
        player.shuffleModeEnabled = false
        player.setMediaItems(
            picked.mapIndexed { i, song ->
                if (i == 0) PlaybackItems.fromSong(song, files, radio = radio)
                else PlaybackItems.fromSong(song, files, suggested = true)
            },
            0, 0L,
        )
        player.prepare()
        player.play()
    }

    /** Suena justo después de la actual, sin cambiar el resto de la cola. */
    fun playNext(song: Song) = ifUnlocked {
        val item = PlaybackItems.fromSong(song, files)
        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
            player.play()
        } else {
            player.addMediaItem(player.currentMediaItemIndex + 1, item)
        }
    }

    fun skipTo(entry: QueueEntry) = ifUnlocked {
        player.seekTo(entry.index, 0L)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun removeFromQueue(entry: QueueEntry) = ifUnlocked { player.removeMediaItem(entry.index) }

    fun togglePlay() = ifUnlocked {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
            error = null
            player.play()
        }
    }

    fun next() = ifUnlocked { player.seekToNext() }
    fun previous() = ifUnlocked { player.seekToPrevious() }
    fun seekTo(ms: Long) = ifUnlocked { player.seekTo(ms); positionMs = ms }
    fun toggleShuffle() = ifUnlocked { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeat() = ifUnlocked {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

@Composable
fun rememberPlayerState(player: Player): PlayerState {
    val files = (LocalContext.current.applicationContext as ReproductorApp).files
    val state = remember(player) { PlayerState(player, files) }
    DisposableEffect(state) {
        player.addListener(state.listener)
        state.refresh()
        state.refreshQueue()
        onDispose { player.removeListener(state.listener) }
    }
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying) {
            state.updatePosition()
            delay(500)
        }
    }
    return state
}

private const val QUEUE_SHOWN = 100
