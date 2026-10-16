package com.lisplanner.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisplanner.app.data.*
import com.lisplanner.app.notifications.ReminderSettings
import com.lisplanner.app.notifications.computeAlerts
import com.lisplanner.app.platform.PickedFile
import com.lisplanner.app.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Destination { INICIO, PROYECTOS, AGENDA, AJUSTES }

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val fileName: String) : ImportState
    data class Ready(val preview: ImportPreview) : ImportState
    data class Failed(val fileName: String, val message: String) : ImportState
}

class AppViewModel(private val platform: Platform) : ViewModel() {
    private val repo = ProjectRepository(platform.storage, viewModelScope)

    val projects: StateFlow<List<Project>> = repo.projects

    val reminderSettings: StateFlow<ReminderSettings> = platform.reminders.settings

    init {
        // iOS programa sus avisos locales con el contenido ya calculado: se refrescan con cada cambio.
        viewModelScope.launch { projects.collect { platform.reminders.onDataChanged(it) } }
    }

    fun updateReminders(s: ReminderSettings) {
        platform.reminders.update(s)
        platform.reminders.onDataChanged(projects.value)
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    /** Lee un proyecto de MS Project (.mpp o .xml) en segundo plano y deja el resultado listo para revisar. */
    fun importFrom(file: PickedFile) {
        val name = file.name
        _importState.value = ImportState.Loading(name)
        viewModelScope.launch {
            _importState.value = runCatching {
                withContext(Dispatchers.Default) {
                    val isXml = name.endsWith(".xml", ignoreCase = true) ||
                        file.bytes.take(64).toByteArray().decodeToString().trimStart('﻿', ' ', '\n', '\r').startsWith("<")
                    if (isXml) {
                        MspdiImporter.read(file.bytes.decodeToString(), name)
                    } else {
                        platform.parseMpp(file.bytes, name)
                            ?: error("En este dispositivo no se pueden leer ficheros .mpp. En MS Project usa «Guardar como → Formato XML» e importa el .xml.")
                    }
                }
            }.fold(
                onSuccess = { if (it.project.tasks.isEmpty()) ImportState.Failed(name, "El fichero no contiene tareas con fechas.") else ImportState.Ready(it) },
                onFailure = { ImportState.Failed(name, it.message ?: it.toString()) },
            )
        }
    }

    fun confirmImport(project: Project) {
        repo.upsertProject(project)
        _importState.value = ImportState.Idle
        openProject(project.id)
    }

    fun dismissImport() {
        _importState.value = ImportState.Idle
    }

    /** Publica ya los avisos con los datos en memoria. Devuelve cuántos proyectos tienen avisos. */
    fun sendRemindersNow(): Int = platform.reminders.sendNow(computeAlerts(projects.value, reminderSettings.value))

    // El estado de navegación vive en el ViewModel para sobrevivir al plegado/desplegado del Fold.
    private val _destination = MutableStateFlow(Destination.INICIO)
    val destination = _destination.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId = _selectedProjectId.asStateFlow()

    fun navigate(to: Destination) {
        _destination.value = to
    }

    fun openProject(id: String?) {
        _selectedProjectId.value = id
        if (id != null) _destination.value = Destination.PROYECTOS
    }

    /** Guarda el proyecto; si es nuevo y se pide plantilla, genera el plan de implantación LIS. */
    fun saveProject(project: Project, applyTemplate: Boolean) {
        val isNew = projects.value.none { it.id == project.id }
        val toSave = if (isNew && applyTemplate) {
            ProjectTemplates.apply(project)
        } else if (isNew && project.phases.isEmpty()) {
            project.copy(phases = DefaultPhases.create())
        } else {
            project
        }
        repo.upsertProject(toSave)
        if (isNew) openProject(toSave.id)
    }

    fun deleteProject(id: String) {
        repo.deleteProject(id)
        if (_selectedProjectId.value == id) _selectedProjectId.value = null
    }

    fun saveTask(projectId: String, task: Task) = repo.upsertTask(projectId, task)
    fun deleteTask(projectId: String, taskId: String) = repo.deleteTask(projectId, taskId)
    fun saveIntegration(projectId: String, integration: Integration) = repo.upsertIntegration(projectId, integration)
    fun deleteIntegration(projectId: String, id: String) = repo.deleteIntegration(projectId, id)
    fun scheduleByHours(projectId: String) = repo.scheduleByHours(projectId)
    fun restoreTasks(projectId: String, tasks: List<Task>) = repo.setTasks(projectId, tasks)

    /** Borra la tarea y devuelve las tareas anteriores para poder deshacer (null si ya no existía). */
    fun deleteTaskForUndo(projectId: String, taskId: String): List<Task>? {
        val before = projects.value.firstOrNull { it.id == projectId }?.tasks ?: return null
        if (before.none { it.id == taskId }) return null
        repo.deleteTask(projectId, taskId)
        return before
    }
    fun reorderTasks(projectId: String, order: List<Pair<String, String>>) = repo.reorderTasks(projectId, order)
    fun updatePhases(projectId: String, phases: List<com.lisplanner.app.data.PhaseDef>, moveTasks: Map<String, String>, deleteTasksOf: Set<String>) =
        repo.updatePhases(projectId, phases, moveTasks, deleteTasksOf)
    fun saveRisk(projectId: String, risk: RiskItem) = repo.upsertRisk(projectId, risk)
    fun deleteRisk(projectId: String, id: String) = repo.deleteRisk(projectId, id)
    fun addMember(projectId: String, member: TeamMember) = repo.addMember(projectId, member)
    fun removeMember(projectId: String, memberId: String) = repo.removeMember(projectId, memberId)
    fun resetSampleData() {
        repo.resetSampleData()
        _selectedProjectId.value = null
    }
}
