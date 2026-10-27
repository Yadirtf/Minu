@file:androidx.annotation.OptIn(UnstableApi::class)

package com.daniel.reproductor.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.daniel.reproductor.MainActivity
import com.daniel.reproductor.R
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.remote.OkHttpDownloader
import com.daniel.reproductor.data.remote.YouTubeResolver
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio de reproducción. Media3 se encarga de la notificación, la pantalla de bloqueo
 * y los botones de audífonos. Los MediaItem usan URIs `yt://VIDEO_ID` y la decisión
 * archivo local / stream online se toma justo cuando ExoPlayer va a cargar cada canción.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    /** URLs resueltas solo en memoria, para no volver a consultar YouTube en cada seek. */
    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    override fun onCreate() {
        super.onCreate()
        val app = application as ReproductorApp

        val httpFactory = OkHttpDataSource.Factory(app.okHttpClient)
            .setUserAgent(OkHttpDownloader.USER_AGENT)
        val upstream = DefaultDataSource.Factory(this, httpFactory)
        val resolving = ResolvingDataSource.Factory(upstream) { spec -> resolve(app, spec) }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(SkipOnErrorListener(player))

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    /** Se ejecuta en el hilo de carga de ExoPlayer, así que puede bloquear. */
    private fun resolve(app: ReproductorApp, spec: DataSpec): DataSpec {
        val videoId = PlaybackItems.videoIdFromUri(spec.uri) ?: return spec

        val song = runBlocking { app.repository.getSong(videoId) }
        val localPath = song?.localPath
        if (localPath != null && File(localPath).exists()) {
            return spec.withUri(Uri.fromFile(File(localPath)))
        }

        val cached = urlCache[videoId]?.takeIf { System.currentTimeMillis() < it.second }?.first
        val url = cached ?: try {
            runBlocking { app.resolver.bestAudio(app.resolver.getInfo(videoId)).content }
                .also { urlCache[videoId] = it to System.currentTimeMillis() + URL_TTL_MS }
        } catch (e: Exception) {
            throw IOException(YouTubeResolver.describeError(e), e)
        }
        return spec.withUri(Uri.parse(url))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Los MediaItem que llegan desde la UI pierden la URI; se reconstruyen a partir del mediaId. */
    private class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(PlaybackItems::withPlaybackUri).toMutableList())
    }

    /** Si una canción falla (video eliminado, sin red…), pasa a la siguiente. */
    private class SkipOnErrorListener(private val player: ExoPlayer) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private companion object {
        const val URL_TTL_MS = 60 * 60 * 1000L
    }
}
