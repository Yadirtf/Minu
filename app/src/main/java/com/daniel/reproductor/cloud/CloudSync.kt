package com.daniel.reproductor.cloud

import android.content.Context
import android.os.Build
import androidx.room.InvalidationTracker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.PlaylistBackup
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.AppDatabase
import com.daniel.reproductor.ui.theme.StyleId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Mantiene la biblioteca respaldada en la nube.
 *
 * - Cada cambio en playlists marca "hay cambios" y programa una subida (espera unos segundos
 *   para juntar varios cambios seguidos).
 * - Al abrir la app se revisa si la nube tiene una versión más nueva (por ejemplo, de otro celular).
 * - Al iniciar sesión en un celular nuevo se une lo de la nube con lo local, sin perder nada.
 */
class CloudSync(
    private val context: Context,
    private val repo: MusicRepository,
    private val session: Session,
    private val api: CloudApi,
    private val settings: AppSettings,
) {
    private val mutex = Mutex()
    @Volatile private var ignoreChangesUntil = 0L

    /** Observa la base de datos: cualquier cambio en playlists se sube a la nube. */
    fun watch(database: AppDatabase) {
        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("Playlist", "PlaylistSong")) {
            override fun onInvalidated(tables: Set<String>) {
                if (session.account.value == null || System.currentTimeMillis() < ignoreChangesUntil) return
                session.dirty = true
                schedule(delaySeconds = 8)
            }
        })
    }

    fun schedule(delaySeconds: Long = 0) {
        if (session.account.value == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Sincroniza ahora. Lanza [UserFacingException] si algo falla. */
    suspend fun syncNow() = mutex.withLock {
        if (session.account.value == null) return@withLock
        session.setRunning(true)
        try {
            val (remote, remoteVersion) = api.getLibrary()
            val remoteSnapshot = remote?.let { PlaylistBackup.decodeObject(it) }
            val remoteIsNewer = remoteSnapshot != null && remoteVersion > session.syncedServerVersion
            when {
                session.needsMerge -> {
                    // Primera vez en este celular: unir nube + local y subir el resultado
                    remoteSnapshot?.let { applying { repo.mergeEntries(it.playlists); applySettings(it.settings) } }
                    push()
                    session.needsMerge = false
                }
                remoteIsNewer && !session.dirty -> {
                    applying { repo.replaceWithEntries(remoteSnapshot.playlists); applySettings(remoteSnapshot.settings) }
                    session.syncedServerVersion = remoteVersion
                }
                remoteIsNewer -> {
                    // Cambios en los dos lados: se unen para no perder nada
                    applying { repo.mergeEntries(remoteSnapshot.playlists) }
                    push()
                }
                session.dirty || remoteSnapshot == null -> push()
            }
            session.recordSync(null)
        } catch (e: UserFacingException) {
            session.recordSync(e.message)
            throw e
        }
    }

    private suspend fun push() {
        session.dirty = false // si hay cambios durante la subida, se vuelve a marcar
        try {
            val data = PlaylistBackup.encodeObject(repo.snapshotEntries(), mapOf(KEY_STYLE to settings.style.value.name))
            session.syncedServerVersion = api.putLibrary(data, "${Build.MANUFACTURER} ${Build.MODEL}")
        } catch (e: Exception) {
            session.dirty = true
            throw e
        }
    }

    /** Los cambios que hace la propia sincronización no cuentan como "cambios locales". */
    private suspend fun applying(block: suspend () -> Unit) {
        ignoreChangesUntil = Long.MAX_VALUE
        try {
            block()
        } finally {
            ignoreChangesUntil = System.currentTimeMillis() + 3_000
        }
    }

    private fun applySettings(values: Map<String, String>) {
        values[KEY_STYLE]?.let { name -> runCatching { StyleId.valueOf(name) }.getOrNull()?.let(settings::setStyle) }
    }

    companion object {
        private const val WORK_NAME = "cloud-sync"
        private const val KEY_STYLE = "style"
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ReproductorApp
        return try {
            app.cloudSync.syncNow()
            Result.success()
        } catch (e: UserFacingException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
