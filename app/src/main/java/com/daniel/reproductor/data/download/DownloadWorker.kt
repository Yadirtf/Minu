package com.daniel.reproductor.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.remote.OkHttpDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * Resuelve el stream, descarga el audio y la miniatura, y actualiza localPath/downloadState.
 * El archivo se baja a la memoria de la app y al terminar [DownloadStorage] lo lleva a su lugar.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app = context.applicationContext as ReproductorApp

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        val dao = app.database.musicDao()
        val song = dao.getSong(videoId) ?: return Result.success() // ya no existe
        if (song.downloadState == DownloadState.DONE && song.localPath != null) return Result.success()

        dao.setDownloadState(videoId, DownloadState.DOWNLOADING)
        return try {
            val info = app.resolver.getInfo(videoId)
            val audio = app.resolver.bestAudio(info)
            val extension = audio.format?.suffix ?: "m4a"

            val partial = app.files.partialFile(videoId)
            withContext(Dispatchers.IO) {
                RangeDownloader(app.okHttpClient, OkHttpDownloader.USER_AGENT)
                    .download(audio.content, partial) { done, total ->
                        if (isStopped) throw CancellationException("Descarga cancelada")
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        setProgressAsync(workDataOf(KEY_PROGRESS to percent, KEY_VIDEO_ID to videoId))
                    }
                if (isStopped) throw CancellationException("Descarga cancelada")
                downloadThumbnail(song.thumbnailUrl, videoId)
            }
            val playlistId = inputData.getLong(KEY_PLAYLIST_ID, NO_PLAYLIST).takeIf { it != NO_PLAYLIST }
            dao.setDownloaded(videoId, app.downloads.store(song, partial, extension, playlistId))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is IOException && runAttemptCount < MAX_RETRIES) {
                dao.setDownloadState(videoId, DownloadState.QUEUED)
                Result.retry() // continúa desde el archivo parcial
            } else {
                app.files.partialFile(videoId).delete()
                dao.setDownloadState(videoId, DownloadState.FAILED)
                Result.failure()
            }
        }
    }

    /** La miniatura se guarda localmente para verla sin internet. Si falla no es grave. */
    private fun downloadThumbnail(url: String, videoId: String) {
        if (url.isEmpty()) return
        runCatching {
            app.okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) {
                    app.files.thumbFile(videoId).outputStream().use { r.body.byteStream().copyTo(it) }
                }
            }
        }
    }

    companion object {
        const val KEY_VIDEO_ID = "videoId"
        /** Playlist desde la que se pidió la descarga: su carpeta es donde se guarda. */
        const val KEY_PLAYLIST_ID = "playlistId"
        const val NO_PLAYLIST = -1L
        const val KEY_PROGRESS = "progress"
        const val TAG = "download"
        private const val MAX_RETRIES = 3

        fun uniqueName(videoId: String) = "download-$videoId"
    }
}
