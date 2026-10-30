package com.daniel.reproductor.playback

import androidx.media3.common.Player
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.data.remote.YouTubeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reproducción automática: si la cola empezó con una canción marcada como radio
 * ([PlaybackItems.fromSong] con `radio`), agrega canciones parecidas de YouTube cuando quedan pocas.
 * Vive en el servicio para que siga funcionando con la pantalla apagada.
 */
class RadioQueue(
    private val player: Player,
    private val app: ReproductorApp,
    private val scope: CoroutineScope,
) : Player.Listener {

    /** La canción que arrancó la radio actual; si cambia, se empieza de cero. */
    private var seed: String? = null
    private var page: YouTubeResolver.RadioPage? = null
    private val seen = HashSet<String>()
    private var job: Job? = null

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) maybeFill()
    }

    private fun maybeFill() {
        val first = if (player.mediaItemCount > 0) player.getMediaItemAt(0) else null
        val radio = first?.let(PlaybackItems::radioOf)
        if (first == null || radio == null || !app.settings.autoplay.value) {
            reset()
            return
        }
        val key = "${first.mediaId}:$radio"
        if (key != seed) {
            reset()
            seed = key
        }
        val remaining = player.mediaItemCount - 1 - player.currentMediaItemIndex
        if (remaining > LOOKAHEAD || job?.isActive == true) return
        job = scope.launch { fill(key, first.mediaId, radio) }
    }

    private suspend fun fill(key: String, seedId: String, source: SongSource) {
        for (i in 0 until player.mediaItemCount) seen += player.getMediaItemAt(i).mediaId
        val current = page
        val next = try {
            when {
                current == null -> app.resolver.radio(seedId, source)
                current.next != null -> app.resolver.radioMore(current)
                // Se acabó esta radio: sigue con la de la última canción de la cola.
                else -> app.resolver.radio(player.getMediaItemAt(player.mediaItemCount - 1).mediaId, source)
            }
        } catch (e: Exception) {
            return // sin red o YouTube no respondió: se vuelve a intentar en la próxima canción
        }
        if (seed != key) return // mientras tanto el usuario puso otra cosa
        page = next
        val fresh = next.songs.filter { it.videoId !in seen }.distinctBy { it.videoId }.take(BATCH)
        fresh.mapTo(seen) { it.videoId }
        if (fresh.isEmpty()) return

        val ended = player.playbackState == Player.STATE_ENDED
        player.addMediaItems(fresh.map { PlaybackItems.fromSong(it, app.files, suggested = true) })
        if (ended) {
            // La cola se terminó antes de que llegaran las sugerencias: sigue con la primera.
            player.seekToNextMediaItem()
            player.play()
        }
    }

    private fun reset() {
        job?.cancel()
        job = null
        seed = null
        page = null
        seen.clear()
    }

    private companion object {
        /** Cuántas canciones por sonar deben quedar antes de pedir más. */
        const val LOOKAHEAD = 2
        const val BATCH = 25
    }
}
