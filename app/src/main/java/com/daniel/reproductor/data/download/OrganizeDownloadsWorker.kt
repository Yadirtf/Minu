package com.daniel.reproductor.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daniel.reproductor.ReproductorApp

/** Ejecuta [DownloadStorage.organize] en segundo plano (cambio de carpeta, playlists renombradas o borradas). */
class OrganizeDownloadsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app = context.applicationContext as ReproductorApp

    override suspend fun doWork(): Result {
        val from = inputData.getStringArray(KEY_RENAMED_FROM).orEmpty()
        val to = inputData.getStringArray(KEY_RENAMED_TO).orEmpty()
        val removed = inputData.getStringArray(KEY_REMOVED).orEmpty().toList()
        app.downloads.organize(from.zip(to).toMap(), removed) { progress ->
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        }
        return Result.success()
    }

    companion object {
        const val NAME = "organize-downloads"
        const val KEY_RENAMED_FROM = "renamedFrom"
        const val KEY_RENAMED_TO = "renamedTo"
        const val KEY_REMOVED = "removed"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
    }
}
