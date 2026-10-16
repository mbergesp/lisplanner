package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Level
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.RiskItem
import com.lisplanner.app.data.RiskKind
import com.lisplanner.app.data.RiskStatus
import com.lisplanner.app.data.Severity
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.components.SectionCard
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.theme.colors
import kotlinx.datetime.LocalDate

private enum class RiskFilter(val label: String) { ABIERTOS("Abiertos"), RIESGOS("Riesgos"), INCIDENCIAS("Incidencias"), CERRADOS("Cerrados") }

@Composable
fun RisksTab(project: Project, onClick: (RiskItem) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(RiskFilter.ABIERTOS) }
    val visible = remember(project.risks, filter) {
        project.risks.filter {
            when (filter) {
                RiskFilter.ABIERTOS -> it.status != RiskStatus.CERRADO
                RiskFilter.RIESGOS -> it.kind == RiskKind.RIESGO && it.status != RiskStatus.CERRADO
                RiskFilter.INCIDENCIAS -> it.kind == RiskKind.INCIDENCIA && it.status != RiskStatus.CERRADO
                RiskFilter.CERRADOS -> it.status == RiskStatus.CERRADO
            }
        }.sortedWith(compareByDescending<RiskItem> { it.score }.thenBy { it.due ?: DATE_MAX })
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Summary(project) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RiskFilter.entries.size) { i ->
                    val f = RiskFilter.entries[i]
                    FilterChip(filter == f, { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Outlined.GppGood, "Nada que gestionar", "No hay elementos con este filtro.")
            }
        }
        items(visible, key = { it.id }) { RiskCard(it, onClick = { onClick(it) }) }
    }
}

@Composable
private fun Summary(project: Project) {
    val open = project.openRisks()
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        val counters: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Counter("Riesgos abiertos", open.count { it.kind == RiskKind.RIESGO }, MaterialTheme.colorScheme.primary)
                Counter("Incidencias abiertas", open.count { it.kind == RiskKind.INCIDENCIA }, MaterialTheme.colorScheme.tertiary)
                Counter("Críticos o altos", open.count { it.severity >= Severity.ALTA }, MaterialTheme.colorScheme.error)
            }
        }
        SectionCard("Matriz de riesgos", icon = Icons.Outlined.Shield) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    RiskMatrix(open.filter { it.kind == RiskKind.RIESGO }, Modifier.widthIn(max = 360.dp).weight(1f))
                    counters(Modifier.weight(1f))
                }
            } else {
                RiskMatrix(open.filter { it.kind == RiskKind.RIESGO }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                counters(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.titleMedium)
    }
}

/** Matriz 3×3 probabilidad × impacto con el número de riesgos abiertos en cada celda. */
@Composable
private fun RiskMatrix(risks: List<RiskItem>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text("Probabilidad", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(bottom = 4.dp))
        Level.entries.reversed().forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.width(44.dp))
                Level.entries.forEach { i ->
                    val n = risks.count { it.probability == p && it.impact == i }
                    val c = RiskItem(title = "", raised = DATE_MIN, probability = p, impact = i).severity.colors()
                    Box(
                        Modifier.weight(1f).aspectRatio(1.6f).padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (n > 0) c.content else c.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (n > 0) Text("$n", style = MaterialTheme.typography.titleMedium, color = c.container, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(44.dp))
            Level.entries.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Text("Impacto →", style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RiskCard(item: RiskItem, onClick: () -> Unit) {
    val sev = item.severity.colors()
    val today = remember { today() }
    val overdue = item.due != null && item.due.isBefore(today) && item.status != RiskStatus.CERRADO
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(sev.container),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("${item.score}", style = MaterialTheme.typography.titleMedium, color = sev.content, fontWeight = FontWeight.Bold)
                Text(item.severity.label, style = MaterialTheme.typography.labelSmall, color = sev.content, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (item.kind == RiskKind.INCIDENCIA) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.mitigation.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.mitigation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.owner.isNotBlank()) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(item.owner, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(10.dp))
                    }
                    if (item.due != null) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(14.dp), tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(item.due.long(), style = MaterialTheme.typography.labelSmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(item.status.label, item.status.colors())
        }
    }
}
