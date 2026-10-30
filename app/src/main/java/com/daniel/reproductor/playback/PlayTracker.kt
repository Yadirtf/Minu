package com.daniel.reproductor.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import com.daniel.reproductor.data.db.Song

/**
 * Cuenta una escucha cuando una canción suena de verdad: [MIN_PLAYED_MS] o la mitad si es más corta.
 * Lo que se salta a los pocos segundos no entra al historial ni a las recomendaciones.
 */
class PlayTracker(
    private val player: Player,
    private val onListened: (Song) -> Unit,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var playedMs = 0L
    private var lastTick = 0L
    private var counted = false

    private val tick = object : Runnable {
        override fun run() {
            accumulate()
            if (player.isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            playedMs = 0L
            counted = false
            lastTick = SystemClock.elapsedRealtime()
        }
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            handler.removeCallbacks(tick)
            if (player.isPlaying) {
                lastTick = SystemClock.elapsedRealtime()
                handler.postDelayed(tick, TICK_MS)
            }
        }
    }

    private fun accumulate() {
        val now = SystemClock.elapsedRealtime()
        playedMs += now - lastTick
        lastTick = now
        if (counted) return
        val item = player.currentMediaItem ?: return
        val durationMs = item.mediaMetadata.durationMs ?: 0L
        val needed = if (durationMs > 0) minOf(MIN_PLAYED_MS, durationMs / 2) else MIN_PLAYED_MS
        if (playedMs >= needed) {
            counted = true
            PlaybackItems.toSong(item)?.let(onListened)
        }
    }

    fun release() = handler.removeCallbacks(tick)

    private companion object {
        const val TICK_MS = 5_000L
        const val MIN_PLAYED_MS = 30_000L
    }
}
