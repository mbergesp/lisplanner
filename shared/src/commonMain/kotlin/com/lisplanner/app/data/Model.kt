package com.lisplanner.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias JsonDate = @Serializable(with = LocalDateSerializer::class) LocalDate

fun newId(): String = kotlin.uuid.Uuid.random().toString()

/** Fases típicas de una implantación de un sistema de información de laboratorio. */
enum class LisPhase(val label: String, val short: String) {
    KICKOFF("Inicio y planificación", "Inicio"),
    ANALISIS("Análisis y levantamiento", "Análisis"),
    CONFIGURACION("Configuración del LIS", "Configuración"),
    ANALIZADORES("Conexión de analizadores", "Analizadores"),
    INTERFACES("Interfaces HIS / HL7", "Interfaces"),
    MIGRACION("Migración de datos", "Migración"),
    VALIDACION("Validación y pruebas", "Validación"),
    FORMACION("Formación", "Formación"),
    ARRANQUE("Arranque (Go-Live)", "Go-Live"),
    SOPORTE("Soporte post-arranque", "Soporte"),
}

enum class ProjectType(val label: String) {
    IMPLANTACION("Implantación"),
    ACTUALIZACION("Actualización"),
}

/** Fase de un proyecto: editable (nombre, orden, color). */
@Serializable
data class PhaseDef(
    val id: String = newId(),
    val name: String,
    val colorIndex: Int = 0,
)

enum class ScheduleHealth(val label: String) {
    RETRASADO("Retrasado"),
    LIGERO_RETRASO("Ligero retraso"),
    EN_PLAZO("En plazo"),
}

enum class ProjectStatus(val label: String) {
    PLANIFICACION("Planificación"),
    EN_CURSO("En curso"),
    EN_RIESGO("En riesgo"),
    PAUSADO("Pausado"),
    COMPLETADO("Completado"),
}

enum class TaskStatus(val label: String) {
    PENDIENTE("Pendiente"),
    EN_CURSO("En curso"),
    ATRASADA("Atrasada"),
    COMPLETADA("Completada"),
}

enum class IntegrationType(val label: String) {
    ANALIZADOR("Analizador"),
    MIDDLEWARE("Middleware"),
    HIS("HIS / HCE"),
    PETICIONARIO("Peticionario electrónico"),
    OTRO("Otro sistema"),
}

enum class Protocol(val label: String) {
    ASTM("ASTM E1381/E1394"),
    LIS2A2("CLSI LIS2-A2"),
    HL7V2("HL7 v2.x"),
    FHIR("HL7 FHIR"),
    POCT1A("POCT1-A"),
    FICHERO("Fichero / CSV"),
    PROPIETARIO("Propietario"),
}

enum class IntegrationStatus(val label: String) {
    PENDIENTE("Pendiente"),
    CONFIGURANDO("Configurando"),
    PRUEBAS("En pruebas"),
    VALIDADO("Validado"),
    BLOQUEADO("Bloqueado"),
}

@Serializable
data class Task(
    val id: String = newId(),
    val name: String,
    val phase: LisPhase,
    val start: JsonDate,
    val end: JsonDate,
    val progress: Int = 0,
    val assignee: String = "",
    val isMilestone: Boolean = false,
    val notes: String = "",
    // Predecesoras (fin → inicio): esta tarea no debería empezar antes de que terminen.
    val dependsOn: List<String> = emptyList(),
    // Esfuerzo: horas estimadas para completar la tarea y horas ya imputadas.
    val estimatedHours: Double = 0.0,
    val spentHours: Double = 0.0,
    // Agrupación libre (p. ej. la tarea resumen de MS Project). Vacío = agrupar por fase LIS.
    val group: String = "",
    // Fase del proyecto (PhaseDef.id). `phase` queda como categoría LIS para colores y clasificación.
    val phaseId: String = "",
    // Programación por horas: la duración sale de estimatedHours / dailyHours (jornadas laborables)
    // y el inicio, de las predecesoras. Si es false, las fechas son manuales.
    val autoSchedule: Boolean = false,
    val dailyHours: Double = 8.0,
) {
    val durationDays: Long get() = daysBetween(start, end) + 1

    fun status(today: LocalDate = today()): TaskStatus = when {
        progress >= 100 -> TaskStatus.COMPLETADA
        end.isBefore(today) -> TaskStatus.ATRASADA
        !start.isAfter(today) -> TaskStatus.EN_CURSO
        else -> TaskStatus.PENDIENTE
    }
}

enum class RiskKind(val label: String) {
    RIESGO("Riesgo"),
    INCIDENCIA("Incidencia"),
}

enum class RiskStatus(val label: String) {
    ABIERTO("Abierto"),
    EN_GESTION("En gestión"),
    CERRADO("Cerrado"),
}

enum class Level(val label: String, val value: Int) {
    BAJA("Baja", 1),
    MEDIA("Media", 2),
    ALTA("Alta", 3),
}

enum class Severity(val label: String) { BAJA("Baja"), MEDIA("Media"), ALTA("Alta"), CRITICA("Crítica") }

/**
 * Riesgo (algo que podría pasar) o incidencia (algo que ya ha pasado).
 * En las incidencias la probabilidad no aplica: se valora solo el impacto.
 */
@Serializable
data class RiskItem(
    val id: String = newId(),
    val kind: RiskKind = RiskKind.RIESGO,
    val title: String,
    val description: String = "",
    val probability: Level = Level.MEDIA,
    val impact: Level = Level.MEDIA,
    val status: RiskStatus = RiskStatus.ABIERTO,
    val owner: String = "",
    val mitigation: String = "",
    val raised: JsonDate,
    val due: JsonDate? = null,
) {
    val score: Int get() = if (kind == RiskKind.INCIDENCIA) impact.value * 3 else probability.value * impact.value

    val severity: Severity get() = when {
        score >= 9 -> Severity.CRITICA
        score >= 6 -> Severity.ALTA
        score >= 3 -> Severity.MEDIA
        else -> Severity.BAJA
    }
}

@Serializable
data class Integration(
    val id: String = newId(),
    val name: String,
    val type: IntegrationType,
    val manufacturer: String = "",
    val protocol: Protocol,
    val area: String = "",
    val status: IntegrationStatus = IntegrationStatus.PENDIENTE,
    val notes: String = "",
)

@Serializable
data class TeamMember(
    val id: String = newId(),
    val name: String,
    val role: String,
)

@Serializable
data class Project(
    val id: String = newId(),
    val name: String,
    val client: String,
    val site: String = "",
    val lisSystem: String = "",
    val projectManager: String = "",
    val startDate: JsonDate,
    val goLiveDate: JsonDate,
    val status: ProjectStatus = ProjectStatus.PLANIFICACION,
    val type: ProjectType = ProjectType.IMPLANTACION,
    // Solo en actualizaciones: versión instalada y versión a la que se actualiza.
    val currentVersion: String = "",
    val targetVersion: String = "",
    val phases: List<PhaseDef> = emptyList(),
    val description: String = "",
    val tasks: List<Task> = emptyList(),
    val integrations: List<Integration> = emptyList(),
    val team: List<TeamMember> = emptyList(),
    val risks: List<RiskItem> = emptyList(),
) {
    /** Avance ponderado por la duración de cada tarea (los hitos no cuentan). */
    val progress: Float
        get() {
            val work = tasks.filterNot { it.isMilestone }
            val total = work.sumOf { it.durationDays }
            if (total == 0L) return 0f
            return work.sumOf { it.durationDays * it.progress } / (total * 100f)
        }

    val endDate: LocalDate get() = (tasks.map { it.end } + goLiveDate).max()

    fun daysToGoLive(today: LocalDate = today()): Long = daysBetween(today, goLiveDate)

    fun phaseProgress(phase: LisPhase): Float? {
        val work = tasks.filter { it.phase == phase && !it.isMilestone }
        val total = work.sumOf { it.durationDays }
        if (total == 0L) return null
        return work.sumOf { it.durationDays * it.progress } / (total * 100f)
    }

    fun overdueTasks(today: LocalDate = today()) = tasks.filter { it.status(today) == TaskStatus.ATRASADA }

    /** Tareas que empiezan antes de que termine alguna de sus predecesoras. */
    fun dependencyConflicts(): List<Pair<Task, Task>> {
        val byId = tasks.associateBy { it.id }
        return tasks.flatMap { t -> t.dependsOn.mapNotNull { byId[it] }.filter { t.start.isBefore(it.end) }.map { it to t } }
    }

    fun openRisks() = risks.filter { it.status != RiskStatus.CERRADO }

    val estimatedHours: Double get() = tasks.sumOf { it.estimatedHours }
    val spentHours: Double get() = tasks.sumOf { it.spentHours }

    /** Horas que faltan según el avance declarado de cada tarea. */
    val remainingHours: Double get() = tasks.sumOf { it.estimatedHours * (100 - it.progress) / 100.0 }

    fun phaseHours(phase: LisPhase): Double = tasks.filter { it.phase == phase }.sumOf { it.estimatedHours }

    /** Los proyectos importados se agrupan por sus tareas resumen; los de plantilla, por fase LIS. */
    val usesCustomGroups: Boolean get() = tasks.any { it.group.isNotBlank() }

    /** Grupos para Gantt, listas e informes: las fases del proyecto que tienen tareas, en su orden. */
    fun groups(): List<TaskGroup> {
        if (phases.isEmpty()) return normalized().takeIf { it.phases.isNotEmpty() }?.groups() ?: emptyList()
        val known = phases.map { it.id }.toSet()
        val list = phases.mapNotNull { p ->
            val ts = tasks.filter { it.phaseId == p.id }
            if (ts.isEmpty()) null else TaskGroup(p.id, p.name, p.name, p.colorIndex, ts)
        }
        val orphans = tasks.filter { it.phaseId !in known }
        return if (orphans.isEmpty()) list else list + TaskGroup("_sin_fase", "Sin fase", "Sin fase", phases.size, orphans)
    }

    /**
     * Avance que debería llevar el proyecto hoy según las fechas de las tareas (valor planificado),
     * ponderado por duración como [progress].
     */
    fun expectedProgress(today: LocalDate = today()): Float {
        val work = tasks.filterNot { it.isMilestone }
        val total = work.sumOf { it.durationDays }
        if (total == 0L) return 0f
        val planned = work.sumOf { t ->
            when {
                !today.isAfter(t.start) -> 0.0
                today.isAfter(t.end) -> t.durationDays.toDouble()
                else -> daysBetween(t.start, today).toDouble()
            }
        }
        return (planned / total).toFloat()
    }

    /** Cumplimiento de plazos: compara el avance real con el previsto a fecha de hoy. */
    fun scheduleHealth(today: LocalDate = today()): ScheduleHealth {
        val spi = schedulePerformance(today)
        return when {
            spi < 0.74f -> ScheduleHealth.RETRASADO
            spi < 0.92f -> ScheduleHealth.LIGERO_RETRASO
            else -> ScheduleHealth.EN_PLAZO
        }
    }

    /** Índice de cumplimiento de plazos: avance real ÷ avance previsto (1 = en plazo). */
    fun schedulePerformance(today: LocalDate = today()): Float {
        val expected = expectedProgress(today)
        return if (expected < 0.01f) 1f else progress / expected
    }

    /** Fecha en que termina la última tarea: el fin calculado del proyecto. */
    val calculatedEnd: LocalDate? get() = tasks.maxOfOrNull { it.end }

    /** Hito de Go-Live / paso a producción, si el plan lo tiene. */
    val goLiveMilestone: Task?
        get() = tasks.filter { it.isMilestone }.lastOrNull { t ->
            val n = t.name.lowercase()
            "go-live" in n || "go live" in n || "paso a producción" in n || "puesta en marcha" in n || "arranque" in n
        }

    /** Go-Live según el plan de tareas: el hito si existe; si no, el fin calculado. */
    val projectedGoLive: LocalDate? get() = goLiveMilestone?.start ?: calculatedEnd

    /** Días de desvío del Go-Live previsto respecto al planificado (positivo = retraso). */
    val goLiveDeviationDays: Long get() = projectedGoLive?.let { daysBetween(goLiveDate, it) } ?: 0

    fun phaseOf(task: Task): PhaseDef? = phases.firstOrNull { it.id == task.phaseId }

    fun colorIndexOf(task: Task): Int = phaseOf(task)?.colorIndex ?: task.phase.ordinal

    /**
     * Garantiza que el proyecto tiene lista de fases (formato actual). Convierte los datos antiguos:
     * grupos importados de MS Project o fases LIS fijas pasan a ser fases editables.
     */
    fun normalized(): Project {
        if (phases.isNotEmpty() || tasks.isEmpty()) return this
        return if (usesCustomGroups) {
            val names = tasks.map { it.group.ifBlank { "General" } }.distinct()
            val defs = names.mapIndexed { i, n -> PhaseDef(name = n, colorIndex = i) }
            val byName = defs.associateBy { it.name }
            copy(phases = defs, tasks = tasks.map { it.copy(phaseId = byName.getValue(it.group.ifBlank { "General" }).id) })
        } else {
            val defs = tasks.map { it.phase }.distinct().sorted().associateWith { PhaseDef(name = it.label, colorIndex = it.ordinal) }
            copy(phases = defs.values.toList(), tasks = tasks.map { it.copy(phaseId = defs.getValue(it.phase).id) })
        }
    }
}

/** Grupo de tareas para Gantt, listas e informes: una fase LIS o una tarea resumen importada. */
data class TaskGroup(
    val key: String,
    val label: String,
    val short: String,
    /** Índice en la paleta de colores. */
    val colorIndex: Int,
    val tasks: List<Task>,
) {
    val start: LocalDate get() = tasks.minOf { it.start }
    val end: LocalDate get() = tasks.maxOf { it.end }
    val estimatedHours: Double get() = tasks.sumOf { it.estimatedHours }
    val spentHours: Double get() = tasks.sumOf { it.spentHours }

    /** Avance ponderado por duración; si solo hay hitos, 0 o 100 %. */
    val progress: Float
        get() {
            val work = tasks.filterNot { it.isMilestone }
            val total = work.sumOf { it.durationDays }
            if (total == 0L) return if (tasks.all { it.progress >= 100 }) 1f else 0f
            return work.sumOf { it.durationDays * it.progress } / (total * 100f)
        }
}

/** Deduce la fase LIS a partir del nombre de una tarea (para colores y clasificación de tareas importadas). */
fun guessPhase(name: String): LisPhase {
    val n = name.lowercase().withoutAccents()
    fun has(vararg k: String) = k.any { it in n }
    return when {
        has("kick", "gestion de proyecto", "planificacion", "plan de proyecto", "cronograma", "comite") -> LisPhase.KICKOFF
        has("analisis", "levantamiento", "requisit", "toma de datos") -> LisPhase.ANALISIS
        has("conectiv", "analizador", "middleware", "driver", "equipos", "instrumento") -> LisPhase.ANALIZADORES
        has("interfaz", "interface", "hl7", "his ", "integracion", "mensajeria") -> LisPhase.INTERFACES
        has("migracion", "historico") -> LisPhase.MIGRACION
        has("prueba", "validacion", "uat", "test") -> LisPhase.VALIDACION
        has("formacion", "manual", "curso") -> LisPhase.FORMACION
        has("arranque", "go-live", "go live", "puesta en marcha", "simulacro", "contingencia") -> LisPhase.ARRANQUE
        has("estabiliz", "soporte", "mantenimiento", "hypercare", "cierre", "post") -> LisPhase.SOPORTE
        else -> LisPhase.CONFIGURACION
    }
}
