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
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daniel.reproductor.MainActivity
import com.daniel.reproductor.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Una fecha especial de la pareja. [key] evita celebrar dos veces lo mismo. */
sealed interface DuoMilestone {
    val key: String

    /** Cada año en el mismo día. */
    data class Anniversary(val years: Int) : DuoMilestone {
        override val key get() = "a$years"
    }

    /** Cada mes en el mismo día (o el último del mes si ese día no existe: 31 -> 30). */
    data class Monthsary(val months: Int) : DuoMilestone {
        override val key get() = "m$months"
    }

    /** 100, 500, 1000 días… */
    data class Days(val days: Long) : DuoMilestone {
        override val key get() = "d$days"
    }
}

/** Cuentas con la fecha en que empezó la relación. Todo en la fecha local del celular. */
object DuoDates {
    private val SPANISH = Locale.forLanguageTag("es")
    private val LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)

    fun parse(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun format(date: LocalDate): String = LONG_DATE.format(date)

    private fun isDayMilestone(days: Long) = days == 100L || days == 500L || (days > 0 && days % 1000 == 0L)

    /** La fecha especial que cae en [day], si hay alguna. Aniversario > días redondos > mesiversario. */
    fun milestoneOn(start: LocalDate, day: LocalDate): DuoMilestone? {
        if (!day.isAfter(start)) return null
        val days = ChronoUnit.DAYS.between(start, day)
        // plusMonths ya recorta al último día del mes (31 ene + 1 mes = 28 feb)
        val whole = ChronoUnit.MONTHS.between(start, day)
        val months = (whole..whole + 1).firstOrNull { it > 0 && start.plusMonths(it) == day }?.toInt()
        return when {
            months != null && months % 12 == 0 -> DuoMilestone.Anniversary(months / 12)
            isDayMilestone(days) -> DuoMilestone.Days(days)
            months != null -> DuoMilestone.Monthsary(months)
            else -> null
        }
    }

    /** La próxima fecha especial después de [day] (para la cuenta regresiva). */
    fun next(start: LocalDate, day: LocalDate): Pair<DuoMilestone, LocalDate>? {
        var d = maxOf(day, start).plusDays(1)
        repeat(40) { // siempre hay un mesiversario en menos de 32 días
            milestoneOn(start, d)?.let { return it to d }
            d = d.plusDays(1)
        }
        return null
    }

    /** "1 año, 8 meses y 16 días" */
    fun together(start: LocalDate, day: LocalDate): String {
        if (!day.isAfter(start)) return "Hoy empieza todo"
        val p = Period.between(start, day)
        val parts = listOfNotNull(
            p.years.takeIf { it > 0 }?.let { plural(it, "año", "años") },
            p.months.takeIf { it > 0 }?.let { plural(it, "mes", "meses") },
            p.days.takeIf { it > 0 }?.let { plural(it, "día", "días") },
        )
        return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    }

    fun days(start: LocalDate, day: LocalDate) = ChronoUnit.DAYS.between(start, day)

    fun plural(n: Number, one: String, many: String) = "$n ${if (n.toLong() == 1L) one else many}"

    private val ORDINALS = listOf("primer", "segundo", "tercer", "cuarto", "quinto", "sexto", "séptimo", "octavo", "noveno", "décimo")

    /** "su primer año", "sus 12 años" */
    fun yearsPhrase(years: Int) = ORDINALS.getOrNull(years - 1)?.let { "su $it año" } ?: "sus $years años"

    /** Lo que se dice de una fecha especial, en corto (notificaciones, Dúo). */
    fun title(m: DuoMilestone) = when (m) {
        is DuoMilestone.Anniversary -> "Hoy es ${yearsPhrase(m.years)} juntos"
        is DuoMilestone.Monthsary -> "Hoy cumplen ${plural(m.months, "mes", "meses")}"
        is DuoMilestone.Days -> "Hoy cumplen ${m.days} días juntos"
    }

    /** Una idea para hacer ese día, siempre con música (las misiones del mes). */
    fun idea(m: DuoMilestone): String = when (m) {
        is DuoMilestone.Anniversary -> "Recreen su primera cita con la canción de origen sonando."
        is DuoMilestone.Days -> if (m.days >= 1000) "Hagan una lista con una canción de cada mes que llevan juntos y escúchenla de principio a fin." else "Escuchen juntos las canciones que más se han dedicado."
        is DuoMilestone.Monthsary -> mission(m.months)
    }

    /** La misión del mes [month] (1, 2, 3…): el ciclo de 12 se repite cada año. */
    fun mission(month: Int) = MISSIONS[(month - 1).mod(MISSIONS.size)]

    /** Meses completos desde el inicio hasta [day] (con el mismo recorte de fin de mes). */
    fun monthsBetween(start: LocalDate, day: LocalDate): Int {
        var m = ChronoUnit.MONTHS.between(start, day).toInt().coerceAtLeast(0)
        if (start.plusMonths((m + 1).toLong()) <= day) m += 1
        return m
    }

    /** El mes N de la relación va de [start + N-1 meses, start + N meses). */
    fun monthRange(start: LocalDate, month: Int): Pair<LocalDate, LocalDate> =
        start.plusMonths((month - 1).toLong()) to start.plusMonths(month.toLong())

    private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", SPANISH)
    fun short(date: LocalDate): String = SHORT_DATE.format(date).replace(".", "")

    private val MISSIONS = listOf(
        "La primera página: escríbanse en una frase cómo fue su primer mes.",
        "Cena con banda sonora: cocinen juntos escuchando las canciones que se dedicaron este mes.",
        "Intercambio de cinco: cada uno elige 5 canciones que el otro nunca ha escuchado.",
        "Baile en la sala: bailen su canción de origen, donde estén.",
        "Canciones de antes: muéstrense la canción que escuchaban a los 15 años y cuéntense por qué.",
        "Medio año: escuchen una canción de cada mes que llevan juntos.",
        "Paseo con audífonos: salgan a caminar escuchando juntos en Minu.",
        "La canción del futuro: elijan la que quieren que suene en un momento que aún no pasa.",
        "Karaoke de dos: cada uno canta la canción que eligió el otro.",
        "Noche sin pantallas: una hora juntos, con música y el celular boca abajo.",
        "Carta para el año: cada uno escribe una carta para leerla en su aniversario.",
        "Víspera: escojan juntos la canción con la que abrirán su aniversario.",
    )
}

/** Lo último que se supo de la pareja, guardado para los avisos aunque la app esté cerrada. */
object DuoPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("duo", Context.MODE_PRIVATE)

    fun save(context: Context, startedOn: String?, partnerName: String?) = prefs(context).edit {
        putString("startedOn", startedOn)
        putString("partnerName", partnerName)
    }

    fun startedOn(context: Context) = DuoDates.parse(prefs(context).getString("startedOn", null))
    fun partnerName(context: Context) = prefs(context).getString("partnerName", null)

    /** "2026-10-31:m8": la última fecha especial que ya se celebró en pantalla. */
    fun celebrated(context: Context) = prefs(context).getString("celebrated", null)
    fun setCelebrated(context: Context, value: String) = prefs(context).edit { putString("celebrated", value) }

    /** Animaciones de la racha ya vistas en este celular: [kind] risk, broken o saved, [value] el día. */
    fun seen(context: Context, kind: String) = prefs(context).getString("seen_$kind", null)
    fun setSeen(context: Context, kind: String, value: String) = prefs(context).edit { putString("seen_$kind", value) }

    fun notified(context: Context) = prefs(context).getString("notified", null)
    fun setNotified(context: Context, value: String) = prefs(context).edit { putString("notified", value) }
}

/** Cada mañana (~9:00) revisa si hoy es una fecha especial y lo avisa. No necesita al servidor. */
class DuoReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = DuoPrefs.startedOn(applicationContext) ?: return Result.success()
        val today = LocalDate.now()
        val milestone = DuoDates.milestoneOn(start, today) ?: return Result.success()
        val mark = "$today:${milestone.key}"
        if (DuoPrefs.notified(applicationContext) == mark) return Result.success()
        DuoPrefs.setNotified(applicationContext, mark)
        val text = when (milestone) {
            is DuoMilestone.Anniversary -> "Hay algo esperándolos en Minu, pero solo se abre con los dos."
            is DuoMilestone.Monthsary -> "Su hilo tiene un nudo nuevo. ${DuoDates.idea(milestone)}"
            is DuoMilestone.Days -> "Abran Minu para celebrarlo."
        }
        notify("💞 ${DuoDates.title(milestone)}", text)
        return Result.success()
    }

    @SuppressLint("MissingPermission") // se revisa abajo
    private fun notify(title: String, text: String) {
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Push.CHANNEL_TOGETHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NAME = "duo-reminder"
        private const val NOTIFICATION_ID = 20

        /** Una vez al día, empezando en las próximas 9:00. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DuoReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
