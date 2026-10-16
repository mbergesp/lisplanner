package com.lisplanner.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Fichero de texto donde cada plataforma guarda los proyectos (JSON). */
interface ProjectStorage {
    fun read(): String?

    /** Escritura atómica (fichero temporal + renombrado). */
    fun write(text: String)
}

/**
 * Almacena los proyectos en un fichero JSON en el almacenamiento interno de la app.
 * Toda la lista vive en memoria como [StateFlow]; cada cambio se persiste en segundo plano.
 */
class ProjectRepository(private val storage: ProjectStorage, private val scope: CoroutineScope) {
    private val writeLock = Mutex()

    private val _projects = MutableStateFlow(load().map { it.normalized() })
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private fun load(): List<Project> = decodeProjects(storage.read())

    private fun persist() {
        val snapshot = _projects.value
        scope.launch(Dispatchers.Default) {
            writeLock.withLock { storage.write(json.encodeToString(snapshot)) }
        }
    }

    private fun mutate(block: (List<Project>) -> List<Project>) {
        _projects.update(block)
        persist()
    }

    // Tras cualquier cambio se recalculan las fechas de las tareas programadas por horas.
    private fun mutateProject(projectId: String, block: (Project) -> Project) =
        mutate { list -> list.map { if (it.id == projectId) Scheduler.reschedule(block(it).normalized()) else it } }

    fun upsertProject(project0: Project) = mutate { list ->
        val project = Scheduler.reschedule(project0.normalized())
        if (list.any { it.id == project.id }) list.map { if (it.id == project.id) project else it } else list + project
    }

    fun deleteProject(projectId: String) = mutate { list -> list.filterNot { it.id == projectId } }

    fun upsertTask(projectId: String, task: Task) = mutateProject(projectId) { p ->
        p.copy(tasks = if (p.tasks.any { it.id == task.id }) p.tasks.map { if (it.id == task.id) task else it } else p.tasks + task)
    }

    fun deleteTask(projectId: String, taskId: String) = mutateProject(projectId) { p ->
        p.copy(
            tasks = p.tasks.filterNot { it.id == taskId }
                .map { t -> if (taskId in t.dependsOn) t.copy(dependsOn = t.dependsOn - taskId) else t },
        )
    }

    fun upsertRisk(projectId: String, risk: RiskItem) = mutateProject(projectId) { p ->
        p.copy(risks = if (p.risks.any { it.id == risk.id }) p.risks.map { if (it.id == risk.id) risk else it } else p.risks + risk)
    }

    fun deleteRisk(projectId: String, riskId: String) = mutateProject(projectId) { p ->
        p.copy(risks = p.risks.filterNot { it.id == riskId })
    }

    fun upsertIntegration(projectId: String, integration: Integration) = mutateProject(projectId) { p ->
        p.copy(
            integrations = if (p.integrations.any { it.id == integration.id }) {
                p.integrations.map { if (it.id == integration.id) integration else it }
            } else {
                p.integrations + integration
            },
        )
    }

    fun deleteIntegration(projectId: String, integrationId: String) = mutateProject(projectId) { p ->
        p.copy(integrations = p.integrations.filterNot { it.id == integrationId })
    }

    fun addMember(projectId: String, member: TeamMember) = mutateProject(projectId) { it.copy(team = it.team + member) }

    fun removeMember(projectId: String, memberId: String) = mutateProject(projectId) { p ->
        p.copy(team = p.team.filterNot { it.id == memberId })
    }

    /** Pasa a programación por horas todas las tareas con horas (y los hitos con predecesoras), sin mover sus fechas actuales. */
    fun scheduleByHours(projectId: String) = mutateProject(projectId) { p ->
        p.copy(
            tasks = p.tasks.map { t ->
                when {
                    t.autoSchedule -> t
                    t.isMilestone && t.dependsOn.isNotEmpty() -> t.copy(autoSchedule = true)
                    !t.isMilestone && t.estimatedHours > 0 -> t.copy(autoSchedule = true, dailyHours = Scheduler.dedicationFor(t))
                    else -> t
                }
            },
        )
    }

    /**
     * Sustituye la lista de fases. Las tareas de las fases eliminadas se mueven a la fase indicada en
     * [moveTasks] (id antiguo → id nuevo) o se borran si su fase está en [deleteTasksOf].
     */
    fun updatePhases(projectId: String, phases: List<PhaseDef>, moveTasks: Map<String, String>, deleteTasksOf: Set<String>) =
        mutateProject(projectId) { p ->
            val removed = p.tasks.filter { it.phaseId in deleteTasksOf }.map { it.id }.toSet()
            p.copy(
                phases = phases,
                tasks = p.tasks.filterNot { it.id in removed }.map { t ->
                    val newPhase = moveTasks[t.phaseId] ?: t.phaseId
                    val name = phases.firstOrNull { it.id == newPhase }?.name
                    t.copy(
                        phaseId = newPhase,
                        phase = if (newPhase != t.phaseId && name != null) guessPhase(name) else t.phase,
                        dependsOn = t.dependsOn.filterNot { it in removed },
                    )
                },
            )
        }

    /** Aplica el orden manual de las tareas y su fase (resultado de arrastrar y soltar). */
    fun reorderTasks(projectId: String, order: List<Pair<String, String>>) = mutateProject(projectId) { p ->
        val byId = p.tasks.associateBy { it.id }
        val moved = order.mapNotNull { (id, phaseId) ->
            val t = byId[id] ?: return@mapNotNull null
            if (t.phaseId == phaseId) t else {
                val name = p.phases.firstOrNull { it.id == phaseId }?.name
                t.copy(phaseId = phaseId, phase = name?.let(::guessPhase) ?: t.phase)
            }
        }
        val rest = p.tasks.filter { t -> order.none { it.first == t.id } }
        p.copy(tasks = moved + rest)
    }

    /** Restaura la lista de tareas completa (deshacer un borrado: vuelve la tarea y las dependencias hacia ella). */
    fun setTasks(projectId: String, tasks: List<Task>) = mutateProject(projectId) { it.copy(tasks = tasks) }

    fun resetSampleData() = mutate { SampleData.projects().map { it.normalized() } }
}

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

/** Lee el JSON guardado; sin fichero (primera ejecución) o si está dañado, carga los datos de ejemplo. */
fun decodeProjects(text: String?): List<Project> {
    if (text == null) return SampleData.projects()
    return runCatching { json.decodeFromString<List<Project>>(text) }
        .onFailure { println("ProjectRepository: no se pudo leer el fichero de proyectos: $it") }
        .getOrElse { SampleData.projects() }
}
