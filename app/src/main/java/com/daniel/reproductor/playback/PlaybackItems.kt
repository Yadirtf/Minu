package com.daniel.reproductor.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.data.remote.SongSource
import java.io.File

object PlaybackItems {
    private const val SCHEME = "yt"

    private const val EXTRA_RADIO = "radio"
    private const val EXTRA_SUGGESTED = "suggested"

    /** Con [radio] la canción arranca una radio de ese catálogo: [RadioQueue] sigue agregando canciones parecidas. */
    fun fromSong(song: Song, files: MusicFiles, radio: SongSource? = null, suggested: Boolean = false): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.videoId)
            .setUri("$SCHEME://${song.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.uploader)
                    .setArtworkUri(artworkUri(song, files))
                    .setDurationMs(song.durationSec * 1000)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(
                        Bundle().apply {
                            radio?.let { putString(EXTRA_RADIO, it.name) }
                            if (suggested) putBoolean(EXTRA_SUGGESTED, true)
                        }.takeUnless { it.isEmpty() }
                    )
                    .build()
            )
            .build()

    private fun artworkUri(song: Song, files: MusicFiles): Uri? =
        when (val thumb = files.thumbnailFor(song)) {
            is File -> Uri.fromFile(thumb)
            else -> song.thumbnailUrl.takeIf { it.isNotEmpty() }?.toUri()
        }

    /**
     * Lo inverso de [fromSong]: arma la canción a partir de lo que está sonando, para poder guardarla
     * aunque venga de una búsqueda o del celular de la pareja. Una portada local no sirve como URL:
     * en ese caso la canción ya está en la biblioteca y se conserva la que tiene guardada.
     */
    fun toSong(item: MediaItem, durationMs: Long = 0L): Song? {
        val videoId = item.mediaId.takeIf { it.isNotBlank() } ?: return null
        val meta = item.mediaMetadata
        return Song(
            videoId = videoId,
            title = meta.title?.toString().orEmpty(),
            uploader = meta.artist?.toString().orEmpty(),
            durationSec = (durationMs.takeIf { it > 0 } ?: meta.durationMs ?: 0L) / 1000,
            thumbnailUrl = meta.artworkUri?.takeIf { it.scheme?.startsWith("http") == true }?.toString().orEmpty(),
        )
    }

    fun radioOf(item: MediaItem): SongSource? =
        item.mediaMetadata.extras?.getString(EXTRA_RADIO)?.let { runCatching { SongSource.valueOf(it) }.getOrNull() }

    /** La agregó la radio, no la eligió el usuario. */
    fun isSuggested(item: MediaItem?): Boolean = item?.mediaMetadata?.extras?.getBoolean(EXTRA_SUGGESTED) == true

    fun withPlaybackUri(item: MediaItem): MediaItem =
        item.buildUpon().setUri("$SCHEME://${item.mediaId}").build()

    fun videoIdFromUri(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.host
}
