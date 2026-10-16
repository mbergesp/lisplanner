package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.LisPhase
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Task
import com.lisplanner.app.ui.components.InitialsAvatar
import com.lisplanner.app.ui.components.LabeledValue
import com.lisplanner.app.ui.components.SectionCard
import com.lisplanner.app.ui.components.SlimProgress
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.relativeDays
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.components.hoursLabel
import com.lisplanner.app.ui.theme.phaseColor
import com.lisplanner.app.ui.theme.groupColor
import kotlinx.datetime.LocalDate

@Composable
fun OverviewTab(
    project: Project,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onTaskClick: (Task) -> Unit,
    onManagePhases: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusDashboard(project)
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PhasesCard(project, onManagePhases)
                        EffortCard(project)
                        WorkloadCard(project)
                        MilestonesCard(project, onTaskClick)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoCard(project)
                        TeamCard(project, onAddMember, onRemoveMember)
                    }
                }
            } else {
                PhasesCard(project, onManagePhases)
                EffortCard(project)
                WorkloadCard(project)
                MilestonesCard(project, onTaskClick)
                InfoCard(project)
                TeamCard(project, onAddMember, onRemoveMember)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhasesCard(project: Project, onManage: () -> Unit) {
    SectionCard(
        "Detalle por fase", icon = Icons.Outlined.Timeline,
        action = { IconButton(onClick = onManage) { Icon(Icons.Outlined.Edit, "Gestionar fases", tint = MaterialTheme.colorScheme.primary) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            project.groups().forEach { g ->
                val progress = g.progress
                val tasks = g.tasks
                val color = groupColor(g.colorIndex)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(g.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${tasks.minOf { it.start }.short()} – ${tasks.maxOf { it.end }.short()}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    SlimProgress(progress, color, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MilestonesCard(project: Project, onTaskClick: (Task) -> Unit) {
    val today = remember { today() }
    val milestones = project.tasks.filter { it.isMilestone }.sortedBy { it.start }
    SectionCard("Hitos", icon = Icons.Outlined.Flag) {
        if (milestones.isEmpty()) {
            Text("No hay hitos definidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        milestones.forEachIndexed { i, m ->
            val done = m.progress >= 100
            val late = !done && m.start.isBefore(today)
            val color = groupColor(project.colorIndexOf(m))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTaskClick(m) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Línea de tiempo vertical con rombo
                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(14.dp).graphicsLayer { rotationZ = 45f }.clip(RoundedCornerShape(2.dp))
                            .background(if (done) color else color.copy(alpha = 0.25f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${m.start.long()} · ${relativeDays(daysBetween(today, m.start))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when { done -> "Cumplido"; late -> "Vencido"; else -> "Pendiente" },
                    style = MaterialTheme.typography.labelMedium,
                    color = when { done -> MaterialTheme.colorScheme.secondary; late -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                )
            }
            if (i < milestones.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun InfoCard(project: Project) {
    SectionCard("Datos del proyecto", icon = Icons.Outlined.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { LabeledValue("Cliente", project.client, Modifier.weight(1f)); LabeledValue("Áreas", project.site, Modifier.weight(1f)) }
            Row { LabeledValue("Sistema LIS", project.lisSystem, Modifier.weight(1f)); LabeledValue("Jefe de proyecto", project.projectManager, Modifier.weight(1f)) }
            Row { LabeledValue("Inicio", project.startDate.long(), Modifier.weight(1f)); LabeledValue("Go-Live", project.goLiveDate.long(), Modifier.weight(1f)) }
            if (project.description.isNotBlank()) LabeledValue("Alcance", project.description)
        }
    }
}

@Composable
private fun TeamCard(project: Project, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    SectionCard(
        "Equipo (${project.team.size})",
        icon = Icons.Outlined.Groups,
        action = { IconButton(onClick = onAdd) { Icon(Icons.Outlined.PersonAdd, "Añadir miembro", tint = MaterialTheme.colorScheme.primary) } },
    ) {
        if (project.team.isEmpty()) Text("Añade a las personas del proyecto para asignarles tareas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        project.team.forEach { m ->
            val load = project.tasks.count { it.assignee == m.name && it.progress < 100 }
            val pending = project.tasks.filter { it.assignee == m.name }.sumOf { it.estimatedHours * (100 - it.progress) / 100.0 }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(m.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyMedium)
                    Text("${m.role} · $load tareas abiertas" + if (pending > 0) " · ${hoursLabel(pending)} pendientes" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRemove(m.id) }) { Icon(Icons.Outlined.Close, "Quitar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Horas estimadas frente a imputadas, en total y por fase. */
@Composable
private fun EffortCard(project: Project) {
    val est = project.estimatedHours
    SectionCard("Esfuerzo (horas)", icon = Icons.Outlined.Schedule) {
        if (est <= 0) {
            Text("Todavía no hay horas estimadas. Añádelas desde el editor de cada tarea.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val spent = project.spentHours
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue("Estimadas", hoursLabel(est), Modifier.weight(1f))
            LabeledValue("Imputadas", hoursLabel(spent), Modifier.weight(1f))
            LabeledValue("Pendientes", hoursLabel(project.remainingHours), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            project.groups().forEach { g ->
                val pe = g.estimatedHours.takeIf { it > 0 } ?: return@forEach
                val ps = g.spentHours
                val color = groupColor(g.colorIndex)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(g.short, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${hoursLabel(ps)} / ${hoursLabel(pe)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ps > pe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SlimProgress((ps / pe).toFloat(), if (ps > pe) MaterialTheme.colorScheme.error else color, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Quién tiene más horas asignadas en este proyecto. */
@Composable
private fun WorkloadCard(project: Project) {
    val loads = remember(project.tasks) { com.lisplanner.app.data.Workload.compute(listOf(project)) }
    SectionCard("Carga de trabajo por persona", icon = Icons.Outlined.Groups) {
        com.lisplanner.app.ui.components.WorkloadChart(loads, Modifier.fillMaxWidth())
    }
}
