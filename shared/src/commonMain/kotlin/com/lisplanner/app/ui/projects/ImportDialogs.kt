package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.Dialog
import com.lisplanner.app.data.ImportPreview
import com.lisplanner.app.data.Project
import com.lisplanner.app.ui.ImportState
import com.lisplanner.app.ui.components.DateField
import com.lisplanner.app.ui.components.LabeledValue
import com.lisplanner.app.ui.components.hoursLabel
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.theme.groupColor

/** Muestra el estado de la importación: leyendo, revisión antes de guardar o error. */
@Composable
fun ImportFlow(state: ImportState, onConfirm: (Project) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Loading -> Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Leyendo proyecto de MS Project…")
                        Text(state.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.ErrorOutline, null) },
            title = { Text("No se pudo importar") },
            text = {
                Text(
                    "«${state.fileName}» no se ha podido leer como proyecto de Microsoft Project (.mpp).\n\nDetalle: ${state.message}",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
        is ImportState.Ready -> ImportReview(state.preview, onConfirm, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportReview(preview: ImportPreview, onConfirm: (Project) -> Unit, onDismiss: () -> Unit) {
    val p = preview.project
    var name by remember { mutableStateOf(p.name) }
    var client by remember { mutableStateOf(p.client) }
    var goLive by remember { mutableStateOf(p.goLiveDate) }
    val groups = remember(p) { p.groups() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar de MS Project") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(preview.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del proyecto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("Go-Live", goLive, { goLive = it }, Modifier.fillMaxWidth())

                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Tareas", "${p.tasks.size}")
                    LabeledValue("Grupos", "${groups.size}")
                    LabeledValue("Dependencias", "${preview.dependencies}")
                    LabeledValue("Recursos", "${p.team.size}")
                    LabeledValue("Horas", hoursLabel(p.estimatedHours))
                    LabeledValue("Periodo", "${p.startDate.long()} → ${p.endDate.long()}")
                }

                Text("Grupos (tareas resumen)", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    groups.forEach { g ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(groupColor(g.colorIndex)))
                            Spacer(Modifier.width(8.dp))
                            Text(g.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${g.tasks.size} · ${hoursLabel(g.estimatedHours)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (preview.warnings.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp)) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(preview.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && goLive.isAfter(p.startDate),
                onClick = { onConfirm(p.copy(name = name.trim(), client = client.trim(), goLiveDate = goLive)) },
            ) { Text("Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
