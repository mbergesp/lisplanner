package com.lisplanner.app.ui.editors

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Task
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.theme.groupColor
import kotlinx.datetime.LocalDate

/** Tareas que dependen (directa o indirectamente) de [taskId]: no pueden ser sus predecesoras sin crear un ciclo. */
private fun successorsOf(project: Project, taskId: String): Set<String> {
    val result = mutableSetOf<String>()
    val queue = ArrayDeque(listOf(taskId))
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        project.tasks.filter { current in it.dependsOn && result.add(it.id) }.forEach { queue.add(it.id) }
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DependenciesField(
    project: Project,
    selected: List<String>,
    start: LocalDate,
    onEdit: () -> Unit,
    onRemove: (String) -> Unit,
    onShift: (LocalDate) -> Unit,
) {
    val preds = selected.mapNotNull { id -> project.tasks.firstOrNull { it.id == id } }
    val latestEnd = preds.maxOfOrNull { it.end }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Depende de", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Empieza cuando terminen estas tareas",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Outlined.AddLink, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (preds.isEmpty()) "Añadir" else "Cambiar")
            }
        }
        if (preds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                preds.forEach { p ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(p.id) },
                        label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(groupColor(project.colorIndexOf(p)))) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Quitar", Modifier.size(InputChipDefaults.IconSize)) },
                    )
                }
            }
        }
        if (latestEnd != null && start.isBefore(latestEnd)) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Empieza antes de que termine una predecesora (${latestEnd.long()}).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShift(latestEnd.plusDays(1)) }) { Text("Ajustar") }
                }
            }
        }
    }
}

@Composable
fun DependencyPicker(
    project: Project,
    taskId: String?,
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(initial.toSet()) }
    var query by remember { mutableStateOf("") }
    val blocked = remember(project, taskId) { taskId?.let { successorsOf(project, it) + it }.orEmpty() }
    val candidates = remember(project, query) {
        project.tasks
            .filter { it.id !in blocked }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<Task>({ it.phase }, { it.start }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Predecesoras") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Buscar tarea") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.id }) { t ->
                        val checked = t.id in chosen
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .clickable { chosen = if (checked) chosen - t.id else chosen + t.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(groupColor(project.colorIndexOf(t))))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text((if (t.isMilestone) "◆ " else "") + t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${project.phaseOf(t)?.name ?: t.phase.short} · termina ${t.end.short()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen.toList()) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
