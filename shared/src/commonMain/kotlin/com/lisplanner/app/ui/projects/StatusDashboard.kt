package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ScheduleHealth
import com.lisplanner.app.ui.components.SectionCard
import kotlinx.datetime.LocalDate
import kotlin.math.cos
import kotlin.math.sin

private val Red = Color(0xFFE53935)
private val Yellow = Color(0xFFFDD835)
private val Green = Color(0xFF43A047)

private fun ScheduleHealth.color() = when (this) {
    ScheduleHealth.RETRASADO -> Red
    ScheduleHealth.LIGERO_RETRASO -> Yellow
    ScheduleHealth.EN_PLAZO -> Green
}

/**
 * Cuadro de mando del proyecto: velocímetro de estado general, reparto de actividades por estado
 * y un anillo de avance por cada fase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusDashboard(project: Project, modifier: Modifier = Modifier) {
    val today = remember { today() }
    SectionCard("Estado del proyecto", icon = Icons.Outlined.Speed, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val w = maxWidth
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        GeneralGauge(project, today, Modifier.weight(1f))
                        ActivitiesBar(project, Modifier.weight(1.4f))
                    }
                } else {
                    GeneralGauge(project, today, Modifier.fillMaxWidth())
                    ActivitiesBar(project, Modifier.fillMaxWidth())
                }
                PhaseDonuts(project, columns = if (w >= 840.dp) 6 else if (w >= 480.dp) 4 else 3, width = w)
            }
        }
    }
}

@Composable
private fun GeneralGauge(project: Project, today: LocalDate, modifier: Modifier) {
    // La aguja marca el avance real: 0 % al inicio de la zona roja, 100 % al final de la verde.
    val value by animateFloatAsState(project.progress.coerceIn(0f, 1f), tween(900), label = "gauge")
    val h = project.scheduleHealth(today)
    val needle = MaterialTheme.colorScheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Estado general", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(2f)) {
            val stroke = size.width * 0.075f
            val radius = size.width / 2 - stroke
            val center = Offset(size.width / 2, size.height - stroke / 2)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            // 10 segmentos: 4 rojos, 3 amarillos, 3 verdes, con pequeños huecos.
            val segments = 10
            val sweep = 180f / segments
            for (i in 0 until segments) {
                val c = when { i < 4 -> Red; i < 7 -> Yellow; else -> Green }
                drawArc(c, 180f + i * sweep + 1.5f, sweep - 3f, false, topLeft, arcSize, style = Stroke(stroke))
            }
            // Aguja
            val angle = ((180f + 180f * value).toDouble() * kotlin.math.PI / 180.0)
            val tip = Offset(center.x + (radius - stroke) * cos(angle).toFloat(), center.y + (radius - stroke) * sin(angle).toFloat())
            drawLine(needle, center, tip, strokeWidth = stroke * 0.35f, cap = StrokeCap.Round)
            drawOval(needle, topLeft = Offset(center.x - stroke * 1.1f, center.y - stroke * 0.75f), size = Size(stroke * 2.2f, stroke * 1.5f))
        }
        Spacer(Modifier.height(6.dp))
        Text("${(project.progress * 100).toInt()}% completado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(h.color()))
            Spacer(Modifier.width(6.dp))
            Text(
                "${h.label} · previsto hoy ${(project.expectedProgress(today) * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitiesBar(project: Project, modifier: Modifier) {
    val total = project.tasks.size.coerceAtLeast(1)
    val notStarted = project.tasks.count { it.progress == 0 }
    val inProgress = project.tasks.count { it.progress in 1..99 }
    val done = project.tasks.count { it.progress >= 100 }
    val parts = listOf(Triple("No iniciado", notStarted, Red), Triple("En proceso", inProgress, Yellow), Triple("Completado", done, Green))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Avance de actividades", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))) {
            parts.filter { it.second > 0 }.forEach { (_, n, c) ->
                Box(Modifier.weight(n.toFloat()).fillMaxSize().background(c), contentAlignment = Alignment.Center) {
                    val pct = kotlin.math.round(n * 100f / total).toInt()
                    if (n * 100 / total >= 7) {
                        Text("$pct%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach { (label, n, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(c))
                    Spacer(Modifier.width(6.dp))
                    Text("$label ($n)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseDonuts(project: Project, columns: Int, width: Dp) {
    val phases = project.phases
    if (phases.isEmpty()) return
    val gap = 12.dp
    // -1 dp: margen para que el redondeo no empuje el último anillo a otra fila.
    val cell = (width - gap * (columns - 1)) / columns - 1.dp
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = columns) {
        phases.forEach { p ->
            val tasks = project.tasks.filter { it.phaseId == p.id }
            val work = tasks.filterNot { it.isMilestone }
            val total = work.sumOf { it.durationDays }
            val progress = when {
                tasks.isEmpty() -> 0f
                total == 0L -> if (tasks.all { it.progress >= 100 }) 1f else 0f
                else -> work.sumOf { it.durationDays * it.progress } / (total * 100f)
            }
            Donut(p.name, progress, empty = tasks.isEmpty(), modifier = Modifier.width(cell))
        }
    }
}

@Composable
private fun Donut(label: String, progress: Float, empty: Boolean, modifier: Modifier) {
    val value by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "donut")
    val trackTop = Color(0xFFEAF2FB)
    val trackBottom = Color(0xFFB9D3EE)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // El nombre se reduce hasta caber en una línea (p. ej. «Interoperabilidad» en la pantalla exterior).
        androidx.compose.foundation.text.BasicText(
            label,
            style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 15.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.17f
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Brush.verticalGradient(listOf(trackTop, trackBottom)), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (value > 0f) drawArc(Green, -90f, 360f * value, false, Offset(inset, inset), arc, style = Stroke(stroke))
            }
            Text(
                if (empty) "—" else "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
    }
}
