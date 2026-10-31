package com.daniel.reproductor.cloud

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.daniel.reproductor.BuildConfig
import com.daniel.reproductor.MainActivity
import com.daniel.reproductor.R
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.db.Song
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Notificaciones con Firebase Cloud Messaging. Sin google-services.json todo queda desactivado. */
object Push {
    const val CHANNEL_DEDICATIONS = "dedicatorias"
    const val CHANNEL_TOGETHER = "juntos"
    const val EXTRA_LISTEN_INVITE = "minu.listen_invite"

    fun available(context: Context): Boolean =
        BuildConfig.HAS_FIREBASE && FirebaseApp.getApps(context).isNotEmpty()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEDICATIONS, "Dedicatorias", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Canciones que tu pareja te dedica" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TOGETHER, "Dúo", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Invitaciones para escuchar juntos y avisos de tu pareja" }
        )
    }

    /** Token de este celular para que el servidor le mande notificaciones. */
    suspend fun token(context: Context): String? {
        if (!available(context)) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /** Avisa al servidor qué celular es este (después de iniciar sesión o al abrir la app). */
    suspend fun register(app: ReproductorApp) {
        if (app.session.account.value == null) return
        val token = token(app) ?: return
        runCatching { app.cloudApi.registerDevice(token) }
    }

    suspend fun unregister(app: ReproductorApp) {
        val token = token(app) ?: return
        runCatching { app.cloudApi.unregisterDevice(token) }
    }
}

/** Recibe las notificaciones: dedicatorias (y las reproduce si así lo pidió tu pareja), invitaciones y avisos. */
class MinuMessagingService : FirebaseMessagingService() {

    private val app get() = application as ReproductorApp

    override fun onNewToken(token: String) {
        if (app.session.account.value == null) return
        app.appScope.launch { runCatching { app.cloudApi.registerDevice(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Con la app abierta todo llega por la conexión en vivo; aquí solo refrescamos
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) {
            app.appScope.launch { app.partnerHub.refresh() }
            return
        }
        val from = data["fromName"].orEmpty().ifBlank { "Tu pareja" }
        when (data["type"]) {
            "dedication" -> {
                val autoplay = data["autoplay"] == "true"
                val song = Song(
                    videoId = data["videoId"].orEmpty(),
                    title = data["title"].orEmpty(),
                    uploader = data["uploader"].orEmpty(),
                    durationSec = data["durationSec"]?.toLongOrNull() ?: 0,
                    thumbnailUrl = data["thumbnailUrl"].orEmpty(),
                )
                if (autoplay && song.videoId.isNotEmpty()) {
                    // Mensaje de alta prioridad: Android permite empezar a reproducir aunque la app esté cerrada
                    app.appScope.launch { runCatching { app.partnerHub.playSong(song) } }
                }
                val message2 = data["message"].orEmpty()
                notify(
                    CHANNEL_DEDICATION_ID, Push.CHANNEL_DEDICATIONS,
                    title = "💌 $from te dedicó una canción",
                    text = buildString {
                        append(song.title)
                        if (message2.isNotBlank()) append(" — “$message2”")
                        if (autoplay) append("\nEstá sonando en tu celular 🎶")
                    },
                )
            }
            "partner.request" -> notify(2, Push.CHANNEL_TOGETHER, "💞 $from quiere vincularse contigo", "Abre Dúo en Minu para aceptar.")
            "partner.accepted" -> notify(3, Push.CHANNEL_TOGETHER, "❤️ $from aceptó tu invitación", "Ya pueden dedicarse canciones y escuchar juntos.")
            "duo.origin" -> notify(5, Push.CHANNEL_TOGETHER, "💞 $from guardó el día en que empezó todo", "Abre Dúo para ver cuánto tiempo llevan juntos.")
            "listen.invite" -> notify(
                4, Push.CHANNEL_TOGETHER, "🎧 $from quiere escuchar música contigo", "Toca para unirte.",
                extra = Push.EXTRA_LISTEN_INVITE,
            )
        }
    }

    @SuppressLint("MissingPermission") // se revisa abajo
    private fun notify(id: Int, channel: String, title: String, text: String, extra: String? = null) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { extra?.let { putExtra(it, true) } }
        val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == Push.CHANNEL_DEDICATIONS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_DEDICATION_ID = 1
    }
}
