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

    fun withPlaybackUri(item: MediaItem): MediaItem =
        item.buildUpon().setUri("$SCHEME://${item.mediaId}").build()

    fun videoIdFromUri(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.host
}
