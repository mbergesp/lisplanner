package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Task
import com.lisplanner.app.data.TaskStatus
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.components.InitialsAvatar
import com.lisplanner.app.ui.components.SlimProgress
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.components.hoursLabel
import com.lisplanner.app.ui.theme.colors
import com.lisplanner.app.ui.theme.phaseColor
import com.lisplanner.app.ui.theme.groupColor
import kotlinx.datetime.LocalDate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Fila de la lista reordenable: cabecera de fase o tarea. */
private sealed interface TaskListRow {
    val key: String

    data class Header(val phaseId: String, val name: String, val colorIndex: Int) : TaskListRow {
        override val key = "h-$phaseId"
    }

    data class Item(val task: Task) : TaskListRow {
        override val key = task.id
    }
}

/** Filas en el orden guardado; se incluyen las fases vacías para poder soltar tareas en ellas. */
private fun buildRows(project: Project): List<TaskListRow> {
    val known = project.phases.map { it.id }.toSet()
    val rows = project.phases.flatMap { p ->
        listOf<TaskListRow>(TaskListRow.Header(p.id, p.name, p.colorIndex)) +
            project.tasks.filter { it.phaseId == p.id }.map { TaskListRow.Item(it) }
    }
    val orphans = project.tasks.filter { it.phaseId !in known }
    return if (orphans.isEmpty()) rows else rows + TaskListRow.Header("", "Sin fase", project.phases.size) + orphans.map { TaskListRow.Item(it) }
}

/**
 * Arrastrar y soltar sobre una LazyColumn: el elemento arrastrado se intercambia con el que tiene debajo
 * en cuanto su centro lo cruza, y la lista se desplaza sola al acercarse a los bordes.
 */
private class DragDropState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val canMoveTo: (Int) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var delta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)
    private var autoScroll: Job? = null

    private val draggingInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** Desplazamiento visual del elemento arrastrado respecto a su posición en la lista. */
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + delta - it.offset } ?: 0f

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = info.offset
        delta = 0f
    }

    fun drag(dy: Float) {
        delta += dy
        val current = draggingInfo ?: return
        val top = current.offset + draggingOffset
        val middle = top + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != draggingKey && middle.toInt() in it.offset..(it.offset + it.size)
        }
        if (target != null && canMoveTo(target.index)) {
            // Si el elemento es el primero visible, se fija el scroll para que la lista no "salte".
            if (current.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
                scope.launch { listState.scrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            }
            onMove(current.index, target.index)
        }
        // Auto-scroll cerca de los bordes
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val edge = 120f
        val speed = when {
            top < listState.layoutInfo.viewportStartOffset + edge -> -18f
            top + current.size > viewportEnd - edge -> 18f
            else -> 0f
        }
        autoScroll?.cancel()
        if (speed != 0f) {
            autoScroll = scope.launch {
                while (isActive && draggingKey != null) {
                    val consumed = listState.scrollBy(speed)
                    if (consumed == 0f) break
                    delta += consumed
                    delay(16)
                }
            }
        }
    }

    fun end() {
        autoScroll?.cancel()
        draggingKey = null
        delta = 0f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksTab(
    project: Project,
    onTaskClick: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onReorder: (List<Pair<String, String>>) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val today = remember { today() }
    var filter by rememberSaveable { mutableStateOf<TaskStatus?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Copia local editable mientras se arrastra; se resincroniza cuando cambia el proyecto.
    val rows = remember { mutableStateListOf<TaskListRow>() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(project.tasks, project.phases) {
        if (!dragging) {
            rows.clear()
            rows.addAll(buildRows(project))
        }
    }
    val leading = 1 // índice 0 = filtros
    val dragState = remember(listState) {
        DragDropState(
            listState, scope,
            // Una tarea no puede quedar por encima de la primera fase.
            canMoveTo = { index -> index - leading >= 1 },
            onMove = { from, to ->
                val f = from - leading
                val t = to - leading
                if (f in rows.indices && t in rows.indices) rows.add(t, rows.removeAt(f))
            },
        )
    }

    fun commit() {
        var phaseId = ""
        val order = rows.mapNotNull { r ->
            when (r) {
                is TaskListRow.Header -> { phaseId = r.phaseId; null }
                is TaskListRow.Item -> r.task.id to phaseId
            }
        }
        onReorder(order)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filters") {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { FilterChip(filter == null, { filter = null }, label = { Text("Todas (${project.tasks.size})") }) }
                    items(listOf(TaskStatus.ATRASADA, TaskStatus.EN_CURSO, TaskStatus.PENDIENTE, TaskStatus.COMPLETADA)) { s ->
                        val n = project.tasks.count { it.status(today) == s }
                        FilterChip(filter == s, { filter = if (filter == s) null else s }, label = { Text("${s.label} ($n)") })
                    }
                }
                Text(
                    if (filter == null) "Arrastra ⠿ para ordenar las tareas o moverlas a otra fase."
                    else "Quita el filtro para ordenar las tareas arrastrándolas.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (project.tasks.isEmpty()) {
            item(key = "empty") { EmptyState(Icons.Outlined.TaskAlt, "Sin tareas", "Añade la primera tarea con el botón +.") }
        }

        if (filter == null) {
            // Vista completa y reordenable: cabeceras como filas normales para poder soltar entre fases.
            itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                val isDragged = dragState.draggingKey == row.key
                val dragModifier = if (isDragged) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dragState.draggingOffset; scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 16f }
                } else {
                    Modifier.animateItem()
                }
                when (row) {
                    is TaskListRow.Header -> {
                        val count = rows.dropWhile { it != row }.drop(1).takeWhile { it is TaskListRow.Item }
                        PhaseHeader(row.name, row.colorIndex, count.count { (it as TaskListRow.Item).task.progress >= 100 }, count.size, dragModifier)
                    }
                    is TaskListRow.Item -> {
                        val handle = Modifier.pointerInput(row.key) {
                            detectDragGestures(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = true
                                    dragState.start(row.key)
                                },
                                onDrag = { change, amount -> change.consume(); dragState.drag(amount.y) },
                                onDragEnd = { dragState.end(); commit(); dragging = false },
                                onDragCancel = { dragState.end(); commit(); dragging = false },
                            )
                        }
                        val accent = groupColor(rows.take(rows.indexOf(row)).filterIsInstance<TaskListRow.Header>().lastOrNull()?.colorIndex ?: 0)
                        SwipeToDelete(row.task, enabled = !dragging, onDelete = onDelete, modifier = dragModifier) {
                            TaskRow(
                                row.task, today,
                                onClick = { onTaskClick(row.task) }, onToggleDone = { onToggleDone(row.task) },
                                accent = accent, dragHandle = handle,
                            )
                        }
                    }
                }
            }
        } else {
            project.groups()
                .map { g -> g to g.tasks.filter { it.status(today) == filter } }
                .filter { it.second.isNotEmpty() }
                .also { if (it.isEmpty()) item(key = "none") { EmptyState(Icons.Outlined.TaskAlt, "Nada por aquí", "No hay tareas con este filtro.") } }
                .forEach { (group, tasks) ->
                    stickyHeader(key = "h-${group.key}") {
                        PhaseHeader(group.label, group.colorIndex, tasks.count { it.progress >= 100 }, tasks.size, Modifier)
                    }
                    items(tasks, key = { it.id }) { task ->
                        SwipeToDelete(task, enabled = true, onDelete = onDelete, modifier = Modifier.animateItem()) {
                            TaskRow(task, today, onClick = { onTaskClick(task) }, onToggleDone = { onToggleDone(task) }, accent = groupColor(group.colorIndex))
                        }
                    }
                }
        }
    }
}

@Composable
private fun PhaseHeader(name: String, colorIndex: Int, done: Int, total: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(groupColor(colorIndex)))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            if (total == 0) "vacía · suelta tareas aquí" else "$done/$total",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: androidx.compose.ui.graphics.Color = phaseColor(task.phase),
    dragHandle: Modifier? = null,
) {
    val status = task.status(today)
    val done = status == TaskStatus.COMPLETADA
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            if (dragHandle != null) {
                Box(dragHandle.fillMaxHeight().width(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DragIndicator, "Arrastrar para mover", tint = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onToggleDone) {
                Icon(
                    if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    if (done) "Marcar como pendiente" else "Marcar como completada",
                    tint = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    (if (task.isMilestone) "◆ " else "") + task.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle ?: (
                        (if (task.isMilestone) task.start.short() else "${task.start.short()} → ${task.end.short()} · ${task.durationDays} d") +
                            if (task.estimatedHours > 0) " · ${hoursLabel(task.spentHours)} / ${hoursLabel(task.estimatedHours)}" else ""
                        ),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!task.isMilestone && !done) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SlimProgress(task.progress / 100f, accent, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${task.progress}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(status.label, status.colors())
                if (task.assignee.isNotBlank()) InitialsAvatar(task.assignee, size = 28.dp)
            }
        }
    }
}

/** Deslizar la tarjeta hacia la izquierda para borrar la tarea (con fondo rojo y papelera). */
@Composable
private fun SwipeToDelete(task: Task, enabled: Boolean, onDelete: (Task) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    // Estado NO guardable a propósito: si se guardara, al deshacer el borrado la tarjeta volvería
    // en estado «deslizada» y se borraría otra vez.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        onDismiss = { onDelete(task) },
        backgroundContent = {
            val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                    .background(if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
                    Text("Eliminar", style = MaterialTheme.typography.labelLarge, color = tint)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Delete, null, tint = tint)
                }
            }
        },
    ) { content() }
}
