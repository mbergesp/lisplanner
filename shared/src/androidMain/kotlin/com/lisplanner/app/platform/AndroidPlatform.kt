package com.lisplanner.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.lisplanner.app.data.ImportPreview
import com.lisplanner.app.data.MppImporter
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStorage
import com.lisplanner.app.data.decodeProjects
import com.lisplanner.app.notifications.Alert
import com.lisplanner.app.notifications.Notifier
import com.lisplanner.app.notifications.ReminderScheduler
import com.lisplanner.app.notifications.ReminderSettings
import com.lisplanner.app.notifications.ReminderSettingsStore
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Fichero JSON de proyectos en el almacenamiento interno de la app. */
class FileProjectStorage(private val file: File) : ProjectStorage {
    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}

fun projectsFile(context: Context) = File(context.filesDir, "projects.json")

/** Lectura para procesos en segundo plano (recordatorios diarios). */
fun readProjects(context: Context): List<Project> = decodeProjects(FileProjectStorage(projectsFile(context)).read())

class AndroidPlatform(context: Context) : Platform {
    private val app = context.applicationContext

    override val storage: ProjectStorage = FileProjectStorage(projectsFile(app))

    override val reminders: ReminderPlatform = object : ReminderPlatform {
        private val store = ReminderSettingsStore.get(app)
        override val settings: StateFlow<ReminderSettings> = store.settings

        init {
            Notifier.createChannels(app)
            ReminderScheduler.schedule(app, store.settings.value, replace = false)
        }

        override fun update(settings: ReminderSettings) {
            val old = store.settings.value
            store.update(settings)
            if (old.enabled != settings.enabled || old.hour != settings.hour || old.minute != settings.minute) {
                ReminderScheduler.schedule(app, settings, replace = true)
            }
        }

        override fun hasPermission(): Boolean = Notifier.hasPermission(app)

        override fun openSystemSettings() {
            app.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        override fun sendNow(alerts: List<Alert>): Int = Notifier.post(app, alerts)

        // En Android los avisos los calcula cada día un proceso de WorkManager leyendo el fichero.
        override fun onDataChanged(projects: List<Project>) = Unit
    }

    override fun parseMpp(bytes: ByteArray, fileName: String): ImportPreview =
        bytes.inputStream().use { MppImporter.read(it, fileName) }

    override fun shareFile(bytes: ByteArray, fileName: String, mimeType: String, title: String) {
        val file = File(app.cacheDir, "reports").apply { mkdirs() }.resolve(fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun showMessage(text: String) {
        Toast.makeText(app, text, Toast.LENGTH_LONG).show()
    }
}

/** Lee un fichero de un content:// (selector de ficheros, «Abrir con», «Compartir»). */
fun readPickedFile(context: Context, uri: Uri): PickedFile? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "proyecto"
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    PickedFile(name, bytes)
}.getOrNull()

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPicked(uri?.let { readPickedFile(context, it) })
    }
    return { launcher.launch(arrayOf("application/vnd.ms-project", "application/xml", "text/xml", "application/octet-stream", "*/*")) }
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(true)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
