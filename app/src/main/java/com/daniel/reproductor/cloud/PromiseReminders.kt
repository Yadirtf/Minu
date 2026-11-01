package com.daniel.reproductor.cloud

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daniel.reproductor.MainActivity
import com.daniel.reproductor.R
import java.util.concurrent.TimeUnit

/**
 * Recordatorios de las Promesas pequeñas: solo en el celular de quien prometió, 10 minutos antes (o a la
 * hora si falta menos). Nunca le llega nada al otro: la idea es ayudar a cumplir, no vigilar.
 *
 * Se sincroniza con el estado de Dúo cada vez que cambia: agenda las promesas mías que siguen abiertas y
 * cancela las que ya se cumplieron, se soltaron o se borraron solas.
 */
object PromiseReminders {
    private const val PREFS = "minu_promesas"
    private const val KEY_IDS = "agendadas"
    private const val AHEAD_MS = 10 * 60_000L

    private fun work(id: String) = "minu-promesa-$id"

    fun sync(context: Context, board: PromiseBoard?, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scheduled = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val wanted = board?.items.orEmpty().filter { it.mine && !it.kept && it.dueAt > now }
        if (scheduled.isEmpty() && wanted.isEmpty()) return
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        (scheduled - wanted.map { it.id }.toSet()).forEach { manager.cancelUniqueWork(work(it)) }
        wanted.forEach { p ->
            val delay = (p.dueAt - AHEAD_MS - now).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<PromiseReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(PromiseReminderWorker.KEY_TEXT to p.text, PromiseReminderWorker.KEY_ID to p.id))
                .build()
            manager.enqueueUniqueWork(work(p.id), ExistingWorkPolicy.KEEP, request)
        }
        prefs.edit().putStringSet(KEY_IDS, wanted.map { it.id }.toSet()).apply()
    }
}

class PromiseReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // se revisa abajo
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.success()
        val id = inputData.getString(KEY_ID).orEmpty()
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()
        val notificationId = 2000 + (id.hashCode() and 0xffff)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Push.EXTRA_OPEN_DUO, true)
        val pending = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Push.CHANNEL_TOGETHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🤞 Tu promesa: $text")
            .setContentText("Minu te lo recuerda con cariño. Cuando la cumplas, márcala en Dúo.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return Result.success()
    }

    companion object {
        const val KEY_TEXT = "texto"
        const val KEY_ID = "id"
    }
}
