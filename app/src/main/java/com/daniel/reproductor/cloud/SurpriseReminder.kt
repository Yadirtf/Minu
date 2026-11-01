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
import com.daniel.reproductor.MainActivity
import com.daniel.reproductor.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Cumplidos a ciegas: el servidor duerme y no puede avisar, así que la app agenda un aviso local para el
 * día de la próxima sorpresa (a una hora distinta cada vez, entre las 10 y las 7 de la noche). El aviso no
 * dice qué es: Minu solo cuenta que tiene algo para ti.
 */
object SurpriseReminder {
    private const val WORK = "minu-sorpresa"
    private const val PREFS = "minu_sorpresa"
    private const val KEY_DAY = "dia"

    fun sync(context: Context, nextOn: String?, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DAY, null) == nextOn) return
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        prefs.edit().putString(KEY_DAY, nextOn).apply()
        if (nextOn == null) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val day = runCatching { LocalDate.parse(nextOn) }.getOrNull() ?: return
        val hour = 10 + (nextOn.hashCode() and 0x7fffffff) % 10 // de 10 a. m. a 7 p. m.
        val at = day.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val request = OneTimeWorkRequestBuilder<SurpriseWorker>()
            .setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class SurpriseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // se revisa abajo
    override suspend fun doWork(): Result {
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Push.EXTRA_OPEN_DUO, true)
        val pending = PendingIntent.getActivity(context, 3001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Push.CHANNEL_TOGETHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎁 Minu tiene algo para ti")
            .setContentText("Te guardé una sorpresa. Ábrela en Dúo.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(3001, notification)
        return Result.success()
    }
}
