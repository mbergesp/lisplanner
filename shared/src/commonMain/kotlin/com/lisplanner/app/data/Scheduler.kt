package com.lisplanner.app.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Calendario laboral: lunes a viernes. */
object WorkCalendar {
    fun isWorkday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /** El propio día si es laborable; si no, el siguiente laborable. */
    fun snap(d: LocalDate): LocalDate {
        var x = d
        while (!isWorkday(x)) x = x.plusDays(1)
        return x
    }

    fun nextWorkday(d: LocalDate) = snap(d.plusDays(1))

    /** Fecha de fin para [days] jornadas empezando en [start] (incluido). */
    fun finish(start: LocalDate, days: Int): LocalDate {
        var x = snap(start)
        repeat((days - 1).coerceAtLeast(0)) { x = nextWorkday(x) }
        return x
    }

    /** Jornadas laborables entre dos fechas, ambas incluidas (mínimo 1). */
    fun workdays(start: LocalDate, end: LocalDate): Int {
        var n = 0
        var x = start
        while (!x.isAfter(end)) {
            if (isWorkday(x)) n++
            x = x.plusDays(1)
        }
        return n.coerceAtLeast(1)
    }
}

/**
 * Programación por esfuerzo, al estilo de MS Project:
 *
 * - **Duración** = horas estimadas ÷ dedicación diaria, redondeado a jornadas completas.
 * - **Inicio** = siguiente día laborable tras el fin de la última predecesora;
 *   sin predecesoras se conserva el inicio indicado (ajustado a día laborable).
 * - Los hitos programados caen el mismo día en que termina su última predecesora.
 * - Las tareas manuales no se mueven, pero sí empujan a sus sucesoras automáticas.
 * - Las tareas ya empezadas (avance > 0) conservan su inicio real.
 */
object Scheduler {

    fun durationDays(hours: Double, dailyHours: Double): Int =
        if (hours <= 0 || dailyHours <= 0) 1 else ceil(hours / dailyHours - 1e-9).toInt().coerceAtLeast(1)

    /** Dedicación (h/día) que mantiene la duración actual de una tarea, redondeada a medias horas. */
    fun dedicationFor(task: Task): Double {
        val days = WorkCalendar.workdays(task.start, task.end)
        if (task.estimatedHours <= 0) return 8.0
        return ((task.estimatedHours / days) * 2).roundToInt().coerceAtLeast(1) / 2.0
    }

    fun reschedule(project: Project): Project {
        if (project.tasks.none { it.autoSchedule }) return project
        val byId = project.tasks.associateBy { it.id }.toMutableMap()
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in done || !visiting.add(id)) return // ya calculada, o ciclo de dependencias
            val t = byId[id]
            if (t == null) {
                visiting.remove(id)
                return
            }
            t.dependsOn.forEach { visit(it) }
            if (t.autoSchedule) {
                val preds = t.dependsOn.mapNotNull { byId[it] }
                val latestEnd = preds.maxOfOrNull { it.end }
                val updated = if (t.isMilestone) {
                    val date = if (t.progress >= 100) t.start else latestEnd ?: t.start
                    t.copy(start = date, end = date)
                } else {
                    // Una tarea ya empezada conserva su inicio real; solo se recalcula su fin.
                    val start = when {
                        t.progress > 0 -> t.start
                        latestEnd != null -> WorkCalendar.nextWorkday(latestEnd)
                        else -> WorkCalendar.snap(t.start)
                    }
                    t.copy(start = start, end = WorkCalendar.finish(start, durationDays(t.estimatedHours, t.dailyHours)))
                }
                byId[id] = updated
            }
            visiting.remove(id)
            done.add(id)
        }
        project.tasks.forEach { visit(it.id) }
        return project.copy(tasks = project.tasks.map { byId.getValue(it.id) })
    }
}
