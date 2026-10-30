package com.daniel.reproductor.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.db.Song
import java.io.File

object PlaybackItems {
    private const val SCHEME = "yt"

    fun fromSong(song: Song, files: MusicFiles): MediaItem =
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

    fun withPlaybackUri(item: MediaItem): MediaItem =
        item.buildUpon().setUri("$SCHEME://${item.mediaId}").build()

    fun videoIdFromUri(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.host
}
