package com.lisplanner.app.data

import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** Carga de trabajo de una persona. */
data class PersonLoad(
    val name: String,
    /** Horas estimadas de todas sus tareas. */
    val assigned: Double,
    /** Horas ya imputadas. */
    val spent: Double,
    /** Horas que faltan según el avance de cada tarea. */
    val pending: Double,
    /** Parte de las pendientes que cae en las próximas [Workload.HORIZON_DAYS] jornadas. */
    val upcoming: Double,
    val openTasks: Int,
    val projects: List<String>,
) {
    /** Ocupación en las próximas semanas respecto a la capacidad (1 = 100 %). */
    val upcomingLoad: Double get() = upcoming / Workload.CAPACITY_HOURS
}

object Workload {
    const val UNASSIGNED = "Sin asignar"
    const val HORIZON_DAYS = 20 // 4 semanas laborables
    const val HOURS_PER_DAY = 8.0
    const val CAPACITY_HOURS = HORIZON_DAYS * HOURS_PER_DAY

    private val resourcesLine = Regex("""^Recursos: (.+)$""", RegexOption.MULTILINE)
    private val resourceItem = Regex("""^(.+) \((\d+(?:[.,]\d+)?) h\)$""")

    /**
     * Reparto de una tarea entre personas (fracción de sus horas para cada una).
     * Las tareas importadas de MS Project con varios recursos guardan el reparto en sus notas.
     */
    fun shares(task: Task): List<Pair<String, Double>> {
        val parsed = resourcesLine.find(task.notes)?.groupValues?.get(1)
            ?.split(", ")?.mapNotNull { item ->
                resourceItem.matchEntire(item.trim())?.let { m -> m.groupValues[1].trim() to m.groupValues[2].replace(',', '.').toDouble() }
            }
        if (parsed != null && parsed.size > 1 && task.estimatedHours > 0 &&
            abs(parsed.sumOf { it.second } - task.estimatedHours) <= parsed.size + 0.5
        ) {
            val total = parsed.sumOf { it.second }
            return parsed.map { (name, h) -> name to if (total > 0) h / total else 1.0 / parsed.size }
        }
        return listOf(task.assignee.ifBlank { UNASSIGNED } to 1.0)
    }

    /** Horas pendientes de la tarea que caen en las próximas [HORIZON_DAYS] jornadas (reparto uniforme). */
    private fun upcomingHours(task: Task, pending: Double, today: LocalDate): Double {
        if (pending <= 0 || task.end.isBefore(today)) {
            // Atrasada: lo pendiente se considera trabajo inmediato.
            return if (pending > 0) pending else 0.0
        }
        val from = if (task.start.isAfter(today)) task.start else today
        val horizonEnd = WorkCalendar.finish(WorkCalendar.snap(today), HORIZON_DAYS)
        if (from.isAfter(horizonEnd)) return 0.0
        val remainingDays = WorkCalendar.workdays(from, task.end)
        val inHorizon = WorkCalendar.workdays(from, if (task.end.isAfter(horizonEnd)) horizonEnd else task.end)
        return pending * inHorizon / remainingDays
    }

    fun compute(projects: List<Project>, today: LocalDate = today()): List<PersonLoad> {
        data class Acc(var assigned: Double = 0.0, var spent: Double = 0.0, var pending: Double = 0.0, var upcoming: Double = 0.0, var open: Int = 0, val projects: MutableSet<String> = linkedSetOf())
        val acc = linkedMapOf<String, Acc>()
        projects.forEach { p ->
            p.tasks.filterNot { it.isMilestone }.forEach { t ->
                val pending = t.estimatedHours * (100 - t.progress) / 100.0
                val upcoming = upcomingHours(t, pending, today)
                shares(t).forEach { (name, f) ->
                    val a = acc.getOrPut(name) { Acc() }
                    a.assigned += t.estimatedHours * f
                    a.spent += t.spentHours * f
                    a.pending += pending * f
                    a.upcoming += upcoming * f
                    if (t.progress < 100) a.open++
                    a.projects += p.client.ifBlank { p.name }
                }
            }
        }
        return acc.filter { (name, a) -> a.assigned > 0 || (a.open > 0 && name != UNASSIGNED) }
            .map { (name, a) -> PersonLoad(name, a.assigned, a.spent, a.pending, a.upcoming, a.open, a.projects.toList()) }
            .sortedWith(compareBy<PersonLoad> { it.name == UNASSIGNED }.thenByDescending { it.assigned })
    }
}
