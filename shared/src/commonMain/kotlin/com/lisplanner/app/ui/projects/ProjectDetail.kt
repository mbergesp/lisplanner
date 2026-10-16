package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Integration
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Task
import com.lisplanner.app.data.TaskStatus
import com.lisplanner.app.data.ProjectType
import com.lisplanner.app.ui.AppViewModel
import com.lisplanner.app.ui.components.ProgressRing
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.components.relativeDays
import com.lisplanner.app.ui.editors.IntegrationEditor
import com.lisplanner.app.ui.editors.MemberEditor
import com.lisplanner.app.ui.editors.ProjectEditor
import com.lisplanner.app.ui.editors.TaskEditor
import com.lisplanner.app.ui.editors.RiskEditor
import com.lisplanner.app.ui.editors.PhaseManager
import androidx.compose.material.icons.outlined.Reorder
import com.lisplanner.app.data.RiskItem
import com.lisplanner.app.export.Reports
import com.lisplanner.app.platform.LocalPlatform
import com.lisplanner.app.export.ProjectXmlExport
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lisplanner.app.ui.gantt.GanttChart

enum class DetailTab(val label: String, val icon: ImageVector) {
    RESUMEN("Resumen", Icons.Outlined.Dashboard),
    GANTT("Gantt", Icons.Outlined.ViewTimeline),
    TAREAS("Tareas", Icons.Outlined.Checklist),
    INTEGRACIONES("Integraciones", Icons.Outlined.Cable),
    RIESGOS("Riesgos", Icons.Outlined.Shield),
}

/** Qué hoja de edición está abierta. `null` en el elemento significa "crear nuevo". */
private sealed interface Editing {
    data object Project : Editing
    data object Member : Editing
    data class TaskEdit(val task: Task?) : Editing
    data class IntegrationEdit(val integration: Integration?) : Editing
    data class RiskEdit(val risk: RiskItem?) : Editing
    data object Phases : Editing
}

@Composable
fun ProjectDetailScreen(
    project: Project,
    vm: AppViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean? = null,
    onToggleFullWidth: () -> Unit = {},
) {
    var tab by rememberSaveable(project.id) { mutableStateOf(DetailTab.RESUMEN) }
    var editing by remember(project.id) { mutableStateOf<Editing?>(null) }
    var exporting by remember { mutableStateOf<String?>(null) }
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    fun deleteWithUndo(task: Task) {
        val before = vm.deleteTaskForUndo(project.id, task.id) ?: return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                "Tarea «${task.name}» eliminada", actionLabel = "Deshacer",
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restoreTasks(project.id, before)
        }
    }
    var confirmSchedule by remember { mutableStateOf(false) }
    var confirmAlign by remember { mutableStateOf(false) }
    fun export(label: String, fileName: String, mimeType: String = "application/pdf", build: () -> ByteArray) {
        exporting = label
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { build() } }
                .onSuccess { platform.shareFile(it, fileName, mimeType, "$label · ${project.name}") }
                .onFailure { platform.showMessage("No se pudo exportar: ${it.message}") }
            exporting = null
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        floatingActionButton = {
            when (tab) {
                DetailTab.GANTT, DetailTab.TAREAS -> ExtendedFloatingActionButton(
                    onClick = { editing = Editing.TaskEdit(null) },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Tarea") },
                )
                DetailTab.INTEGRACIONES -> ExtendedFloatingActionButton(
                    onClick = { editing = Editing.IntegrationEdit(null) },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Integración") },
                )
                DetailTab.RIESGOS -> ExtendedFloatingActionButton(
                    onClick = { editing = Editing.RiskEdit(null) },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Registrar") },
                )
                DetailTab.RESUMEN -> Unit
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ProjectHero(
                project = project,
                condensed = tab == DetailTab.GANTT,
                showBack = showBack,
                onBack = onBack,
                onEdit = { editing = Editing.Project },
                onExportReport = { export("Informe de estado", Reports.fileName(project, "informe")) { Reports.statusReport(project) } },
                onExportGantt = { export("Diagrama de Gantt", Reports.fileName(project, "gantt")) { Reports.gantt(project) } },
                onExportMsProject = { export("MS Project", Reports.fileName(project, "msproject", "xml"), "application/xml") { ProjectXmlExport.export(project) } },
                onScheduleByHours = { confirmSchedule = true },
                onManagePhases = { editing = Editing.Phases },
                onAlignGoLive = { confirmAlign = true },
                fullWidth = fullWidth,
                onToggleFullWidth = onToggleFullWidth,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PrimaryScrollableTabRow(
                selectedTabIndex = tab.ordinal,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                DetailTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) },
                        icon = { Icon(t.icon, null) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(tab, label = "tab") { current ->
                    when (current) {
                        DetailTab.RESUMEN -> OverviewTab(
                            project = project,
                            onAddMember = { editing = Editing.Member },
                            onRemoveMember = { vm.removeMember(project.id, it) },
                            onTaskClick = { editing = Editing.TaskEdit(it) },
                            onManagePhases = { editing = Editing.Phases },
                        )
                        DetailTab.GANTT -> GanttChart(project, onTaskClick = { editing = Editing.TaskEdit(it) })
                        DetailTab.TAREAS -> TasksTab(
                            project = project,
                            onTaskClick = { editing = Editing.TaskEdit(it) },
                            onToggleDone = { t -> vm.saveTask(project.id, t.copy(progress = if (t.progress >= 100) 0 else 100)) },
                            onReorder = { vm.reorderTasks(project.id, it) },
                            onDelete = ::deleteWithUndo,
                        )
                        DetailTab.INTEGRACIONES -> IntegrationsTab(project, onClick = { editing = Editing.IntegrationEdit(it) })
                        DetailTab.RIESGOS -> RisksTab(project, onClick = { editing = Editing.RiskEdit(it) })
                    }
                }
            }
        }
    }

    when (val e = editing) {
        null -> Unit
        Editing.Project -> ProjectEditor(
            project = project,
            onDismiss = { editing = null },
            onSave = { p, template -> vm.saveProject(p, template); editing = null },
            onDelete = { editing = null; vm.deleteProject(project.id) },
        )
        Editing.Member -> MemberEditor(
            onDismiss = { editing = null },
            onSave = { vm.addMember(project.id, it); editing = null },
        )
        is Editing.TaskEdit -> TaskEditor(
            task = e.task, project = project,
            onDismiss = { editing = null },
            onSave = { vm.saveTask(project.id, it); editing = null },
            onDelete = e.task?.let { t -> { vm.deleteTask(project.id, t.id); editing = null } },
        )
        is Editing.IntegrationEdit -> IntegrationEditor(
            integration = e.integration,
            onDismiss = { editing = null },
            onSave = { vm.saveIntegration(project.id, it); editing = null },
            onDelete = e.integration?.let { i -> { vm.deleteIntegration(project.id, i.id); editing = null } },
        )
        Editing.Phases -> PhaseManager(
            project = project,
            onDismiss = { editing = null },
            onSave = { phases, move, delete -> vm.updatePhases(project.id, phases, move, delete); editing = null },
        )
        is Editing.RiskEdit -> RiskEditor(
            risk = e.risk, project = project,
            onDismiss = { editing = null },
            onSave = { vm.saveRisk(project.id, it); editing = null },
            onDelete = e.risk?.let { r -> { vm.deleteRisk(project.id, r.id); editing = null } },
        )
    }
    if (confirmAlign) {
        val target = project.projectedGoLive
        val word = if (project.type == ProjectType.ACTUALIZACION) "paso a producción" else "Go-Live"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmAlign = false },
            icon = { Icon(Icons.Outlined.Schedule, null) },
            title = { Text("Ajustar fecha de $word") },
            text = { Text("La fecha planificada (${project.goLiveDate.long()}) pasará a ser la calculada a partir de las tareas: ${target?.long()}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAlign = false
                    if (target != null) vm.saveProject(project.copy(goLiveDate = target), applyTemplate = false)
                }) { Text("Ajustar") }
            },
            dismissButton = { TextButton(onClick = { confirmAlign = false }) { Text("Cancelar") } },
        )
    }
    if (confirmSchedule) {
        val candidates = project.tasks.count { !it.autoSchedule && !it.isMilestone && it.estimatedHours > 0 }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSchedule = false },
            icon = { Icon(Icons.Outlined.Schedule, null) },
            title = { Text("Programar fechas por horas") },
            text = {
                Text(
                    "$candidates tareas con horas estimadas pasarán a calcular sus fechas automáticamente:\n\n" +
                        "• Duración = horas ÷ dedicación diaria (se calcula para mantener la duración actual).\n" +
                        "• Cada tarea empieza el día laborable siguiente al fin de sus predecesoras.\n\n" +
                        "Algunas fechas pueden moverse unos días para respetar las dependencias. Podrás volver a fechas manuales en cada tarea.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmSchedule = false; vm.scheduleByHours(project.id) }) { Text("Programar") } },
            dismissButton = { TextButton(onClick = { confirmSchedule = false }) { Text("Cancelar") } },
        )
    }
    exporting?.let { label ->
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Generando $label…")
                }
            }
        }
    }
}

@Composable
private fun ProjectHero(
    project: Project,
    condensed: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExportReport: () -> Unit,
    onExportGantt: () -> Unit,
    onExportMsProject: () -> Unit,
    onScheduleByHours: () -> Unit,
    onManagePhases: () -> Unit,
    onAlignGoLive: () -> Unit,
    fullWidth: Boolean?,
    onToggleFullWidth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.linearGradient(listOf(Color(0xFF0B5C7A), Color(0xFF138A7E)))
    val onHero = Color.White
    val buttonColors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = onHero)
    val days = project.daysToGoLive()
    val done = project.tasks.count { it.status() == TaskStatus.COMPLETADA }
    val overdue = project.overdueTasks().size

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(gradient)
            .padding(horizontal = 16.dp, vertical = if (condensed) 10.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack, colors = buttonColors) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleLarge, color = onHero, maxLines = if (condensed) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(project.client, project.site).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (condensed) {
                Spacer(Modifier.width(8.dp))
                ProgressRing(project.progress, size = 44.dp, stroke = 4.dp, color = onHero, textColor = onHero)
            }
            Spacer(Modifier.width(8.dp))
            if (fullWidth != null) {
                IconButton(onClick = onToggleFullWidth, colors = buttonColors) {
                    Icon(
                        if (fullWidth) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                        if (fullWidth) "Mostrar lista" else "Ampliar",
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }, colors = buttonColors) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar proyecto") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Gestionar fases") }, leadingIcon = { Icon(Icons.Outlined.Reorder, null) }, onClick = { menu = false; onManagePhases() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Informe de estado (PDF)") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = { menu = false; onExportReport() })
                    DropdownMenuItem(text = { Text("Diagrama de Gantt (PDF)") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { menu = false; onExportGantt() })
                    DropdownMenuItem(text = { Text("Microsoft Project (.xml)") }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = { menu = false; onExportMsProject() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Programar fechas por horas") }, leadingIcon = { Icon(Icons.Outlined.Schedule, null) }, onClick = { menu = false; onScheduleByHours() })
                }
            }
        }
        AnimatedVisibility(!condensed) {
            Column {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressRing(project.progress, size = 68.dp, stroke = 7.dp, color = onHero, textColor = onHero)
                    HeroStat(if (project.type == ProjectType.ACTUALIZACION) "Producción" else "Go-Live", project.goLiveDate.short(), relativeDays(days), Modifier.weight(1f))
                    HeroStat("Tareas", "$done / ${project.tasks.size}", "completadas", Modifier.weight(1f))
                    HeroStat("Atrasadas", "$overdue", if (overdue == 0) "al día" else "a resolver", Modifier.weight(1f), highlight = overdue > 0)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(onHero))
                    Spacer(Modifier.width(6.dp))
                    Text(project.status.label, style = MaterialTheme.typography.labelLarge, color = onHero)
                    val info = listOfNotNull(
                        project.type.label,
                        project.lisSystem.ifBlank { null },
                        if (project.type == ProjectType.ACTUALIZACION && (project.currentVersion.isNotBlank() || project.targetVersion.isNotBlank()))
                            "${project.currentVersion.ifBlank { "?" }} → ${project.targetVersion.ifBlank { "?" }}" else null,
                    ).joinToString("  ·  ")
                    run {
                        Text("  ·  $info", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CalculatedEndBar(project, onAlignGoLive)
            }
        }
    }
}

/** Fin calculado a partir de las tareas y desvío del Go-Live respecto al planificado. */
@Composable
private fun CalculatedEndBar(project: Project, onAlignGoLive: () -> Unit) {
    val end = project.calculatedEnd ?: return
    val dev = project.goLiveDeviationDays
    val goLiveWord = if (project.type == ProjectType.ACTUALIZACION) "Producción" else "Go-Live"
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Fin calculado: ${end.long()}", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    dev > 0 -> "$goLiveWord previsto $dev días tarde (${project.projectedGoLive?.short()})"
                    dev < 0 -> "$goLiveWord previsto ${-dev} días antes (${project.projectedGoLive?.short()})"
                    else -> "$goLiveWord en fecha"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (dev > 0) Color(0xFFFFD6A0) else Color.White.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (dev != 0L) {
            TextButton(onClick = onAlignGoLive, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Text("Ajustar")
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, caption: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = if (highlight) Color(0xFFFFD6A0) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
