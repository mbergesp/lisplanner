package com.lisplanner.app.ui.projects

import com.lisplanner.app.data.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lisplanner.app.data.LisPhase
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.ui.components.EmptyState
import com.lisplanner.app.ui.components.ProgressRing
import com.lisplanner.app.ui.components.StatusChip
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.relativeDays
import com.lisplanner.app.ui.theme.colors
import com.lisplanner.app.ui.theme.groupColor

@Composable
fun ProjectListScreen(
    projects: List<Project>,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<ProjectStatus?>(null) }
    val visible = remember(projects, query, filter) {
        projects
            .filter { filter == null || it.status == filter }
            .filter { query.isBlank() || listOf(it.name, it.client, it.site, it.lisSystem).any { f -> f.contains(query, ignoreCase = true) } }
            .sortedWith(compareBy({ it.status == ProjectStatus.COMPLETADO }, { it.goLiveDate }))
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nuevo proyecto") })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val roomy = maxWidth >= 440.dp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Proyectos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            if (roomy) {
                                FilledTonalButton(onClick = onImport) {
                                    Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Importar .mpp", maxLines = 1)
                                }
                            } else {
                                androidx.compose.material3.FilledTonalIconButton(onClick = onImport) {
                                    Icon(Icons.Outlined.UploadFile, "Importar de MS Project (.mpp)")
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Buscar…", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Borrar") } },
                        singleLine = true, shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todos (${projects.size})") }) }
                        items(ProjectStatus.entries) { s ->
                            val n = projects.count { it.status == s }
                            if (n > 0) FilterChip(selected = filter == s, onClick = { filter = if (filter == s) null else s }, label = { Text("${s.label} ($n)") })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Outlined.Biotech, "Sin proyectos", "Crea tu primer proyecto de implantación LIS.")
                }
            }
            items(visible, key = { it.id }) { p ->
                ProjectCard(p, selected = p.id == selectedId, onClick = { onOpen(p.id) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProjectCard(project: Project, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val overdue = project.overdueTasks().size
    val days = project.daysToGoLive()
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(project.status.label, project.status.colors())
                        if (project.type == com.lisplanner.app.data.ProjectType.ACTUALIZACION) {
                            StatusChip("Actualización", com.lisplanner.app.ui.theme.StatusColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(project.client, project.lisSystem).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ProgressRing(project.progress, size = 58.dp)
            }
            Spacer(Modifier.height(14.dp))
            PhaseStrip(project)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Event, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${if (project.type == com.lisplanner.app.data.ProjectType.ACTUALIZACION) "Producción" else "Go-Live"} ${project.goLiveDate.long()} · ${relativeDays(days)}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (overdue > 0) {
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(4.dp))
                        Text("$overdue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

/** Una franja por fase: se rellena según su avance. Resume el estado del plan de un vistazo. */
@Composable
fun PhaseStrip(project: Project, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        project.groups().take(16).forEach { g ->
            val p = g.progress
            val c = groupColor(g.colorIndex)
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().background(c))
            }
        }
    }
}
