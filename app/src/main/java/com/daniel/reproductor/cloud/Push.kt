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
    const val EXTRA_OPEN_DUO = "minu.open_duo"

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
            "streak.rescue" -> notify(
                6, Push.CHANNEL_TOGETHER, "🧵 $from eligió salvar el hilo",
                if (data["rescue"] == "capsule") "Hoy toca la cápsula de tres canciones. Ábrela en Dúo."
                else "Hoy toca escuchar juntos 30 minutos. Invítale desde Dúo.",
            )
            "capsule.sealed" -> notify(7, Push.CHANNEL_TOGETHER, "💌 $from selló su cápsula", "Te toca: responde tus tres canciones y se abrirá para los dos.")
            "capsule.opened" -> notify(7, Push.CHANNEL_TOGETHER, "✨ Su cápsula se abrió", "Salvaron el hilo. Entra a Dúo para ver qué respondió $from.")
            "mission.done" -> notify(8, Push.CHANNEL_TOGETHER, "🎶 $from marcó la misión del mes", "Si ya la hicieron, márcala tú también en Dúo.")
            "anniversary.hug" -> notify(9, Push.CHANNEL_TOGETHER, "🎁 $from te está abrazando en el hilo", "Abre Minu y abraza tú también: su aniversario solo se abre con los dos.")
            "letter.sealed" -> notify(10, Push.CHANNEL_TOGETHER, "💌 $from selló una carta para su aniversario", "Se abrirá el día que lo abran juntos.")
            "duo.origin" -> notify(5, Push.CHANNEL_TOGETHER, "💞 $from guardó el día en que empezó todo", "Abre Dúo para ver cuánto tiempo llevan juntos.")
            "minu.touch" -> touchNotification(this, data["id"].orEmpty(), TouchKind.from(data["kind"]), from)
            "minu.touch.reply" -> {
                val kind = TouchKind.from(data["kind"])
                notify(14, Push.CHANNEL_TOGETHER, "${kind.emoji} $from te devolvió ${kind.sentence}", "Minu está feliz: se encontraron 💞", extra = Push.EXTRA_OPEN_DUO)
            }
            "minu.question" -> notify(15, Push.CHANNEL_TOGETHER, "💌 $from respondió la pregunta del día", "Su sobre está sellado. Responde tú y se abre para los dos.", extra = Push.EXTRA_OPEN_DUO)
            "minu.question.open" -> notify(15, Push.CHANNEL_TOGETHER, "💌 Se abrió su pregunta del día", "Mira lo que respondió $from.", extra = Push.EXTRA_OPEN_DUO)
            "minu.question.react" -> notify(15, Push.CHANNEL_TOGETHER, "💬 $from reaccionó a tu respuesta", "Míralo en la pregunta del día.", extra = Push.EXTRA_OPEN_DUO)
            "minu.thanks" -> notify(16, Push.CHANNEL_TOGETHER, "🫙 $from echó un gracias al frasco", "Ábrelo en Dúo para leerlo.", extra = Push.EXTRA_OPEN_DUO)
            "minu.news" -> notify(17, Push.CHANNEL_TOGETHER, "✨ $from te cuenta algo bueno", data["text"].orEmpty().ifBlank { "Entra a Dúo a celebrarlo." }, extra = Push.EXTRA_OPEN_DUO)
            "minu.cheer" -> notify(17, Push.CHANNEL_TOGETHER, "🎉 $from celebró tu buena noticia", "Míralo en Dúo.", extra = Push.EXTRA_OPEN_DUO)
            "minu.terms" -> notify(
                11, Push.CHANNEL_TOGETHER, "🧶 $from quiere despertar a Minu",
                "Alguien duerme en su hilo rojo. Entra a Dúo, lee cómo cuida sus datos y acepta tú también.",
                extra = Push.EXTRA_OPEN_DUO,
            )
            "minu.born" -> notify(
                11, Push.CHANNEL_TOGETHER, "🧶 ¡Minu despertó!",
                "Ya vive en su hilo rojo. Pasa por Dúo a saludarle.",
                extra = Push.EXTRA_OPEN_DUO,
            )
            "minu.proposal" -> notify(
                12, Push.CHANNEL_TOGETHER,
                if (data["kind"] == "species") "🧶 $from propuso otra forma para Minu" else "🧶 $from propuso un nuevo nombre para Minu",
                "Míralo en la casita de Minu, en Dúo.",
                extra = Push.EXTRA_OPEN_DUO,
            )
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

/** Id de la notificación del toque [touchId] (para reemplazarla al devolverlo). */
internal fun touchNotificationId(touchId: String) = 1000 + (touchId.hashCode() and 0xffff)

/**
 * "Minu te trae un abrazo de X": con tres botones para devolverlo sin abrir la app. Al tocarla, abre Dúo,
 * donde Minu espera con los cuatro gestos.
 */
@SuppressLint("MissingPermission") // se revisa abajo
internal fun touchNotification(context: Context, touchId: String, kind: TouchKind, from: String) {
    val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!allowed || touchId.isBlank()) return
    val id = touchNotificationId(touchId)
    val open = PendingIntent.getActivity(
        context, id,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Push.EXTRA_OPEN_DUO, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(context, Push.CHANNEL_TOGETHER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("${kind.emoji} $from te manda ${kind.sentence}")
        .setContentText("Minu lo trajo volando. Devuélveselo:")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(open)
    // Tres gestos para devolver (Android muestra hasta tres botones); el mismo que llegó va primero
    val replies = (listOf(kind) + TouchKind.entries.filter { it != kind }).take(3)
    replies.forEachIndexed { i, reply ->
        val intent = Intent(context, TouchReplyReceiver::class.java)
            .putExtra(TouchReplyReceiver.EXTRA_ID, touchId)
            .putExtra(TouchReplyReceiver.EXTRA_KIND, reply.id)
            .putExtra(TouchReplyReceiver.EXTRA_FROM, from)
        val pending = PendingIntent.getBroadcast(context, id * 4 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.addAction(0, "${reply.emoji} ${reply.label}", pending)
    }
    NotificationManagerCompat.from(context).notify(id, builder.build())
}

/** Devuelve un toque desde el botón de la notificación y la cambia por "Minu se lo llevó". */
class TouchReplyReceiver : android.content.BroadcastReceiver() {
    @SuppressLint("MissingPermission") // se revisa antes de avisar
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ReproductorApp
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val kind = TouchKind.from(intent.getStringExtra(EXTRA_KIND))
        val from = intent.getStringExtra(EXTRA_FROM).orEmpty()
        val done = goAsync()
        app.appScope.launch {
            val ok = runCatching { app.partnerHub.replyTouch(id, kind) }.isSuccess
            runCatching {
                val manager = NotificationManagerCompat.from(context)
                manager.cancel(touchNotificationId(id))
                if (ok) {
                    val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (allowed) {
                        manager.notify(
                            touchNotificationId(id),
                            NotificationCompat.Builder(context, Push.CHANNEL_TOGETHER)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle("${kind.emoji} Minu le lleva ${kind.sentence} a $from")
                                .setTimeoutAfter(4_000)
                                .setAutoCancel(true)
                                .build(),
                        )
                    }
                }
            }
            done.finish()
        }
    }

    companion object {
        const val EXTRA_ID = "touch_id"
        const val EXTRA_KIND = "touch_kind"
        const val EXTRA_FROM = "touch_from"
    }
}
