package com.lisplanner.app.ui.dashboard

import com.lisplanner.app.data.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lisplanner.app.data.IntegrationStatus
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.data.Severity
import com.lisplanner.app.data.Task
import com.lisplanner.app.ui.components.SectionCard
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.theme.KpiNumberStyle
import com.lisplanner.app.ui.theme.colors
import com.lisplanner.app.ui.theme.phaseColor
import com.lisplanner.app.ui.theme.groupColor
import kotlinx.datetime.LocalDate

@Composable
fun DashboardScreen(
    projects: List<Project>,
    onOpenProject: (String) -> Unit,
    onResetSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { today() }
    val active = projects.filter { it.status != ProjectStatus.COMPLETADO }
    val nextGoLive = active.filter { !it.goLiveDate.isBefore(today) }.minByOrNull { it.goLiveDate }
    val overdue = active.flatMap { p -> p.overdueTasks(today).map { p to it } }.sortedBy { it.second.end }
    val integrations = active.flatMap { it.integrations }
    val validated = integrations.count { it.status == IntegrationStatus.VALIDADO }
    val blocked = active.flatMap { p -> p.integrations.filter { it.status == IntegrationStatus.BLOQUEADO }.map { p to it } }
    val hotRisks = active.flatMap { p -> p.openRisks().filter { it.severity >= Severity.ALTA }.map { p to it } }.sortedByDescending { it.second.score }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header(today, onResetSample) }

            item { Kpi(Icons.Outlined.Science, "Proyectos activos", "${active.size}", "${projects.size - active.size} completados", MaterialTheme.colorScheme.primary) }
            item {
                Kpi(
                    Icons.Outlined.RocketLaunch, "Próximo go-live",
                    nextGoLive?.let { "${daysBetween(today, it.goLiveDate)} d" } ?: "—",
                    nextGoLive?.client ?: "Sin arranques previstos", MaterialTheme.colorScheme.secondary,
                    onClick = nextGoLive?.let { { onOpenProject(it.id) } },
                )
            }
            item { Kpi(Icons.Outlined.WarningAmber, "Tareas atrasadas", "${overdue.size}", if (overdue.isEmpty()) "Todo al día" else "en ${overdue.map { it.first.id }.distinct().size} proyectos", MaterialTheme.colorScheme.error) }
            item { Kpi(Icons.Outlined.Cable, "Integraciones validadas", if (integrations.isEmpty()) "—" else "${validated * 100 / integrations.size}%", "$validated de ${integrations.size}", MaterialTheme.colorScheme.tertiary) }

            item(span = { halfOrFull() }) {
                SectionCard("Cartera de proyectos", icon = Icons.Outlined.ViewTimeline) {
                    PortfolioTimeline(projects.sortedBy { it.startDate }, today, onOpenProject)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val loads = remember(active) { com.lisplanner.app.data.Workload.compute(active) }
                SectionCard("Carga del equipo · proyectos activos", icon = Icons.Outlined.Groups) {
                    com.lisplanner.app.ui.components.WorkloadChart(loads, Modifier.fillMaxWidth(), showProjects = true)
                }
            }
            item(span = { halfOrFull() }) {
                SectionCard("Requiere atención", icon = Icons.Outlined.NotificationImportant) {
                    if (overdue.isEmpty() && blocked.isEmpty() && hotRisks.isEmpty()) {
                        Text("No hay tareas atrasadas, integraciones bloqueadas ni riesgos altos. 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    blocked.forEach { (p, i) ->
                        AttentionRow(
                            title = i.name, subtitle = "${p.client} · Integración bloqueada",
                            tag = "Bloqueada", tagColors = i.status.colors(), color = MaterialTheme.colorScheme.error,
                            onClick = { onOpenProject(p.id) },
                        )
                    }
                    hotRisks.forEach { (p, r) ->
                        AttentionRow(
                            title = r.title, subtitle = "${p.client} · ${r.kind.label} ${r.severity.label.lowercase()}",
                            tag = "${r.kind.label} · ${r.score}", tagColors = r.severity.colors(), color = MaterialTheme.colorScheme.tertiary,
                            onClick = { onOpenProject(p.id) },
                        )
                    }
                    overdue.take(8).forEach { (p, t) -> OverdueRow(p, t, today) { onOpenProject(p.id) } }
                    if (overdue.size > 8) Text("y ${overdue.size - 8} más…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** En pantallas anchas (Fold desplegado) las tarjetas grandes se colocan de dos en dos. */
private fun LazyGridItemSpanScope.halfOrFull() = GridItemSpan(if (maxLineSpan >= 4) maxLineSpan / 2 else maxLineSpan)

@Composable
private fun Header(today: LocalDate, onResetSample: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                today.format(EsDateFormat("EEEE, d 'de' MMMM")).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            Text("Proyectos LIS", style = MaterialTheme.typography.headlineMedium)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Restaurar datos de ejemplo") }, onClick = { menu = false; onResetSample() })
            }
        }
    }
}

@Composable
private fun Kpi(icon: ImageVector, label: String, value: String, caption: String, accent: Color, onClick: (() -> Unit)? = null) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = KpiNumberStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Mini-Gantt de cartera: una barra por proyecto (inicio → go-live) sobre una escala común. */
@Composable
private fun PortfolioTimeline(projects: List<Project>, today: LocalDate, onOpen: (String) -> Unit) {
    if (projects.isEmpty()) {
        Text("Sin proyectos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val from = projects.minOf { it.startDate }.withDayOfMonth(1)
    val to = projects.maxOf { it.endDate }.plusMonths(1).withDayOfMonth(1)
    val total = daysBetween(from, to).toFloat()
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val monthFmt = remember(projects) {
        val span = monthsBetween(projects.minOf { it.startDate }, projects.maxOf { it.endDate })
        EsDateFormat(if (span > 36) "MMM yy" else "MMM")
    }

    // Eje de meses
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        var m = from
        val months = monthsBetween(from, to)
        val step = when { months > 60 -> 12L; months > 36 -> 6L; months > 18 -> 3L; months > 9 -> 2L; else -> 1L }
        while (m.isBefore(to)) {
            val x = daysBetween(from, m) / total * size.width
            drawLine(gridColor, Offset(x, size.height - 4.dp.toPx()), Offset(x, size.height), 1f)
            if (x < size.width - 24.dp.toPx()) {
                drawText(measurer, monthFmt.format(m).replace(".", ""), Offset(x + 2.dp.toPx(), 0f), TextStyle(fontSize = 10.sp, color = axisColor))
            }
            m = m.plusMonths(step)
        }
    }
    Spacer(Modifier.height(6.dp))
    projects.forEach { p ->
        val c = p.status.colors().content
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(p.id) }.padding(vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.client, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(p.progress * 100).toInt()}% · ${p.goLiveDate.short()}", style = MaterialTheme.typography.labelMedium, color = axisColor)
            }
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                fun x(d: LocalDate) = daysBetween(from, d) / total * size.width
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, size = size, cornerRadius = r)
                val x0 = x(p.startDate)
                val w = (x(p.goLiveDate) - x0).coerceAtLeast(4f)
                drawRoundRect(c.copy(alpha = 0.3f), Offset(x0, 0f), Size(w, size.height), r)
                drawRoundRect(c, Offset(x0, 0f), Size(w * p.progress, size.height), r)
                val tx = x(today)
                drawLine(todayColor, Offset(tx, -4f), Offset(tx, size.height + 4f), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun OverdueRow(project: Project, task: Task, today: LocalDate, onClick: () -> Unit) {
    AttentionRow(
        title = task.name,
        subtitle = "${project.client} · venció ${task.end.long()}",
        tag = "${daysBetween(task.end, today)} d",
        tagColors = com.lisplanner.app.data.TaskStatus.ATRASADA.colors(),
        color = groupColor(project.colorIndexOf(task)),
        onClick = onClick,
    )
}

@Composable
private fun AttentionRow(title: String, subtitle: String, tag: String, tagColors: com.lisplanner.app.ui.theme.StatusColors, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(tag, tagColors)
    }
}
