package com.lisplanner.app.ui.agenda

import com.lisplanner.app.data.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.data.Task
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.editors.TaskEditor
import com.lisplanner.app.ui.projects.TaskRow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private enum class Bucket(val label: String) {
    ATRASADAS("Atrasadas"),
    SEMANA("Esta semana"),
    SIGUIENTE("Próxima semana"),
    MES("Próximos 30 días"),
}

/** Agenda transversal: lo que hay que mover esta semana en todos los proyectos activos. */
@Composable
fun AgendaScreen(
    projects: List<Project>,
    onSaveTask: (projectId: String, Task) -> Unit,
    onDeleteTask: (projectId: String, taskId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { today() }
    val endOfWeek = today.nextOrSame(DayOfWeek.SUNDAY)
    val endOfNext = endOfWeek.plusWeeks(1)
    val horizon = today.plusDays(30)
    var editing by remember { mutableStateOf<Pair<Project, Task>?>(null) }

    val buckets = remember(projects) {
        projects.filter { it.status != ProjectStatus.COMPLETADO }
            .flatMap { p -> p.tasks.filter { it.progress < 100 }.map { p to it } }
            .mapNotNull { pt ->
                val t = pt.second
                val bucket = when {
                    t.end.isBefore(today) -> Bucket.ATRASADAS
                    !t.start.isAfter(endOfWeek) -> Bucket.SEMANA
                    !t.start.isAfter(endOfNext) -> Bucket.SIGUIENTE
                    !t.start.isAfter(horizon) -> Bucket.MES
                    else -> null
                }
                bucket?.let { it to pt }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sortedBy { it.second.end } }
            .entries.sortedBy { it.key }.associate { it.key to it.value }
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Agenda", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Tareas abiertas de todos los proyectos activos",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (buckets.isEmpty()) {
                item { EmptyState(Icons.Outlined.EventAvailable, "Agenda despejada", "No hay tareas en los próximos 30 días.") }
            }
            buckets.forEach { (bucket, items) ->
                item(key = bucket.name) {
                    Text(
                        "${bucket.label} · ${items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (bucket == Bucket.ATRASADAS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { "${bucket.name}-${it.second.id}" }) { (p, t) ->
                    TaskRow(
                        task = t, today = today,
                        onClick = { editing = p to t },
                        onToggleDone = { onSaveTask(p.id, t.copy(progress = 100)) },
                        subtitle = "${p.client} · ${if (t.isMilestone) t.start.short() else "hasta ${t.end.short()}"}",
                        modifier = Modifier.widthIn(max = 900.dp).animateItem(),
                        accent = com.lisplanner.app.ui.theme.groupColor(p.colorIndexOf(t)),
                    )
                }
            }
        }
    }

    editing?.let { (p, t) ->
        TaskEditor(
            task = t, project = p,
            onDismiss = { editing = null },
            onSave = { onSaveTask(p.id, it); editing = null },
            onDelete = { onDeleteTask(p.id, t.id); editing = null },
        )
    }
}
