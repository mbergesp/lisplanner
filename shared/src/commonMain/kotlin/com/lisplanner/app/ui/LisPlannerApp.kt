package com.lisplanner.app.ui

import com.lisplanner.app.data.*

import com.lisplanner.app.platform.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lisplanner.app.data.Project
import com.lisplanner.app.ui.agenda.AgendaScreen
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.dashboard.DashboardScreen
import com.lisplanner.app.ui.editors.ProjectEditor
import com.lisplanner.app.ui.projects.ProjectDetailScreen
import com.lisplanner.app.ui.projects.ProjectListScreen
import com.lisplanner.app.ui.projects.ImportFlow
import com.lisplanner.app.ui.settings.SettingsScreen
import androidx.compose.material.icons.outlined.Settings

private val Destination.label get() = when (this) {
    Destination.INICIO -> "Inicio"
    Destination.PROYECTOS -> "Proyectos"
    Destination.AGENDA -> "Agenda"
    Destination.AJUSTES -> "Ajustes"
}

private val Destination.icon: ImageVector get() = when (this) {
    Destination.INICIO -> Icons.Outlined.Home
    Destination.PROYECTOS -> Icons.Outlined.Science
    Destination.AGENDA -> Icons.Outlined.CalendarMonth
    Destination.AJUSTES -> Icons.Outlined.Settings
}

/**
 * Raíz de la app. [NavigationSuiteScaffold] elige automáticamente la navegación según el tamaño:
 * barra inferior en la pantalla exterior del Fold y rail lateral con el Fold desplegado.
 */
@Composable
fun LisPlannerApp(
    openProjectRequest: String? = null,
    onOpenRequestHandled: () -> Unit = {},
    importRequest: PickedFile? = null,
    onImportRequestHandled: () -> Unit = {},
) {
    val platform = LocalPlatform.current
    val vm: AppViewModel = viewModel { AppViewModel(platform) }
    val reminderSettings by vm.reminderSettings.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val startImport = rememberFilePicker { file -> if (file != null) vm.importFrom(file) }
    // Fichero .mpp o .xml abierto o compartido desde otra app (correo, Archivos, Drive…).
    LaunchedEffect(importRequest) {
        if (importRequest != null) {
            vm.navigate(Destination.PROYECTOS)
            vm.importFrom(importRequest)
            onImportRequestHandled()
        }
    }
    LaunchedEffect(openProjectRequest) {
        if (openProjectRequest != null) {
            vm.openProject(openProjectRequest)
            onOpenRequestHandled()
        }
    }
    // Primera vez: pedir permiso de notificaciones si los recordatorios están activos.
    val requestPermission = rememberNotificationPermissionRequest { }
    LaunchedEffect(Unit) {
        if (reminderSettings.enabled && !platform.reminders.hasPermission()) requestPermission()
    }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProjectId.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Destination.entries.forEach { d ->
                item(
                    selected = d == destination,
                    onClick = { vm.navigate(d) },
                    icon = { Icon(d.icon, null) },
                    label = { Text(d.label) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        when (destination) {
            Destination.INICIO -> DashboardScreen(
                projects = projects,
                onOpenProject = vm::openProject,
                onResetSample = vm::resetSampleData,
            )
            Destination.PROYECTOS -> ProjectsListDetail(
                projects = projects,
                selectedId = selectedId,
                vm = vm,
                onCreate = { creating = true },
                onImport = startImport,
            )
            Destination.AGENDA -> AgendaScreen(
                projects = projects,
                onSaveTask = vm::saveTask,
                onDeleteTask = vm::deleteTask,
            )
            Destination.AJUSTES -> SettingsScreen(
                projects = projects,
                settings = reminderSettings,
                onChange = vm::updateReminders,
                onSendNow = {
                    val n = vm.sendRemindersNow()
                    platform.showMessage(
                        if (n == 0) "No hay avisos con la configuración actual" else "Avisos enviados ($n proyecto${if (n > 1) "s" else ""})",
                    )
                },
                onOpenProject = vm::openProject,
                onResetSample = vm::resetSampleData,
            )
        }
    }

    PlatformBackHandler(enabled = destination != Destination.INICIO && selectedId == null) { vm.navigate(Destination.INICIO) }

    ImportFlow(importState, onConfirm = vm::confirmImport, onDismiss = vm::dismissImport)

    if (creating) {
        ProjectEditor(
            project = null,
            onDismiss = { creating = false },
            onSave = { p, template -> vm.saveProject(p, template); creating = false },
            onDelete = null,
        )
    }
}

/**
 * Lista + detalle adaptativo.
 * - Pantalla estrecha (Fold plegado): una sola vista, el detalle se abre encima de la lista.
 * - Pantalla ancha (Fold desplegado): lista y detalle lado a lado.
 * - Fold semiabierto en modo libro: la división coincide con la bisagra para que nada quede en el pliegue.
 */
@Composable
private fun ProjectsListDetail(
    projects: List<Project>,
    selectedId: String?,
    vm: AppViewModel,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    val selected = projects.firstOrNull { it.id == selectedId }
    val hinge = currentWindowAdaptiveInfo().windowPosture.hingeList.firstOrNull { it.isVertical && it.isSeparating }
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    var detailFullWidth by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { originX = it.positionInWindow().x }) {
        val twoPane = maxWidth >= 700.dp || hinge != null

        if (twoPane) {
            val hingeSplit = hinge?.let { with(density) { (it.bounds.left - originX).toDp() } }?.takeIf { it in 240.dp..(maxWidth - 240.dp) }
            val listWidth = hingeSplit ?: (maxWidth * 0.36f).coerceIn(280.dp, 420.dp)
            val gap = if (hingeSplit != null) with(density) { hinge.bounds.width.toDp() } else 0.dp

            // En dos paneles siempre mostramos algo a la derecha.
            LaunchedEffect(projects.isNotEmpty(), selected == null) {
                if (selected == null) projects.firstOrNull()?.let { vm.openProject(it.id) }
            }
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(!detailFullWidth || selected == null) {
                    Row {
                        ProjectListScreen(projects, selectedId, onOpen = vm::openProject, onCreate = onCreate, onImport = onImport, modifier = Modifier.width(listWidth).fillMaxHeight())
                        if (gap > 0.dp) Spacer(Modifier.width(gap)) else VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) {
                        ProjectDetailScreen(
                            selected, vm, showBack = false, onBack = {},
                            fullWidth = detailFullWidth,
                            onToggleFullWidth = { detailFullWidth = !detailFullWidth },
                        )
                    } else {
                        EmptyState(Icons.Outlined.Science, "Selecciona un proyecto", "Verás aquí su resumen, Gantt, tareas e integraciones.")
                    }
                }
            }
        } else {
            if (selected != null) {
                PlatformBackHandler { vm.openProject(null) }
                ProjectDetailScreen(selected, vm, showBack = true, onBack = { vm.openProject(null) })
            } else {
                ProjectListScreen(projects, selectedId, onOpen = vm::openProject, onCreate = onCreate, onImport = onImport)
            }
        }
    }
}
