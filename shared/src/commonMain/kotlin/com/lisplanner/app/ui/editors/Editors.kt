package com.lisplanner.app.ui.editors

import com.lisplanner.app.data.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Integration
import com.lisplanner.app.data.IntegrationStatus
import com.lisplanner.app.data.IntegrationType
import com.lisplanner.app.data.LisPhase
import com.lisplanner.app.data.LisTemplate
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.data.ProjectType
import com.lisplanner.app.data.ProjectTemplates
import com.lisplanner.app.data.PhaseDef
import com.lisplanner.app.data.guessPhase
import com.lisplanner.app.data.Protocol
import com.lisplanner.app.data.Level
import com.lisplanner.app.data.RiskItem
import com.lisplanner.app.data.RiskKind
import com.lisplanner.app.data.RiskStatus
import com.lisplanner.app.data.Task
import com.lisplanner.app.data.TeamMember
import com.lisplanner.app.data.Scheduler
import com.lisplanner.app.data.WorkCalendar
import com.lisplanner.app.ui.components.DateField
import com.lisplanner.app.ui.components.DropdownField
import com.lisplanner.app.ui.components.parseHours
import kotlinx.datetime.LocalDate

/** Contenedor común: hoja inferior con título, contenido desplazable y botones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 720.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = onSave, enabled = saveEnabled) { Text("Guardar") }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Dos campos en fila cuando hay anchura (Fold desplegado), en columna si no. */
@Composable
private fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        if (maxWidth >= 480.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f)); second(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ProjectEditor(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (Project, applyTemplate: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val today = remember { today() }
    var name by remember { mutableStateOf(project?.name.orEmpty()) }
    var client by remember { mutableStateOf(project?.client.orEmpty()) }
    var site by remember { mutableStateOf(project?.site.orEmpty()) }
    var lis by remember { mutableStateOf(project?.lisSystem.orEmpty()) }
    var pm by remember { mutableStateOf(project?.projectManager.orEmpty()) }
    var start by remember { mutableStateOf(project?.startDate ?: today) }
    var goLive by remember { mutableStateOf(project?.goLiveDate ?: today.plusWeeks(24)) }
    var status by remember { mutableStateOf(project?.status ?: ProjectStatus.PLANIFICACION) }
    var description by remember { mutableStateOf(project?.description.orEmpty()) }
    var template by remember { mutableStateOf(true) }
    var type by remember { mutableStateOf(project?.type ?: ProjectType.IMPLANTACION) }
    var currentVersion by remember { mutableStateOf(project?.currentVersion.orEmpty()) }
    var targetVersion by remember { mutableStateOf(project?.targetVersion.orEmpty()) }

    EditorSheet(
        title = if (project == null) "Nuevo proyecto" else "Editar proyecto",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank() && client.isNotBlank() && goLive.isAfter(start),
        onDelete = onDelete,
        onSave = {
            val base = project ?: Project(name = name, client = client, startDate = start, goLiveDate = goLive)
            val team = if (project == null && pm.isNotBlank()) listOf(TeamMember(name = pm.trim(), role = "Jefe de proyecto")) else base.team
            onSave(
                base.copy(
                    name = name.trim(), client = client.trim(), site = site.trim(), lisSystem = lis.trim(),
                    projectManager = pm.trim(), startDate = start, goLiveDate = goLive, status = status,
                    description = description.trim(), team = team,
                    type = type, currentVersion = currentVersion.trim(), targetVersion = targetVersion.trim(),
                ),
                project == null && template,
            )
        },
    ) {
        if (project == null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProjectType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            // Una actualización suele ser más corta: se propone un go-live a 12 semanas.
                            if (t != type && goLive == start.plusWeeks(if (type == ProjectType.IMPLANTACION) 24 else 12)) {
                                goLive = start.plusWeeks(if (t == ProjectType.IMPLANTACION) 24 else 12)
                            }
                            type = t
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, ProjectType.entries.size),
                        label = { Text(t.label) },
                    )
                }
            }
        }
        Field(name, { name = it }, "Nombre del proyecto")
        FieldPair({ Field(client, { client = it }, "Cliente / hospital", modifier = it) }, { Field(site, { site = it }, "Laboratorio / áreas", modifier = it) })
        FieldPair({ Field(lis, { lis = it }, if (type == ProjectType.ACTUALIZACION) "Sistema LIS" else "Sistema LIS / versión", modifier = it) }, { Field(pm, { pm = it }, "Jefe de proyecto", modifier = it) })
        if (type == ProjectType.ACTUALIZACION) {
            FieldPair(
                { Field(currentVersion, { currentVersion = it }, "Versión actual", modifier = it) },
                { Field(targetVersion, { targetVersion = it }, "Versión destino", modifier = it) },
            )
        }
        FieldPair({ DateField("Inicio", start, { start = it }, it) }, { DateField(if (type == ProjectType.ACTUALIZACION) "Paso a producción" else "Go-Live", goLive, { goLive = it }, it) })
        if (!goLive.isAfter(start)) {
            Text("El go-live debe ser posterior al inicio.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        DropdownField("Estado", status, ProjectStatus.entries.toList(), { it.label }, { status = it })
        Field(description, { description = it }, "Alcance / descripción", singleLine = false)
        if (project == null) {
            ListItem(
                headlineContent = { Text(if (type == ProjectType.ACTUALIZACION) "Generar plan de actualización LIS" else "Generar plan de implantación LIS") },
                supportingContent = { Text(ProjectTemplates.summary(type) + " Las fases (Consultoría, Sistemas, Configuración, Interoperabilidad, Formación, Despliegue) se pueden modificar después.") },
                trailingContent = { Switch(checked = template, onCheckedChange = { template = it }) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.widthIn(max = 720.dp),
            )
        }
    }
}

@Composable
fun TaskEditor(
    task: Task?,
    project: Project,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val today = remember { today() }
    var name by remember { mutableStateOf(task?.name.orEmpty()) }
    // Tarea nueva: se propone en la fase de la última tarea del plan y encadenada detrás de ella.
    val lastTask = remember { project.tasks.lastOrNull() }
    var phaseDef by remember { mutableStateOf(task?.let { project.phaseOf(it) } ?: lastTask?.let { project.phaseOf(it) } ?: project.phases.firstOrNull()) }
    // Categoría LIS (colores, clasificación): se conserva si no cambia la fase; si cambia, se deduce del nombre.
    val phase = if (task != null && phaseDef?.id == task.phaseId) task.phase else phaseDef?.let { guessPhase(it.name) } ?: task?.phase ?: LisPhase.CONFIGURACION
    var start by remember { mutableStateOf(task?.start ?: maxOf(today, project.startDate)) }
    var end by remember { mutableStateOf(task?.end ?: maxOf(today, project.startDate).plusDays(6)) }
    var progress by remember { mutableIntStateOf(task?.progress ?: 0) }
    var assignee by remember { mutableStateOf(task?.assignee.orEmpty()) }
    var milestone by remember { mutableStateOf(task?.isMilestone ?: false) }
    var notes by remember { mutableStateOf(task?.notes.orEmpty()) }
    var dependsOn by remember { mutableStateOf(task?.dependsOn ?: listOfNotNull(lastTask?.id)) }
    var depsTouched by remember { mutableStateOf(task != null) }
    var estimated by remember { mutableStateOf(task?.estimatedHours?.takeIf { it > 0 }?.let(::formatHoursInput).orEmpty()) }
    var spent by remember { mutableStateOf(task?.spentHours?.takeIf { it > 0 }?.let(::formatHoursInput).orEmpty()) }
    var pickingDeps by remember { mutableStateOf(false) }
    // Las tareas nuevas se programan por horas por defecto.
    var auto by remember { mutableStateOf(task?.autoSchedule ?: true) }
    var daily by remember { mutableStateOf(formatHoursInput(task?.dailyHours ?: 8.0)) }

    // Vista previa de las fechas que calculará el planificador.
    val est = parseHours(estimated) ?: 0.0
    val dailyH = parseHours(daily)?.takeIf { it > 0 } ?: 8.0
    val latestPredEnd = project.tasks.filter { it.id in dependsOn && it.id != task?.id }.maxOfOrNull { it.end }
    val workdays = Scheduler.durationDays(est, dailyH)
    val autoStart = when {
        milestone -> if (progress >= 100) start else latestPredEnd ?: start
        progress > 0 -> start
        else -> latestPredEnd?.let(WorkCalendar::nextWorkday) ?: WorkCalendar.snap(start)
    }
    val autoEnd = if (milestone) autoStart else WorkCalendar.finish(autoStart, workdays)

    fun draft(): Task {
        val base = task ?: Task(name = name, phase = phase, start = start, end = end)
        return base.copy(
            name = name.trim(), phase = phase, phaseId = phaseDef?.id.orEmpty(),
            start = if (auto) autoStart else start,
            end = if (auto) autoEnd else if (milestone) start else end,
            autoSchedule = auto, dailyHours = dailyH,
            progress = progress, assignee = assignee.trim(), isMilestone = milestone, notes = notes.trim(),
            dependsOn = dependsOn,
            estimatedHours = parseHours(estimated) ?: 0.0,
            spentHours = parseHours(spent) ?: 0.0,
        )
    }

    // Simulación del plan completo con la tarea tal como está en el formulario.
    val simulated = run {
        val d = draft()
        val tasks = if (task == null) project.tasks + d else project.tasks.map { if (it.id == d.id) d else it }
        Scheduler.reschedule(project.copy(tasks = tasks))
    }

    EditorSheet(
        title = if (task == null) "Nueva tarea" else "Editar tarea",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank() && (auto || milestone || !end.isBefore(start)) &&
            parseHours(estimated) != null && parseHours(spent) != null && (!auto || parseHours(daily)?.let { it > 0 } == true),
        onDelete = onDelete,
        onSave = { onSave(draft()) },
    ) {
        Field(name, { name = it }, "Tarea")
        if (project.phases.isNotEmpty()) {
            DropdownField("Fase", phaseDef ?: project.phases.first(), project.phases, { it.name }, { p ->
                phaseDef = p
                // Mientras no se toquen las dependencias, se encadena tras la última tarea de la fase elegida.
                if (task == null && !depsTouched) dependsOn = listOfNotNull(project.tasks.lastOrNull { it.phaseId == p.id }?.id)
            })
        } else {
            Text("El proyecto aún no tiene fases: créalas desde «Gestionar fases».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ListItem(
            headlineContent = { Text("Es un hito") },
            supportingContent = { Text("Un punto de control sin duración (aprobaciones, go-live…).") },
            trailingContent = { Switch(milestone, { milestone = it }) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        HoursFields(
            estimated = estimated, onEstimated = { estimated = it },
            spent = spent, onSpent = { spent = it },
            progress = progress,
        )
        PlanImpact(before = project, after = simulated)
        ListItem(
            headlineContent = { Text("Calcular fechas por horas") },
            supportingContent = { Text("La duración sale de las horas estimadas y la dedicación diaria (lunes a viernes); el inicio, de las predecesoras.") },
            trailingContent = { Switch(auto, { auto = it }) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        if (auto) {
            AutoScheduleFields(
                milestone = milestone, daily = daily, onDaily = { daily = it },
                start = start, onStart = { start = it }, hasPredecessors = latestPredEnd != null,
                autoStart = autoStart, autoEnd = autoEnd, workdays = workdays, dailyHours = dailyH,
            )
        } else if (milestone) {
            DateField("Fecha", start, { start = it })
        } else {
            FieldPair({ DateField("Inicio", start, { start = it; if (end.isBefore(it)) end = it }, it) }, { DateField("Fin", end, { end = it }, it) })
        }
        DependenciesField(
            project = project, selected = dependsOn, start = if (auto) autoStart else start,
            onEdit = { pickingDeps = true },
            onRemove = { id -> dependsOn = dependsOn - id; depsTouched = true },
            onShift = { newStart ->
                val length = daysBetween(start, end)
                start = newStart
                end = newStart.plusDays(length)
            },
        )
        Column {
            Text("Avance: $progress%", style = MaterialTheme.typography.labelLarge)
            Slider(value = progress.toFloat(), onValueChange = { progress = (it / 5).toInt() * 5 }, valueRange = 0f..100f)
        }
        val people = project.team.map { it.name }
        if (people.isNotEmpty()) {
            DropdownField("Responsable", assignee.ifBlank { "Sin asignar" }, listOf("Sin asignar") + people, { it }, { assignee = if (it == "Sin asignar") "" else it })
        } else {
            Field(assignee, { assignee = it }, "Responsable")
        }
        Field(notes, { notes = it }, "Notas", singleLine = false)
    }
    if (pickingDeps) {
        DependencyPicker(
            project = project, taskId = task?.id, initial = dependsOn,
            onDismiss = { pickingDeps = false },
            onConfirm = { dependsOn = it; depsTouched = true; pickingDeps = false },
        )
    }
}

@Composable
fun IntegrationEditor(
    integration: Integration?,
    onDismiss: () -> Unit,
    onSave: (Integration) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(integration?.name.orEmpty()) }
    var type by remember { mutableStateOf(integration?.type ?: IntegrationType.ANALIZADOR) }
    var manufacturer by remember { mutableStateOf(integration?.manufacturer.orEmpty()) }
    var protocol by remember { mutableStateOf(integration?.protocol ?: Protocol.ASTM) }
    var area by remember { mutableStateOf(integration?.area.orEmpty()) }
    var status by remember { mutableStateOf(integration?.status ?: IntegrationStatus.PENDIENTE) }
    var notes by remember { mutableStateOf(integration?.notes.orEmpty()) }

    EditorSheet(
        title = if (integration == null) "Nueva integración" else "Editar integración",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank(),
        onDelete = onDelete,
        onSave = {
            val base = integration ?: Integration(name = name, type = type, protocol = protocol)
            onSave(base.copy(name = name.trim(), type = type, manufacturer = manufacturer.trim(), protocol = protocol, area = area.trim(), status = status, notes = notes.trim()))
        },
    ) {
        Field(name, { name = it }, "Equipo / sistema")
        FieldPair(
            { DropdownField("Tipo", type, IntegrationType.entries.toList(), { it.label }, { type = it }, it) },
            { Field(manufacturer, { manufacturer = it }, "Fabricante / proveedor", modifier = it) },
        )
        FieldPair(
            { DropdownField("Protocolo", protocol, Protocol.entries.toList(), { it.label }, { protocol = it }, it) },
            { Field(area, { area = it }, "Área / sección", modifier = it) },
        )
        DropdownField("Estado", status, IntegrationStatus.entries.toList(), { it.label }, { status = it })
        Field(notes, { notes = it }, "Notas (puertos, incidencias, mapeos…)", singleLine = false)
    }
}

@Composable
fun MemberEditor(onDismiss: () -> Unit, onSave: (TeamMember) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(LisTemplate.defaultRoles.first()) }
    EditorSheet(
        title = "Añadir miembro",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank(),
        onSave = { onSave(TeamMember(name = name.trim(), role = role)) },
    ) {
        Field(name, { name = it }, "Nombre")
        DropdownField("Rol", role, LisTemplate.defaultRoles, { it }, { role = it })
    }
}

@Composable
fun RiskEditor(
    risk: RiskItem?,
    project: Project,
    onDismiss: () -> Unit,
    onSave: (RiskItem) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val today = remember { today() }
    var kind by remember { mutableStateOf(risk?.kind ?: RiskKind.RIESGO) }
    var title by remember { mutableStateOf(risk?.title.orEmpty()) }
    var description by remember { mutableStateOf(risk?.description.orEmpty()) }
    var probability by remember { mutableStateOf(risk?.probability ?: Level.MEDIA) }
    var impact by remember { mutableStateOf(risk?.impact ?: Level.MEDIA) }
    var status by remember { mutableStateOf(risk?.status ?: RiskStatus.ABIERTO) }
    var owner by remember { mutableStateOf(risk?.owner.orEmpty()) }
    var mitigation by remember { mutableStateOf(risk?.mitigation.orEmpty()) }
    var hasDue by remember { mutableStateOf(risk?.due != null) }
    var due by remember { mutableStateOf(risk?.due ?: today.plusWeeks(2)) }

    EditorSheet(
        title = when {
            risk != null -> "Editar ${kind.label.lowercase()}"
            else -> "Nuevo riesgo o incidencia"
        },
        onDismiss = onDismiss,
        saveEnabled = title.isNotBlank(),
        onDelete = onDelete,
        onSave = {
            val base = risk ?: RiskItem(title = title, raised = today)
            onSave(
                base.copy(
                    kind = kind, title = title.trim(), description = description.trim(), probability = probability,
                    impact = impact, status = status, owner = owner.trim(), mitigation = mitigation.trim(),
                    due = if (hasDue) due else null,
                ),
            )
        },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RiskKind.entries.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = kind == k, onClick = { kind = k },
                    shape = SegmentedButtonDefaults.itemShape(i, RiskKind.entries.size),
                    label = { Text(k.label) },
                )
            }
        }
        Text(
            if (kind == RiskKind.RIESGO) "Algo que podría ocurrir y afectar al proyecto." else "Un problema que ya está afectando al proyecto.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field(title, { title = it }, "Título")
        Field(description, { description = it }, "Descripción", singleLine = false)
        if (kind == RiskKind.RIESGO) {
            FieldPair(
                { DropdownField("Probabilidad", probability, Level.entries.toList(), { it.label }, { probability = it }, it) },
                { DropdownField("Impacto", impact, Level.entries.toList(), { it.label }, { impact = it }, it) },
            )
        } else {
            DropdownField("Impacto", impact, Level.entries.toList(), { it.label }, { impact = it })
        }
        val people = project.team.map { it.name }
        FieldPair(
            {
                if (people.isNotEmpty()) {
                    DropdownField("Responsable", owner.ifBlank { "Sin asignar" }, listOf("Sin asignar") + people, { p -> p }, { p -> owner = if (p == "Sin asignar") "" else p }, it)
                } else {
                    Field(owner, { owner = it }, "Responsable", modifier = it)
                }
            },
            { DropdownField("Estado", status, RiskStatus.entries.toList(), { s -> s.label }, { s -> status = s }, it) },
        )
        Field(mitigation, { mitigation = it }, if (kind == RiskKind.RIESGO) "Plan de mitigación" else "Acciones correctivas", singleLine = false)
        ListItem(
            headlineContent = { Text("Fecha límite") },
            trailingContent = { Switch(hasDue, { hasDue = it }) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        if (hasDue) DateField("Fecha límite", due, { due = it })
    }
}

private fun formatHoursInput(h: Double): String = if (h % 1.0 == 0.0) h.toLong().toString() else h.toString().replace('.', ',')

@Composable
private fun HoursFields(estimated: String, onEstimated: (String) -> Unit, spent: String, onSpent: (String) -> Unit, progress: Int) {
    val est = parseHours(estimated)
    val sp = parseHours(spent)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Horas de trabajo", style = MaterialTheme.typography.labelLarge)
        FieldPair(
            {
                OutlinedTextField(
                    value = estimated, onValueChange = onEstimated, label = { Text("Estimadas") }, suffix = { Text("h") },
                    singleLine = true, isError = est == null,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = it,
                )
            },
            {
                OutlinedTextField(
                    value = spent, onValueChange = onSpent, label = { Text("Imputadas") }, suffix = { Text("h") },
                    singleLine = true, isError = sp == null,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = it,
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Imputar:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(1.0, 4.0, 8.0).forEach { add ->
                androidx.compose.material3.AssistChip(
                    onClick = { onSpent(formatHoursInput((sp ?: 0.0) + add)) },
                    label = { Text("+${add.toInt()} h") },
                )
            }
        }
        if (est != null && est > 0 && sp != null) {
            // Desviación: horas imputadas frente a las que "tocaban" según el avance declarado.
            val expected = est * progress / 100.0
            val over = sp > est || (progress > 0 && sp > expected * 1.15)
            Text(
                "Pendientes según avance: ${com.lisplanner.app.ui.components.hoursLabel((est * (100 - progress) / 100.0))}" +
                    if (sp > est) " · ¡Imputadas superan la estimación!" else if (over) " · Consumo por encima del avance" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dedicación diaria y fechas calculadas para una tarea programada por horas. */
@Composable
private fun AutoScheduleFields(
    milestone: Boolean,
    daily: String,
    onDaily: (String) -> Unit,
    start: LocalDate,
    onStart: (LocalDate) -> Unit,
    hasPredecessors: Boolean,
    autoStart: LocalDate,
    autoEnd: LocalDate,
    workdays: Int,
    dailyHours: Double,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!milestone) {
                OutlinedTextField(
                    value = daily, onValueChange = onDaily,
                    label = { Text("Dedicación diaria") }, suffix = { Text("h/día") },
                    singleLine = true, isError = parseHours(daily)?.let { it > 0 } != true,
                    supportingText = { Text("8 = una persona a jornada completa · 16 = dos personas") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4.0, 6.0, 8.0, 16.0).forEach { h ->
                        androidx.compose.material3.FilterChip(
                            selected = dailyHours == h, onClick = { onDaily(formatHoursInput(h)) },
                            label = { Text("${h.toInt()} h") },
                        )
                    }
                }
            }
            if (!hasPredecessors) {
                DateField(if (milestone) "Fecha" else "Inicio (no antes de)", start, onStart)
            }
            val fmt = { d: LocalDate -> d.format(EsDateFormat("EEE d MMM yyyy")) }
            Text(
                buildString {
                    if (milestone) {
                        append("Fecha: ${fmt(autoStart)}")
                        if (hasPredecessors) append(" (fin de sus predecesoras)")
                    } else {
                        append("Inicio: ${fmt(autoStart)}")
                        if (hasPredecessors) append(" (tras sus predecesoras)")
                        append("\nFin: ${fmt(autoEnd)}")
                        append("\nDuración: $workdays jornada${if (workdays > 1) "s" else ""} laborable${if (workdays > 1) "s" else ""}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Cómo cambia el final del proyecto (y su Go-Live) con la tarea tal como está en el formulario. */
@Composable
private fun PlanImpact(before: Project, after: Project) {
    val endBefore = before.calculatedEnd
    val endAfter = after.calculatedEnd ?: return
    val shift = endBefore?.let { daysBetween(it, endAfter) } ?: 0L
    val fmt = EsDateFormat("d MMM yyyy")
    val late = after.goLiveDeviationDays > 0
    androidx.compose.material3.Surface(
        color = if (shift > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fin del proyecto con esta tarea", style = MaterialTheme.typography.labelLarge)
            Text(
                endAfter.format(fmt).replace(".", "") + when {
                    shift > 0 -> "  (+$shift días)"
                    shift < 0 -> "  ($shift días)"
                    else -> "  (sin cambios)"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (shift > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            after.projectedGoLive?.takeIf { after.goLiveMilestone != null }?.let { gl ->
                val dev = after.goLiveDeviationDays
                Text(
                    (if (after.type == com.lisplanner.app.data.ProjectType.ACTUALIZACION) "Producción prevista: " else "Go-Live previsto: ") +
                        gl.format(fmt).replace(".", "") + when {
                        dev > 0 -> " · $dev días después de lo planificado"
                        dev < 0 -> " · ${-dev} días antes de lo planificado"
                        else -> " · en fecha"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
