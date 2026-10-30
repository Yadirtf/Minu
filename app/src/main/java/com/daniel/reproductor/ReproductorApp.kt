package com.daniel.reproductor

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.daniel.reproductor.cloud.CloudApi
import com.daniel.reproductor.cloud.CloudSync
import com.daniel.reproductor.cloud.PartnerHub
import com.daniel.reproductor.cloud.Push
import com.daniel.reproductor.cloud.Session
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.ListeningHistory
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.NetworkMonitor
import com.daniel.reproductor.data.db.AppDatabase
import com.daniel.reproductor.data.download.DownloadStorage
import com.daniel.reproductor.data.remote.ExtractorUpdateChecker
import com.daniel.reproductor.data.remote.OkHttpDownloader
import com.daniel.reproductor.data.remote.YouTubeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

class ReproductorApp : Application() {

    /** Corrutinas que viven mientras viva la app (en el hilo principal). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val resolver: YouTubeResolver by lazy { YouTubeResolver() }
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val files: MusicFiles by lazy { MusicFiles(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val history: ListeningHistory by lazy { ListeningHistory(database.historyDao(), database.musicDao(), resolver) }
    val network: NetworkMonitor by lazy { NetworkMonitor(this) }
    val updateChecker: ExtractorUpdateChecker by lazy {
        ExtractorUpdateChecker(this, okHttpClient, BuildConfig.NEWPIPE_VERSION)
    }
    val downloads: DownloadStorage by lazy {
        DownloadStorage(this, database.musicDao(), files, settings) { WorkManager.getInstance(this) }
    }
    val repository: MusicRepository by lazy {
        MusicRepository(database.musicDao(), resolver, downloads, { WorkManager.getInstance(this) }, settings)
    }

    // Nube y "Juntos"
    val session: Session by lazy { Session(this) }
    val cloudApi: CloudApi by lazy { CloudApi(okHttpClient, session) }
    val cloudSync: CloudSync by lazy { CloudSync(this, repository, session, cloudApi, settings) }
    val partnerHub: PartnerHub by lazy { PartnerHub(this, okHttpClient, cloudApi, session, files, appScope) }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader(okHttpClient))
        cloudSync.watch(database)
        Push.createChannels(this)
        appScope.launch { Push.register(this@ReproductorApp) } // por si cambió el token de notificaciones

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                partnerHub.setForeground(true)
                cloudSync.schedule() // trae cambios de la nube (por ejemplo, de otro celular)
            }

            override fun onStop(owner: LifecycleOwner) = partnerHub.setForeground(false)
        })
    }
}
