package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Integration
import com.lisplanner.app.data.IntegrationStatus
import com.lisplanner.app.data.IntegrationType
import com.lisplanner.app.data.Project
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.theme.colors

private fun IntegrationType.icon() = when (this) {
    IntegrationType.ANALIZADOR -> Icons.Outlined.Science
    IntegrationType.MIDDLEWARE -> Icons.Outlined.Dns
    IntegrationType.HIS -> Icons.Outlined.LocalHospital
    IntegrationType.PETICIONARIO -> Icons.Outlined.Assignment
    IntegrationType.OTRO -> Icons.Outlined.Settings
}

@Composable
fun IntegrationsTab(project: Project, onClick: (Integration) -> Unit) {
    val items = project.integrations.sortedWith(compareBy({ it.type }, { it.status }, { it.name }))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { StatusSummary(project.integrations) }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Outlined.Cable, "Sin integraciones", "Registra analizadores, middleware e interfaces con el HIS.")
            }
        }
        items(items, key = { it.id }) { IntegrationCard(it, onClick = { onClick(it) }) }
    }
}

/** Barra apilada con el reparto de integraciones por estado. */
@Composable
private fun StatusSummary(list: List<Integration>) {
    if (list.isEmpty()) return
    val validated = list.count { it.status == IntegrationStatus.VALIDADO }
    Column {
        Text("$validated de ${list.size} validadas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IntegrationStatus.entries.forEach { s ->
                val n = list.count { it.status == s }
                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxSize().background(s.colors().content))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IntegrationStatus.entries.forEach { s ->
                val n = list.count { it.status == s }
                if (n > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(s.colors().content))
                    Spacer(Modifier.width(4.dp))
                    Text("${s.label} $n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun IntegrationCard(item: Integration, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(item.type.icon(), null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.manufacturer, item.area).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { item.type.label },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(item.protocol.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(item.status.label, item.status.colors())
        }
    }
}
