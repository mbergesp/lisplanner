package com.lisplanner.app.notifications

import com.lisplanner.app.data.*

import android.Manifest
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
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lisplanner.app.platform.readProjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/* ======================================================================
 *  Preferencias
 * ====================================================================== */

class ReminderSettingsStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    private fun read() = ReminderSettings(
        enabled = prefs.getBoolean("enabled", true),
        hour = prefs.getInt("hour", 8),
        minute = prefs.getInt("minute", 30),
        leadDays = prefs.getInt("leadDays", 2),
        startSoon = prefs.getBoolean("startSoon", true),
        dueSoon = prefs.getBoolean("dueSoon", true),
        overdue = prefs.getBoolean("overdue", true),
        milestones = prefs.getBoolean("milestones", true),
        risks = prefs.getBoolean("risks", true),
        onlyAssignee = prefs.getString("onlyAssignee", "").orEmpty(),
    )

    fun update(s: ReminderSettings) {
        prefs.edit {
            putBoolean("enabled", s.enabled); putInt("hour", s.hour); putInt("minute", s.minute)
            putInt("leadDays", s.leadDays); putBoolean("startSoon", s.startSoon); putBoolean("dueSoon", s.dueSoon)
            putBoolean("overdue", s.overdue); putBoolean("milestones", s.milestones); putBoolean("risks", s.risks)
            putString("onlyAssignee", s.onlyAssignee)
        }
        _settings.value = s
    }

    companion object {
        @Volatile private var instance: ReminderSettingsStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ReminderSettingsStore(context).also { instance = it } }
    }
}

/* ======================================================================
 *  Notificaciones
 * ====================================================================== */

object Notifier {
    private fun notificationIcon(context: Context): Int =
        context.resources.getIdentifier("ic_notification", "drawable", context.packageName).takeIf { it != 0 }
            ?: context.applicationInfo.icon

    const val EXTRA_PROJECT_ID = "projectId"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_OVERDUE = "overdue"
    private const val GROUP = "com.lisplanner.app.REMINDERS"
    private const val SUMMARY_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios de tareas", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tareas que empiezan o vencen pronto, hitos y riesgos con fecha límite" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERDUE, "Tareas atrasadas", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tareas cuya fecha de fin ya ha pasado" },
        )
    }

    fun hasPermission(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openProject(context: Context, projectId: String?, requestCode: Int): PendingIntent {
        // La actividad principal está en el módulo de la app: se usa la intención de lanzamiento del paquete.
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (projectId != null) putExtra(EXTRA_PROJECT_ID, projectId) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Una notificación por proyecto (con la lista de avisos) más un resumen agrupado. Devuelve cuántas se publicaron. */
    @android.annotation.SuppressLint("MissingPermission")
    fun post(context: Context, alerts: List<Alert>): Int {
        if (!hasPermission(context)) return 0
        createChannels(context)
        val nm = NotificationManagerCompat.from(context)
        nm.cancelAll()
        if (alerts.isEmpty()) return 0

        val byProject = alerts.groupBy { it.projectId }
        byProject.entries.forEachIndexed { i, (projectId, list) ->
            val overdue = list.count { it.kind == AlertKind.ATRASADA }
            val title = list.first().projectName
            val summary = alertSummary(list)
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(summary)
            list.take(7).forEach { style.addLine("${it.kind.label}: ${it.title} — ${it.detail}") }
            if (list.size > 7) style.addLine("y ${list.size - 7} más…")

            val n = NotificationCompat.Builder(context, if (overdue > 0) CHANNEL_OVERDUE else CHANNEL_REMINDERS)
                .setSmallIcon(notificationIcon(context))
                .setColor(0xFF0B5C7A.toInt())
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openProject(context, projectId, 100 + i))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            nm.notify(projectId.hashCode(), n)
        }
        val totalOverdue = alerts.count { it.kind == AlertKind.ATRASADA }
        nm.notify(
            SUMMARY_ID,
            NotificationCompat.Builder(context, if (totalOverdue > 0) CHANNEL_OVERDUE else CHANNEL_REMINDERS)
                .setSmallIcon(notificationIcon(context))
                .setColor(0xFF0B5C7A.toInt())
                .setContentTitle("${alerts.size} avisos en ${byProject.size} proyecto${if (byProject.size > 1) "s" else ""}")
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("LIS Planner"))
                .setContentIntent(openProject(context, null, 99))
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build(),
        )
        return byProject.size
    }
}

/* ======================================================================
 *  Programación diaria con WorkManager
 * ====================================================================== */

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = ReminderSettingsStore.get(applicationContext).settings.value
        if (!settings.enabled) return Result.success()
        Notifier.post(applicationContext, computeAlerts(readProjects(applicationContext), settings))
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK = "daily-reminders"

    fun schedule(context: Context, s: ReminderSettings, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!s.enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(s.hour, s.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        // Al cambiar la hora hay que reprogramar desde cero; al abrir la app se conserva la programación existente.
        wm.enqueueUniquePeriodicWork(WORK, if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Ejecuta la comprobación ahora mismo (botón "Probar"). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
}
