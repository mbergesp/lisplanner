package com.lisplanner.app.ui.components

import com.lisplanner.app.data.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.PersonLoad
import com.lisplanner.app.data.Workload

private enum class LoadView(val label: String) { TOTAL("Horas asignadas"), PROXIMAS("Próximas 4 semanas") }

/**
 * Gráfico de barras horizontales de carga por persona.
 * - Horas asignadas: imputadas (verde) + pendientes (azul), ordenado de más a menos.
 * - Próximas 4 semanas: horas pendientes frente a la capacidad (160 h); en rojo si se supera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkloadChart(loads: List<PersonLoad>, modifier: Modifier = Modifier, showProjects: Boolean = false) {
    var view by rememberSaveable { mutableStateOf(LoadView.TOTAL) }
    val spentColor = Color(0xFF43A047)
    val pendingColor = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loads.isEmpty()) {
            Text("No hay horas asignadas a personas todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadView.entries.forEach { v -> FilterChip(view == v, { view = v }, label = { Text(v.label) }) }
        }
        val sorted = when (view) {
            LoadView.TOTAL -> loads
            LoadView.PROXIMAS -> loads.sortedWith(compareBy<PersonLoad> { it.name == Workload.UNASSIGNED }.thenByDescending { it.upcoming })
        }
        val max = when (view) {
            LoadView.TOTAL -> sorted.maxOf { it.assigned }
            LoadView.PROXIMAS -> maxOf(Workload.CAPACITY_HOURS, sorted.maxOf { it.upcoming })
        }.coerceAtLeast(1.0)

        sorted.forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(p.name, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.openTasks} tareas abiertas" + if (showProjects && p.projects.isNotEmpty()) " · ${p.projects.joinToString()}" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val (value, alert) = when (view) {
                        LoadView.TOTAL -> hoursLabel(p.assigned) to false
                        LoadView.PROXIMAS -> "${hoursLabel(p.upcoming)} · ${(p.upcomingLoad * 100).toInt()}%" to (p.upcomingLoad > 1.0)
                    }
                    Text(value, style = MaterialTheme.typography.labelLarge, color = if (alert) over else MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    when (view) {
                        LoadView.TOTAL -> {
                            val total by animateFloatAsState((p.assigned / max).toFloat(), tween(600), label = "total")
                            val done by animateFloatAsState((p.spent.coerceAtMost(p.assigned) / max).toFloat(), tween(600), label = "spent")
                            Box(Modifier.fillMaxWidth(total).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(pendingColor))
                            Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(spentColor))
                        }
                        LoadView.PROXIMAS -> {
                            val frac by animateFloatAsState((p.upcoming / max).toFloat(), tween(600), label = "upcoming")
                            Box(
                                Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                                    .background(if (p.upcomingLoad > 1.0) over else pendingColor),
                            )
                            // Marca de capacidad (160 h)
                            val cap = (Workload.CAPACITY_HOURS / max).toFloat()
                            Box(Modifier.fillMaxWidth(cap).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                            }
                        }
                    }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (view) {
                LoadView.TOTAL -> {
                    Legend(spentColor, "Imputadas")
                    Legend(pendingColor, "Pendientes")
                }
                LoadView.PROXIMAS -> {
                    Legend(pendingColor, "Pendiente en 4 semanas")
                    Legend(over, "Sobrecarga")
                    Legend(MaterialTheme.colorScheme.onSurface, "Capacidad ${hoursLabel(Workload.CAPACITY_HOURS)} (40 h/sem)")
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
