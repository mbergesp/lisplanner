package com.lisplanner.app.ui.gantt

import com.lisplanner.app.data.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Task
import com.lisplanner.app.data.TaskStatus
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.components.hoursLabel
import com.lisplanner.app.ui.components.SlimProgress
import com.lisplanner.app.ui.theme.groupColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

enum class GanttZoom(val label: String, val dayWidth: Dp) {
    DIA("Día", 32.dp),
    SEMANA("Semana", 9.dp),
    MES("Mes", 3.dp),
    // Ajusta todo el proyecto al ancho disponible (útil en planes de varios años).
    TODO("Todo", 0.dp),
}

private sealed interface GanttRow {
    data class GroupRow(
        val key: String,
        val label: String,
        val colorIndex: Int,
        val start: LocalDate,
        val end: LocalDate,
        val progress: Float,
        val collapsed: Boolean,
        val hours: Double,
        val spent: Double,
    ) : GanttRow
    data class TaskRow(val task: Task, val colorIndex: Int) : GanttRow
}

private val RowHeight = 46.dp
private val HeaderHeight = 54.dp
private val HoursColumn = 84.dp

private fun buildRows(project: Project, collapsed: Set<String>): List<GanttRow> =
    project.groups().flatMap { g ->
        val sorted = g.tasks // orden manual del proyecto
        val header = GanttRow.GroupRow(
            key = g.key, label = g.short, colorIndex = g.colorIndex,
            start = g.start, end = g.end, progress = g.progress,
            collapsed = g.key in collapsed,
            hours = g.estimatedHours, spent = g.spentHours,
        )
        if (g.key in collapsed) listOf(header) else listOf(header) + sorted.map { GanttRow.TaskRow(it, g.colorIndex) }
    }

/**
 * Diagrama de Gantt agrupado por fases de implantación.
 * Cabecera de fechas fija, columna de tareas fija y línea temporal con desplazamiento en ambos ejes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GanttChart(
    project: Project,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by rememberSaveable { mutableStateOf(GanttZoom.SEMANA) }
    var collapsed by remember(project.id) { mutableStateOf(emptySet<String>()) }
    val today = remember { today() }
    val rows = remember(project.tasks, collapsed) { buildRows(project, collapsed) }

    val from = remember(project) {
        (project.tasks.map { it.start } + project.startDate).min().minusDays(3)
            .previousOrSame(DayOfWeek.MONDAY)
    }
    val to = remember(project) { (project.tasks.map { it.end } + project.goLiveDate).max().plusDays(14) }
    val days = daysBetween(from, to).toInt() + 1

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val showLegend = maxWidth >= 760.dp
        val labelWidth = if (wide) 240.dp else 150.dp
        val timelineWidth = maxWidth - labelWidth - (if (wide) HoursColumn else 0.dp)
        // Compose no admite lienzos de más de ~260.000 px: en proyectos muy largos se estrecha el día.
        val dayWidth = if (zoom == GanttZoom.TODO) timelineWidth / days else minOf(zoom.dayWidth, with(density) { (240_000f / days).toDp() })
        val viewportPx = with(density) { (maxWidth - labelWidth).toPx() }
        val dayPx = with(density) { dayWidth.toPx() }
        val todayIdx = daysBetween(from, today).toInt()

        fun scrollToToday(scope: CoroutineScope, animate: Boolean) = scope.launch {
            snapshotFlow { hScroll.maxValue }.first { it > 0 }
            val target = (todayIdx * dayPx - viewportPx * 0.3f).toInt().coerceIn(0, hScroll.maxValue)
            if (animate) hScroll.animateScrollTo(target) else hScroll.scrollTo(target)
        }
        LaunchedEffect(zoom, project.id) { scrollToToday(this, animate = false) }
        // Al abrir, bajar hasta la primera tarea aún abierta para que el Gantt muestre barras en curso.
        LaunchedEffect(project.id) {
            val idx = rows.indexOfFirst { it is GanttRow.TaskRow && it.task.progress < 100 && !it.task.end.isBefore(today.minusDays(14)) }
            if (idx > 1) {
                snapshotFlow { vScroll.maxValue }.first { it > 0 }
                vScroll.scrollTo(with(density) { ((idx - 1) * RowHeight.toPx()).toInt() })
            }
        }

        Column(Modifier.fillMaxSize()) {
            // ---- Barra de herramientas ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 300.dp).weight(1f, fill = false)) {
                    GanttZoom.entries.forEachIndexed { i, z ->
                        SegmentedButton(
                            selected = zoom == z,
                            onClick = { zoom = z },
                            shape = SegmentedButtonDefaults.itemShape(i, GanttZoom.entries.size),
                            label = { Text(z.label, maxLines = 1) },
                            icon = {},
                        )
                    }
                }
                FilledTonalIconButton(onClick = { scrollToToday(scope, animate = true) }) {
                    Icon(Icons.Outlined.Today, "Ir a hoy")
                }
                val allKeys = project.groups().map { it.key }.toSet()
                val allCollapsed = collapsed.containsAll(allKeys)
                IconButton(onClick = { collapsed = if (allCollapsed) emptySet() else allKeys }) {
                    Icon(if (allCollapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, "Expandir o contraer fases")
                }
                if (showLegend) Legend(Modifier.weight(1f).padding(start = 8.dp))
            }
            EffortSummary(project, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Cabecera ----
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                Box(Modifier.width(labelWidth).height(HeaderHeight).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Fase / tarea", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (wide) {
                    Box(Modifier.width(HoursColumn).height(HeaderHeight), contentAlignment = Alignment.Center) {
                        Text("Horas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TimelineHeader(from, days, zoom, dayWidth, today, hScroll, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Cuerpo ----
            Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(vScroll)) {
                Column(Modifier.width(labelWidth)) {
                    rows.forEach { row ->
                        when (row) {
                            is GanttRow.GroupRow -> PhaseLabel(row, showHours = !wide) {
                                collapsed = if (row.key in collapsed) collapsed - row.key else collapsed + row.key
                            }
                            is GanttRow.TaskRow -> TaskLabel(row.task, showHours = !wide) { onTaskClick(row.task) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                if (wide) {
                    Column(Modifier.width(HoursColumn).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))) {
                        rows.forEach { row -> HoursCell(row) }
                    }
                }
                Box(Modifier.weight(1f).clipToBounds().horizontalScroll(hScroll)) {
                    TimelineBody(
                        rows = rows, from = from, days = days, zoom = zoom, dayWidth = dayWidth, today = today, goLive = project.goLiveDate,
                        onRowTap = { row ->
                            when (row) {
                                is GanttRow.GroupRow -> collapsed = if (row.key in collapsed) collapsed - row.key else collapsed + row.key
                                is GanttRow.TaskRow -> onTaskClick(row.task)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseLabel(row: GanttRow.GroupRow, showHours: Boolean, onClick: () -> Unit) {
    val color = groupColor(row.colorIndex)
    Row(
        Modifier.fillMaxWidth().height(RowHeight).background(color.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ExpandMore, null,
            Modifier.size(18.dp).rotate(if (row.collapsed) -90f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (showHours && row.hours > 0) "${(row.progress * 100).toInt()}% · ${hoursLabel(row.hours)}" else "${(row.progress * 100).toInt()}% completado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun TaskLabel(task: Task, showHours: Boolean, onClick: () -> Unit) {
    val overdue = task.status() == TaskStatus.ATRASADA
    Column(
        Modifier.fillMaxWidth().height(RowHeight).clickable(onClick = onClick).padding(start = 30.dp, end = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            (if (task.isMilestone) "◆ " else "") + task.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (task.isMilestone) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            (if (task.autoSchedule) "⏱ " else "") +
                (if (showHours && task.estimatedHours > 0) "${hoursLabel(task.estimatedHours)} · " else "") +
                if (task.isMilestone) task.start.short() else "${task.start.short()} → ${task.end.short()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimelineHeader(from: LocalDate, days: Int, zoom: GanttZoom, dayWidth: Dp, today: LocalDate, hScroll: ScrollState, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.tertiary
    val onToday = MaterialTheme.colorScheme.onTertiary
    val monthFmt = remember(zoom) { EsDateFormat(if (zoom == GanttZoom.MES || zoom == GanttZoom.TODO) "MMM yy" else "MMMM yyyy") }
    val labelStyle = TextStyle(fontSize = 11.sp, color = onVariant)
    val monthStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onSurface)

    Box(modifier.horizontalScroll(hScroll)) {
        Canvas(Modifier.width(dayWidth * days).height(HeaderHeight)) {
            val dw = dayWidth.toPx()
            val mid = size.height / 2
            // Si los meses no caben, la cabecera muestra años y marcas de mes.
            val yearMode = 30 * dw < 56.dp.toPx()
            for (d in 0 until days) {
                val date = from.plusDays(d.toLong())
                val x = d * dw
                if (yearMode) {
                    if (date.day == 1) drawLine(grid, Offset(x, size.height - 8.dp.toPx()), Offset(x, size.height), 1f)
                    if (date.dayOfYear == 1 || d == 0) {
                        if (date.dayOfYear == 1) drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        val label = measurer.measure(date.year.toString(), monthStyle)
                        val yearEnd = daysBetween(from, date.withDayOfYear(1).plusYears(1)) * dw
                        val pad = 4.dp.toPx()
                        val lx = maxOf(x + pad, hScroll.value + pad).coerceAtMost(yearEnd - label.size.width - pad)
                        if (lx >= x) drawText(label, topLeft = Offset(lx, pad))
                    }
                } else if (date.day == 1 || d == 0) {
                    if (date.day == 1) drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    // Etiqueta "pegajosa": se queda en el borde izquierdo visible mientras su mes esté en pantalla.
                    val label = measurer.measure(monthFmt.format(date).replaceFirstChar { it.uppercase() }, monthStyle)
                    val monthEnd = daysBetween(from, date.withDayOfMonth(1).plusMonths(1)) * dw
                    val pad = 6.dp.toPx()
                    val lx = maxOf(x + pad, hScroll.value + pad).coerceAtMost(monthEnd - label.size.width - pad)
                    if (lx >= x) drawText(label, topLeft = Offset(lx, pad))
                }
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                when (zoom) {
                    GanttZoom.DIA -> {
                        val t = measurer.measure(date.day.toString(), labelStyle.copy(color = if (weekend) onVariant.copy(alpha = 0.5f) else onVariant))
                        drawText(t, topLeft = Offset(x + (dw - t.size.width) / 2, mid + 4.dp.toPx()))
                    }
                    GanttZoom.SEMANA -> if (date.dayOfWeek == DayOfWeek.MONDAY) {
                        drawLine(grid, Offset(x, mid + 4.dp.toPx()), Offset(x, size.height), 1f)
                        drawText(measurer, date.day.toString(), Offset(x + 3.dp.toPx(), mid + 4.dp.toPx()), labelStyle)
                    }
                    GanttZoom.MES -> if (date.dayOfWeek == DayOfWeek.MONDAY) {
                        drawLine(grid, Offset(x, size.height - 6.dp.toPx()), Offset(x, size.height), 1f)
                    }
                    GanttZoom.TODO -> Unit
                }
            }
            // Marca de hoy
            val ti = daysBetween(from, today)
            if (ti in 0 until days) {
                val cx = ti * dw + dw / 2
                val t = measurer.measure("Hoy", TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = onToday))
                val pw = t.size.width + 10.dp.toPx()
                val ph = t.size.height + 2.dp.toPx()
                val top = size.height - ph - 2.dp.toPx()
                drawRoundRect(todayColor, Offset(cx - pw / 2, top), Size(pw, ph), CornerRadius(ph / 2))
                drawText(t, topLeft = Offset(cx - t.size.width / 2, top + 1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TimelineBody(
    rows: List<GanttRow>,
    from: LocalDate,
    days: Int,
    zoom: GanttZoom,
    dayWidth: Dp,
    today: LocalDate,
    goLive: LocalDate,
    onRowTap: (GanttRow) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val palette = (0 until 10).map { groupColor(it) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val weekendShade = MaterialTheme.colorScheme.surfaceContainer
    val error = MaterialTheme.colorScheme.error
    val todayColor = MaterialTheme.colorScheme.tertiary
    val goLiveColor = MaterialTheme.colorScheme.primary
    val onBar = Color.White
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val rowOf = remember(rows) {
        rows.withIndex().mapNotNull { (i, r) -> (r as? GanttRow.TaskRow)?.let { it.task.id to (i to it.task) } }.toMap()
    }

    Canvas(
        Modifier
            .width(dayWidth * days)
            .height(RowHeight * rows.size + 24.dp)
            .pointerInput(rows) {
                detectTapGestures { pos ->
                    rows.getOrNull((pos.y / RowHeight.toPx()).toInt())?.let(onRowTap)
                }
            },
    ) {
        val dw = dayWidth.toPx()
        val rh = RowHeight.toPx()
        fun xOf(date: LocalDate) = daysBetween(from, date) * dw

        // Fondo: fines de semana y rejilla
        for (d in 0 until days) {
            val date = from.plusDays(d.toLong())
            val x = d * dw
            if ((zoom == GanttZoom.DIA || zoom == GanttZoom.SEMANA) && (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)) {
                drawRect(weekendShade, Offset(x, 0f), Size(dw, size.height))
            }
            val yearGrid = 30 * dw < 56.dp.toPx()
            when {
                yearGrid -> if (date.dayOfYear == 1) drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                date.day == 1 -> drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                date.dayOfWeek == DayOfWeek.MONDAY && (zoom == GanttZoom.DIA || zoom == GanttZoom.SEMANA) -> drawLine(grid.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), 1f)
            }
        }

        rows.forEachIndexed { i, row ->
            val top = i * rh
            when (row) {
                is GanttRow.GroupRow -> {
                    val c = palette[row.colorIndex % palette.size]
                    drawRect(c.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, rh))
                    drawPhaseBar(xOf(row.start), xOf(row.end) + dw, top + rh / 2, row.progress, c)
                }
                is GanttRow.TaskRow -> {
                    val t = row.task
                    val c = palette[row.colorIndex % palette.size]
                    if (t.isMilestone) {
                        drawMilestone(xOf(t.start) + dw / 2, top + rh / 2, c, done = t.progress >= 100, surface = surface)
                    } else {
                        val x0 = xOf(t.start)
                        val w = (xOf(t.end) + dw - x0).coerceAtLeast(4.dp.toPx())
                        val bh = 22.dp.toPx()
                        val by = top + (rh - bh) / 2
                        val r = CornerRadius(bh / 2.6f)
                        drawRoundRect(c.copy(alpha = 0.28f), Offset(x0, by), Size(w, bh), r)
                        if (t.progress > 0) drawRoundRect(c, Offset(x0, by), Size(w * t.progress / 100f, bh), r)
                        if (t.status(today) == TaskStatus.ATRASADA) {
                            drawRoundRect(error, Offset(x0, by), Size(w, bh), r, style = Stroke(2.dp.toPx()))
                        }
                        val label = measurer.measure("${t.progress}%", TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = onBar))
                        if (w > label.size.width + 10.dp.toPx() && t.progress > 0 && w * t.progress / 100f > label.size.width + 8.dp.toPx()) {
                            drawText(label, topLeft = Offset(x0 + 6.dp.toPx(), by + (bh - label.size.height) / 2))
                        }
                    }
                }
            }
            drawLine(grid.copy(alpha = 0.35f), Offset(0f, top + rh), Offset(size.width, top + rh), 1f)
        }

        // Dependencias fin → inicio
        rows.forEach { row ->
            val succ = (row as? GanttRow.TaskRow)?.task ?: return@forEach
            succ.dependsOn.forEach { predId ->
                val (pi, pred) = rowOf[predId] ?: return@forEach
                val si = rowOf.getValue(succ.id).first
                val conflict = succ.start.isBefore(pred.end)
                drawDependency(
                    fromX = if (pred.isMilestone) xOf(pred.start) + dw / 2 + 9.dp.toPx() else xOf(pred.end) + dw,
                    fromY = pi * rh + rh / 2,
                    toX = if (succ.isMilestone) xOf(succ.start) + dw / 2 - 9.dp.toPx() else xOf(succ.start),
                    toY = si * rh + rh / 2,
                    rowHeight = rh,
                    color = if (conflict) error else arrowColor,
                )
            }
        }

        // Go-Live y hoy
        val gx = xOf(goLive) + dw / 2
        drawLine(goLiveColor, Offset(gx, 0f), Offset(gx, size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        val tx = xOf(today) + dw / 2
        drawLine(todayColor, Offset(tx, 0f), Offset(tx, size.height), 2.dp.toPx())
    }
}

/** Conector en ángulo recto con punta de flecha; si no hay hueco, rodea por el borde de la fila. */
private fun DrawScope.drawDependency(fromX: Float, fromY: Float, toX: Float, toY: Float, rowHeight: Float, color: Color) {
    val gap = 6.dp.toPx()
    val head = 5.dp.toPx()
    val path = Path().apply {
        moveTo(fromX, fromY)
        if (toX - fromX >= gap * 2) {
            lineTo(fromX + gap, fromY)
            lineTo(fromX + gap, toY)
        } else {
            val edgeY = if (toY > fromY) toY - rowHeight / 2 else toY + rowHeight / 2
            lineTo(fromX + gap, fromY)
            lineTo(fromX + gap, edgeY)
            lineTo(toX - gap * 2, edgeY)
            lineTo(toX - gap * 2, toY)
        }
        lineTo(toX - head, toY)
    }
    drawPath(path, color, style = Stroke(1.5.dp.toPx()))
    val tip = Path().apply { moveTo(toX, toY); lineTo(toX - head, toY - head * 0.75f); lineTo(toX - head, toY + head * 0.75f); close() }
    drawPath(tip, color)
}

private fun DrawScope.drawPhaseBar(x0: Float, x1: Float, cy: Float, progress: Float, color: Color) {
    val h = 8.dp.toPx()
    val top = cy - h / 2
    drawRect(color.copy(alpha = 0.35f), Offset(x0, top), Size(x1 - x0, h))
    drawRect(color, Offset(x0, top), Size((x1 - x0) * progress, h))
    // Remates en forma de corchete, típicos de una barra resumen (si la barra tiene sitio)
    val tip = 6.dp.toPx()
    if (x1 - x0 < tip * 3) return
    listOf(x0, x1).forEach { x ->
        val dir = if (x == x0) 1 else -1
        val p = Path().apply {
            moveTo(x, top); lineTo(x, top + h + tip); lineTo(x + dir * tip, top + h); close()
        }
        drawPath(p, color)
    }
}

private fun DrawScope.drawMilestone(cx: Float, cy: Float, color: Color, done: Boolean, surface: Color) {
    val r = 9.dp.toPx()
    val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
    drawPath(p, if (done) color else surface)
    drawPath(p, color, style = Stroke(2.5.dp.toPx()))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        LegendItem({ Box(Modifier.size(18.dp, 8.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.secondary)) }, "Avance", style, muted)
        LegendItem({ Text("◆", color = MaterialTheme.colorScheme.secondary) }, "Hito", style, muted)
        LegendItem({ Box(Modifier.size(18.dp, 8.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.error)) }, "Atrasada", style, muted)
        LegendItem({ Box(Modifier.size(3.dp, 14.dp).background(MaterialTheme.colorScheme.tertiary)) }, "Hoy", style, muted)
        LegendItem({ Box(Modifier.size(3.dp, 14.dp).background(MaterialTheme.colorScheme.primary)) }, "Go-Live", style, muted)
        LegendItem({ Text("→", color = muted) }, "Dependencia", style, muted)
    }
}

@Composable
private fun LegendItem(swatch: @Composable () -> Unit, label: String, style: TextStyle, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(label, style = style, color = color)
    }
}

@Composable
private fun HoursCell(row: GanttRow) {
    val (est, spent, bold) = when (row) {
        is GanttRow.GroupRow -> Triple(row.hours, row.spent, true)
        is GanttRow.TaskRow -> Triple(row.task.estimatedHours, row.task.spentHours, false)
    }
    val over = spent > est && est > 0
    Column(
        Modifier.fillMaxWidth().height(RowHeight)
            .then(if (row is GanttRow.GroupRow) Modifier.background(groupColor(row.colorIndex).copy(alpha = 0.08f)) else Modifier)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End,
    ) {
        if (est > 0 || spent > 0) {
            Text(hoursLabel(est), style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(
                "${hoursLabel(spent)} imp.",
                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Esfuerzo total del proyecto: estimado, imputado y pendiente según el avance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortSummary(project: Project, modifier: Modifier = Modifier) {
    val est = project.estimatedHours
    val spent = project.spentHours
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (est <= 0) {
            Text(
                "Añade horas estimadas a las tareas para ver el esfuerzo total del proyecto.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
            return@Surface
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.Top,
            ) {
                EffortStat("Total proyecto", hoursLabel(est), strong = true)
                EffortStat("Imputadas", "${hoursLabel(spent)} (${(spent / est * 100).toInt()}%)", alert = spent > est)
                EffortStat("Pendientes", hoursLabel(project.remainingHours))
                EffortStat("Jornadas (8 h)", formatNumberEs(est / 8, 0))
                project.calculatedEnd?.let { end ->
                    EffortStat("Fin calculado", end.short(), alert = project.goLiveDeviationDays > 0)
                }
            }
            Spacer(Modifier.height(8.dp))
            SlimProgress((spent / est).toFloat(), if (spent > est) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EffortStat(label: String, value: String, strong: Boolean = false, alert: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
