package com.lisplanner.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.lisplanner.app.data.ImportPreview
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStorage
import com.lisplanner.app.notifications.Alert
import com.lisplanner.app.notifications.ReminderSettings
import kotlinx.coroutines.flow.StateFlow

/** Fichero elegido por el usuario (o recibido de otra app) para importar. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Lo que cada sistema (Android, iOS) hace a su manera. El resto de la app —datos, plantillas,
 * planificación, interfaz e informes— es común.
 */
interface Platform {
    /** Fichero JSON donde se guardan los proyectos. */
    val storage: ProjectStorage

    val reminders: ReminderPlatform

    /** Lee un .mpp de MS Project. Devuelve null si la plataforma no puede (iOS: solo XML). */
    fun parseMpp(bytes: ByteArray, fileName: String): ImportPreview?

    /** Guarda el fichero y abre la hoja de compartir del sistema (correo, Drive, Archivos…). */
    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String, title: String)

    /** Mensaje breve (Toast en Android). */
    fun showMessage(text: String)
}

/** Recordatorios diarios de tareas. */
interface ReminderPlatform {
    val settings: StateFlow<ReminderSettings>

    /** Guarda los ajustes y reprograma el aviso diario si cambian la hora o la activación. */
    fun update(settings: ReminderSettings)

    fun hasPermission(): Boolean

    /** Abre los ajustes de notificaciones de la app en el sistema. */
    fun openSystemSettings()

    /** Publica ya los avisos. Devuelve cuántos proyectos tienen avisos. */
    fun sendNow(alerts: List<Alert>): Int

    /**
     * Llamado al abrir la app y cada vez que cambian los proyectos. En iOS los avisos locales se
     * programan aquí con su contenido ya calculado; en Android los calcula un proceso diario.
     */
    fun onDataChanged(projects: List<Project>)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("No se ha proporcionado la plataforma") }

/** Lanzador del selector de ficheros del sistema. Devuelve una función que lo abre. */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit

/** Lanzador de la petición de permiso de notificaciones (Android 13+, iOS). */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Botón/gesto «Atrás» del sistema. En iOS no hay botón: se usa la navegación de la propia app. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
