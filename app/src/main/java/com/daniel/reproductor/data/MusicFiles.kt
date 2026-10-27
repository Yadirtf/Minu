package com.daniel.reproductor.data

import android.content.Context
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Song
import java.io.File

/** Archivos descargados en el almacenamiento interno de la app (no requiere permisos). */
class MusicFiles(context: Context) {
    val musicDir = File(context.filesDir, "music").apply { mkdirs() }
    val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun audioFile(videoId: String, extension: String) = File(musicDir, "$videoId.$extension")
    fun partialFile(videoId: String) = File(musicDir, "$videoId.part")
    fun thumbFile(videoId: String) = File(thumbsDir, "$videoId.jpg")

    /** Miniatura local si la canción está descargada; si no, la URL de YouTube. */
    fun thumbnailFor(song: Song): Any {
        if (song.downloadState == DownloadState.DONE) {
            val local = thumbFile(song.videoId)
            if (local.exists()) return local
        }
        return song.thumbnailUrl
    }

    fun deleteFor(song: Song) {
        song.localPath?.let { File(it).delete() }
        partialFile(song.videoId).delete()
        thumbFile(song.videoId).delete()
    }
}
