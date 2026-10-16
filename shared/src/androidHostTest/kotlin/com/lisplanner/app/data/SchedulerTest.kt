package com.lisplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.datetime.LocalDate

class SchedulerTest {
    private val monday = LocalDate(2026, 10, 12)

    @Test
    fun durationComesFromHoursAndDedication() {
        val a = Task(name = "A", phase = LisPhase.CONFIGURACION, start = monday, end = monday, estimatedHours = 40.0, autoSchedule = true)
        val b = Task(name = "B", phase = LisPhase.CONFIGURACION, start = monday, end = monday, estimatedHours = 20.0, dailyHours = 4.0, autoSchedule = true, dependsOn = listOf(a.id))
        val m = Task(name = "Hito", phase = LisPhase.ARRANQUE, start = monday, end = monday, isMilestone = true, autoSchedule = true, dependsOn = listOf(b.id))
        val p = Scheduler.reschedule(Project(name = "P", client = "C", startDate = monday, goLiveDate = monday.plusMonths(1), tasks = listOf(m, b, a)))
        val byName = p.tasks.associateBy { it.name }
        // A: 40 h a 8 h/día = 5 jornadas → lunes 12 a viernes 16
        assertEquals(LocalDate(2026, 10, 16), byName.getValue("A").end)
        // B: empieza el lunes siguiente; 20 h a 4 h/día = 5 jornadas → viernes 23
        assertEquals(LocalDate(2026, 10, 19), byName.getValue("B").start)
        assertEquals(LocalDate(2026, 10, 23), byName.getValue("B").end)
        // El hito cae el día en que termina B
        assertEquals(LocalDate(2026, 10, 23), byName.getValue("Hito").start)
    }

    @Test
    fun startedTasksKeepTheirActualStart() {
        val a = Task(name = "A", phase = LisPhase.CONFIGURACION, start = monday, end = monday, estimatedHours = 8.0)
        val b = Task(
            name = "B", phase = LisPhase.CONFIGURACION, start = monday.plusDays(14), end = monday.plusDays(14),
            estimatedHours = 16.0, progress = 30, autoSchedule = true, dependsOn = listOf(a.id),
        )
        val p = Scheduler.reschedule(Project(name = "P", client = "C", startDate = monday, goLiveDate = monday.plusMonths(1), tasks = listOf(a, b)))
        val rb = p.tasks.single { it.name == "B" }
        assertEquals(monday.plusDays(14), rb.start)
        assertEquals(monday.plusDays(15), rb.end)
    }

    @Test
    fun addingChainedTasksPushesProjectEnd() {
        val a = Task(name = "A", phase = LisPhase.CONFIGURACION, start = monday, end = monday, estimatedHours = 40.0, autoSchedule = true)
        val goLive = Task(name = "Go-Live", phase = LisPhase.ARRANQUE, start = monday, end = monday, isMilestone = true, autoSchedule = true, dependsOn = listOf(a.id))
        val p0 = Scheduler.reschedule(Project(name = "P", client = "C", startDate = monday, goLiveDate = LocalDate(2026, 10, 16), tasks = listOf(a, goLive)))
        assertEquals(LocalDate(2026, 10, 16), p0.calculatedEnd)
        assertEquals(0L, p0.goLiveDeviationDays)
        // Se añade B (24 h) detrás de A y el Go-Live pasa a depender de B: el plan se alarga 3 jornadas.
        val b = Task(name = "B", phase = LisPhase.CONFIGURACION, start = monday, end = monday, estimatedHours = 24.0, autoSchedule = true, dependsOn = listOf(a.id))
        val p1 = Scheduler.reschedule(p0.copy(tasks = p0.tasks.map { if (it.id == goLive.id) it.copy(dependsOn = listOf(b.id)) else it } + b))
        assertEquals(LocalDate(2026, 10, 21), p1.calculatedEnd)
        assertEquals(5L, p1.goLiveDeviationDays)
    }

    @Test
    fun manualTasksAreNotMoved() {
        val a = Task(name = "A", phase = LisPhase.CONFIGURACION, start = monday, end = monday.plusDays(10), estimatedHours = 8.0)
        val p = Scheduler.reschedule(Project(name = "P", client = "C", startDate = monday, goLiveDate = monday.plusMonths(1), tasks = listOf(a)))
        assertEquals(monday.plusDays(10), p.tasks.single().end)
    }
}
