@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.lisplanner.app.platform

import com.lisplanner.app.data.*
import com.lisplanner.app.notifications.Alert
import com.lisplanner.app.notifications.ReminderSettings
import com.lisplanner.app.notifications.alertSummary
import com.lisplanner.app.notifications.computeAlerts
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.number
import kotlinx.serialization.json.Json
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

/* ---------- Conversión de bytes ---------- */

fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** Lee un fichero elegido en Archivos o abierto desde otra app («Abrir con LIS Planner»). */
fun readPickedFile(url: NSURL): PickedFile? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return PickedFile(url.lastPathComponent ?: "proyecto", data.toByteArray())
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/* ---------- Almacenamiento ---------- */

/** projects.json en la carpeta Documentos de la app (incluida en la copia de iCloud). */
class IosFileStorage(private val path: String) : ProjectStorage {
    override fun read(): String? = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

    override fun write(text: String) {
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}

private fun documentsDir(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

/* ---------- Plataforma ---------- */

class IosPlatform : Platform {
    /** Controlador raíz de Compose, para presentar hojas del sistema (compartir, selector de ficheros). */
    var rootController: UIViewController? = null

    /** Proyecto a abrir al tocar una notificación. */
    val openProjectRequest = MutableStateFlow<String?>(null)

    /** Fichero abierto desde otra app (Archivos, Mail…). */
    val importRequest = MutableStateFlow<PickedFile?>(null)

    /** Mensaje breve que se muestra sobre la interfaz (equivalente al Toast de Android). */
    val message = MutableStateFlow<String?>(null)

    override val storage: ProjectStorage = IosFileStorage("${documentsDir()}/projects.json")

    override val reminders = IosReminders(this)

    override fun parseMpp(bytes: ByteArray, fileName: String): ImportPreview? = null

    override fun shareFile(bytes: ByteArray, fileName: String, mimeType: String, title: String) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName
        bytes.toNSData().writeToFile(path, atomically = true)
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        val top = topController() ?: return
        // En iPad la hoja se muestra como popover y necesita un punto de anclaje.
        sheet.popoverPresentationController?.let { pop ->
            pop.sourceView = top.view
            val width = top.view.bounds.useContents { size.width }
            pop.sourceRect = CGRectMake(width - 40.0, 60.0, 1.0, 1.0)
        }
        top.presentViewController(sheet, animated = true, completion = null)
    }

    override fun showMessage(text: String) {
        message.value = text
    }

    fun topController(): UIViewController? {
        var vc = rootController ?: return null
        while (true) vc = vc.presentedViewController ?: return vc
    }
}

/**
 * Recordatorios con notificaciones locales. iOS no permite ejecutar código a diario en segundo
 * plano, así que cada vez que cambian los datos se programan los avisos de los próximos días con
 * su contenido ya calculado (iOS admite 64 pendientes; usamos 14).
 */
class IosReminders(private val platform: IosPlatform) : ReminderPlatform {
    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val defaults = NSUserDefaults.standardUserDefaults
    private val json = Json { ignoreUnknownKeys = true }
    private var lastProjects: List<Project> = emptyList()
    private var authorized = false

    private val _settings = MutableStateFlow(
        defaults.stringForKey(KEY)?.let { runCatching { json.decodeFromString(ReminderSettings.serializer(), it) }.getOrNull() }
            ?: ReminderSettings(),
    )
    override val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    private val delegate = NotificationDelegate { id -> platform.openProjectRequest.value = id }

    init {
        center.delegate = delegate
        refreshPermission()
    }

    fun refreshPermission() {
        center.getNotificationSettingsWithCompletionHandler { s ->
            val status = s?.authorizationStatus
            authorized = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional ||
                status == UNAuthorizationStatusEphemeral
        }
    }

    override fun update(settings: ReminderSettings) {
        _settings.value = settings
        defaults.setObject(json.encodeToString(ReminderSettings.serializer(), settings), forKey = KEY)
        schedule()
    }

    override fun hasPermission(): Boolean = authorized

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override fun sendNow(alerts: List<Alert>): Int {
        refreshPermission()
        val byProject = alerts.groupBy { it.projectId }
        byProject.values.forEach { list ->
            val content = UNMutableNotificationContent()
            content.setTitle(list.first().projectName)
            content.setBody(body(list))
            content.setSound(UNNotificationSound.defaultSound)
            content.setThreadIdentifier(list.first().projectId)
            content.setUserInfo(mapOf<Any?, Any?>(PROJECT_ID to list.first().projectId))
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("now-${list.first().projectId}", content, null),
                withCompletionHandler = null,
            )
        }
        return byProject.size
    }

    override fun onDataChanged(projects: List<Project>) {
        lastProjects = projects
        schedule()
    }

    private fun schedule() {
        center.removePendingNotificationRequestsWithIdentifiers((0 until DAYS).map { "$DAILY$it" })
        val s = _settings.value
        if (!s.enabled) return
        val first = today()
        for (i in 0 until DAYS) {
            val day = first.plusDays(i)
            val alerts = computeAlerts(lastProjects, s, day)
            if (alerts.isEmpty()) continue
            val byProject = alerts.groupBy { it.projectId }
            val content = UNMutableNotificationContent()
            if (byProject.size == 1) {
                content.setTitle(alerts.first().projectName)
                content.setBody(body(alerts))
                content.setUserInfo(mapOf<Any?, Any?>(PROJECT_ID to alerts.first().projectId))
            } else {
                content.setTitle("LIS Planner · ${byProject.size} proyectos con avisos")
                content.setBody(byProject.values.joinToString("\n") { "${it.first().projectName}: ${alertSummary(it)}" })
            }
            content.setSound(UNNotificationSound.defaultSound)
            val at = NSDateComponents()
            at.setYear(day.year.toLong())
            at.setMonth(day.month.number.toLong())
            at.setDay(day.day.toLong())
            at.setHour(s.hour.toLong())
            at.setMinute(s.minute.toLong())
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(
                    "$DAILY$i", content, UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false),
                ),
                withCompletionHandler = null,
            )
        }
    }

    private fun body(list: List<Alert>): String = buildString {
        append(alertSummary(list))
        list.take(4).forEach { append("\n• ${it.title} — ${it.detail}") }
        if (list.size > 4) append("\ny ${list.size - 4} más…")
    }

    private companion object {
        const val KEY = "reminderSettings"
        const val PROJECT_ID = "projectId"
        const val DAILY = "daily-"
        const val DAYS = 14
    }
}

private class NotificationDelegate(val onOpen: (String) -> Unit) : NSObject(), UNUserNotificationCenterDelegateProtocol {
    // Mostrar el aviso aunque la app esté abierta.
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
        )
    }

    // Al tocar el aviso, abrir su proyecto.
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val id = didReceiveNotificationResponse.notification.request.content.userInfo[PROJECT_ID_KEY] as? String
        if (id != null) onOpen(id)
        withCompletionHandler()
    }
}

private const val PROJECT_ID_KEY = "projectId"

/** Instancia única: los avisos y los ficheros abiertos pueden llegar antes de que exista la interfaz. */
val iosPlatform: IosPlatform by lazy { IosPlatform() }
