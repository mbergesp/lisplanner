package com.lisplanner.app.ui.editors

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.PhaseDef
import com.lisplanner.app.data.Project
import com.lisplanner.app.ui.theme.groupColor

/**
 * Editor de las fases de un proyecto: renombrar, cambiar color, reordenar, añadir y eliminar.
 * Al eliminar una fase con tareas se elige si moverlas a otra fase o borrarlas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseManager(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (phases: List<PhaseDef>, moveTasks: Map<String, String>, deleteTasksOf: Set<String>) -> Unit,
) {
    val phases = remember { mutableStateListOf<PhaseDef>().apply { addAll(project.phases) } }
    val moveTasks = remember { mutableStateMapOf<String, String>() }
    val deleteTasksOf = remember { mutableStateListOf<String>() }
    var deleting by remember { mutableStateOf<PhaseDef?>(null) }
    // Tareas que acabarán en la fase, teniendo en cuenta los movimientos pendientes de guardar.
    fun taskCount(p: PhaseDef) = project.tasks.count { it.phaseId !in deleteTasksOf && (moveTasks[it.phaseId] ?: it.phaseId) == p.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 720.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Fases del proyecto", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Cambia el nombre, el color o el orden. Las tareas conservan su fase.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            phases.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsar el círculo cambia el color de la fase
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(groupColor(p.colorIndex))
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { phases[i] = p.copy(colorIndex = (p.colorIndex + 1) % 10) },
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = p.name, onValueChange = { phases[i] = p.copy(name = it) },
                        singleLine = true, isError = p.name.isBlank(),
                        supportingText = { Text("${taskCount(p)} tareas") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    Column {
                        IconButton(onClick = { phases.add(i - 1, phases.removeAt(i)) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, "Subir")
                        }
                        IconButton(onClick = { phases.add(i + 1, phases.removeAt(i)) }, enabled = i < phases.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowDown, "Bajar")
                        }
                    }
                    IconButton(onClick = { if (taskCount(p) == 0) phases.removeAt(i) else deleting = p }) {
                        Icon(Icons.Outlined.Delete, "Eliminar fase", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedButton(onClick = {
                val used = phases.map { it.colorIndex }.toSet()
                phases.add(PhaseDef(name = "Nueva fase", colorIndex = (0 until 10).firstOrNull { it !in used } ?: phases.size % 10))
            }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir fase")
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(phases.map { it.copy(name = it.name.trim()) }, moveTasks.toMap(), deleteTasksOf.toSet()) },
                    enabled = phases.isNotEmpty() && phases.none { it.name.isBlank() },
                ) { Text("Guardar") }
            }
        }
    }

    deleting?.let { p ->
        val others = phases.filter { it.id != p.id }
        var target by remember(p.id) { mutableStateOf(others.getOrNull((phases.indexOf(p) - 1).coerceAtLeast(0)) ?: others.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar «${p.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Esta fase tiene ${taskCount(p)} tareas. ¿Qué quieres hacer con ellas?")
                    if (others.isNotEmpty() && target != null) {
                        com.lisplanner.app.ui.components.DropdownField("Mover las tareas a", target!!, others, { it.name }, { target = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = target != null, onClick = {
                    val to = target!!.id
                    // Las tareas que ya venían de otra fase eliminada también siguen al nuevo destino.
                    moveTasks.keys.filter { moveTasks[it] == p.id }.forEach { moveTasks[it] = to }
                    moveTasks[p.id] = to
                    phases.remove(p)
                    deleting = null
                }) { Text("Mover y eliminar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleting = null }) { Text("Cancelar") }
                    TextButton(onClick = {
                        deleteTasksOf.add(p.id)
                        moveTasks.keys.filter { moveTasks[it] == p.id }.forEach { deleteTasksOf.add(it); moveTasks.remove(it) }
                        phases.remove(p)
                        deleting = null
                    }) { Text("Borrar tareas", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}
