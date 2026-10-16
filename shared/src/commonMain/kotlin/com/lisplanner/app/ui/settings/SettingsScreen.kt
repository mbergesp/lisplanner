package com.lisplanner.app.ui.settings

import com.lisplanner.app.data.*

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lisplanner.app.data.Project
import com.lisplanner.app.notifications.AlertKind
import com.lisplanner.app.platform.LocalPlatform
import com.lisplanner.app.platform.rememberNotificationPermissionRequest
import com.lisplanner.app.notifications.ReminderSettings
import com.lisplanner.app.notifications.computeAlerts
import com.lisplanner.app.ui.components.DropdownField
import com.lisplanner.app.ui.components.SectionCard
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.theme.StatusColors
import com.lisplanner.app.ui.theme.colors
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.data.Severity
import com.lisplanner.app.data.TaskStatus

@Composable
fun SettingsScreen(
    projects: List<Project>,
    settings: ReminderSettings,
    onChange: (ReminderSettings) -> Unit,
    onSendNow: () -> Unit,
    onOpenProject: (String) -> Unit,
    onResetSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val twoColumns = maxWidth >= 720.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            NotificationsCard(projects, settings, onChange, onSendNow)
                            DataCard(onResetSample)
                        }
                        PreviewCard(projects, settings, onOpenProject, Modifier.weight(1f))
                    }
                } else {
                    NotificationsCard(projects, settings, onChange, onSendNow)
                    PreviewCard(projects, settings, onOpenProject)
                    DataCard(onResetSample)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsCard(
    projects: List<Project>,
    s: ReminderSettings,
    onChange: (ReminderSettings) -> Unit,
    onSendNow: () -> Unit,
) {
    val reminders = LocalPlatform.current.reminders
    var granted by remember { mutableStateOf(reminders.hasPermission()) }
    LifecycleResumeEffect(Unit) {
        granted = reminders.hasPermission()
        onPauseOrDispose {}
    }
    var askedOnce by remember { mutableStateOf(false) }
    val requestPermission = rememberNotificationPermissionRequest { ok ->
        granted = ok
        askedOnce = true
    }
    var pickingTime by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    SectionCard("Notificaciones", icon = Icons.Outlined.NotificationsActive) {
        if (!granted) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "El sistema tiene bloqueadas las notificaciones de LIS Planner.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (!askedOnce) requestPermission() else reminders.openSystemSettings()
                    }) { Text("Permitir") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        ListItem(
            headlineContent = { Text("Resumen diario") },
            supportingContent = { Text("Un aviso al día con lo que necesita tu atención") },
            trailingContent = { Switch(s.enabled, { onChange(s.copy(enabled = it)) }) },
            colors = transparent,
        )
        if (s.enabled) {
            ListItem(
                headlineContent = { Text("Hora del aviso") },
                trailingContent = { Text("${s.hour.toString().padStart(2, '0')}:${s.minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) },
                colors = transparent,
                modifier = Modifier.clickable { pickingTime = true },
            )
            DropdownField(
                "Avisar con antelación de", s.leadDays, listOf(1, 2, 3, 5, 7),
                { if (it == 1) "1 día" else "$it días" }, { onChange(s.copy(leadDays = it)) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val people = projects.flatMap { p -> p.team.map { it.name } }.distinct().sorted()
            DropdownField(
                "Tareas de", s.onlyAssignee.ifBlank { "Todo el equipo" }, listOf("Todo el equipo") + people, { it },
                { onChange(s.copy(onlyAssignee = if (it == "Todo el equipo") "" else it)) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Qué avisar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Toggle("Tareas atrasadas", "Fin ya pasado y sin completar", s.overdue) { onChange(s.copy(overdue = it)) }
            Toggle("Tareas que vencen pronto", "Terminan dentro del plazo de antelación", s.dueSoon) { onChange(s.copy(dueSoon = it)) }
            Toggle("Tareas que empiezan pronto", "Aún sin avance y con inicio cercano", s.startSoon) { onChange(s.copy(startSoon = it)) }
            Toggle("Hitos próximos", "Aprobaciones, Go-Live, cierre…", s.milestones) { onChange(s.copy(milestones = it)) }
            Toggle("Riesgos e incidencias", "Abiertos con fecha límite cercana o vencida", s.risks) { onChange(s.copy(risks = it)) }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onSendNow, enabled = granted, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Outlined.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enviar avisos ahora")
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = s.hour, initialMinute = s.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Hora del resumen diario") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
            confirmButton = {
                TextButton(onClick = { onChange(s.copy(hour = state.hour, minute = state.minute)); pickingTime = false }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun AlertKind.colors(): StatusColors = when (this) {
    AlertKind.ATRASADA -> TaskStatus.ATRASADA.colors()
    AlertKind.VENCE -> ProjectStatus.PAUSADO.colors()
    AlertKind.EMPIEZA -> TaskStatus.EN_CURSO.colors()
    AlertKind.HITO -> TaskStatus.COMPLETADA.colors()
    AlertKind.RIESGO -> Severity.ALTA.colors()
}

/** Lo que se notificaría hoy con la configuración actual. */
@Composable
private fun PreviewCard(projects: List<Project>, s: ReminderSettings, onOpenProject: (String) -> Unit, modifier: Modifier = Modifier) {
    val alerts = remember(projects, s) { computeAlerts(projects, s) }
    SectionCard("Avisos de hoy (${alerts.size})", icon = Icons.Outlined.Preview, modifier = modifier) {
        if (alerts.isEmpty()) {
            Text("Con la configuración actual no habría avisos hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        alerts.groupBy { it.projectId }.forEach { (pid, list) ->
            Text(
                list.first().projectName, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            list.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenProject(pid) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusChip(a.kind.label, a.kind.colors())
                }
            }
        }
    }
}

@Composable
private fun DataCard(onResetSample: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SectionCard("Datos", icon = Icons.Outlined.Storage) {
        Text(
            "Los proyectos se guardan solo en este dispositivo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { confirm = true }) {
            Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restaurar datos de ejemplo")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Restaurar datos de ejemplo?") },
            text = { Text("Se sustituirán todos tus proyectos por los de ejemplo. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirm = false; onResetSample() }) { Text("Restaurar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
