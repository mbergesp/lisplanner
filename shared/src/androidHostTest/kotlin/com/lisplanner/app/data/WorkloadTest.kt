package com.lisplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlinx.datetime.LocalDate

class WorkloadTest {
    private val monday = LocalDate(2026, 10, 12)

    @Test
    fun sumsHoursPerPersonAndUpcomingWindow() {
        val tasks = listOf(
            // 80 h, mitad hecha, de lunes a viernes de la semana siguiente: todo cae en las próximas 4 semanas.
            Task(name = "A", phase = LisPhase.CONFIGURACION, start = monday, end = monday.plusDays(11), estimatedHours = 80.0, spentHours = 40.0, progress = 50, assignee = "Ana"),
            Task(name = "B", phase = LisPhase.CONFIGURACION, start = monday, end = monday.plusDays(4), estimatedHours = 20.0, assignee = "Luis"),
            Task(name = "C", phase = LisPhase.CONFIGURACION, start = monday, end = monday.plusDays(4), estimatedHours = 10.0),
        )
        val loads = Workload.compute(listOf(Project(name = "P", client = "C", startDate = monday, goLiveDate = monday.plusMonths(2), tasks = tasks)), monday)
        assertEquals(listOf("Ana", "Luis", Workload.UNASSIGNED), loads.map { it.name })
        val ana = loads.first()
        assertEquals(80.0, ana.assigned, 0.01)
        assertEquals(40.0, ana.pending, 0.01)
        assertEquals(40.0, ana.upcoming, 0.01)
        assertEquals(0.25, ana.upcomingLoad, 0.001)
    }

    @Test
    fun importedMultiResourceTasksAreSplit() {
        val path = System.getenv("MPP_SAMPLE")
        assumeTrue(path != null && File(path).exists())
        val p = File(path!!).inputStream().use { MppImporter.read(it, "x.mpp") }.project
        val loads = Workload.compute(listOf(p), LocalDate(2027, 3, 1))
        loads.forEach { println("  ${it.name}: ${"%.0f".format(it.assigned)} h · próximas 4 sem ${"%.0f".format(it.upcoming)} h (${(it.upcomingLoad * 100).toInt()}%)") }
        assertEquals(p.estimatedHours, loads.sumOf { it.assigned }, 1.0)
    }
}
