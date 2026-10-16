package com.lisplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDate

class TemplatesTest {
    private val start = LocalDate(2026, 11, 2)

    @Test
    fun upgradeTemplateIsConsistent() {
        val p = ProjectTemplates.apply(
            Project(name = "Upg", client = "C", startDate = start, goLiveDate = start.plusWeeks(12), type = ProjectType.ACTUALIZACION),
        )
        assertEquals(DefaultPhases.names, p.phases.map { it.name })
        assertTrue(p.tasks.all { t -> p.phases.any { it.id == t.phaseId } })
        assertTrue("Conflictos: ${p.dependencyConflicts().map { it.second.name }}", p.dependencyConflicts().isEmpty())
        val goLive = p.tasks.single { it.name == "Paso a producción" }
        assertEquals(start.plusWeeks(12), goLive.start)
        println("Actualización: ${p.tasks.size} tareas, ${p.estimatedHours} h, ${p.tasks.sumOf { it.dependsOn.size }} dependencias")
    }

    @Test
    fun implantationTemplateGetsEditablePhases() {
        val p = ProjectTemplates.apply(Project(name = "Imp", client = "C", startDate = start, goLiveDate = start.plusWeeks(24)))
        assertEquals(DefaultPhases.names, p.phases.map { it.name })
        // Ninguna fase por defecto se queda vacía con la plantilla de implantación.
        assertEquals(6, p.groups().size)
        p.groups().forEach { println("  ${it.label}: ${it.tasks.joinToString { t -> t.name }}") }
        assertTrue(p.tasks.all { it.phaseId.isNotEmpty() })
    }

    @Test
    fun newProjectWithoutTemplateGetsDefaultPhases() {
        assertEquals(listOf("Consultoría", "Sistemas", "Configuración", "Interoperabilidad", "Formación", "Despliegue"), DefaultPhases.create().map { it.name })
    }

    @Test
    fun legacyProjectsAreMigrated() {
        // Proyecto guardado con versiones anteriores: sin lista de fases.
        val legacy = SampleData.projects().first().copy(phases = emptyList(), tasks = SampleData.projects().first().tasks.map { it.copy(phaseId = "") })
        val n = legacy.normalized()
        assertTrue(n.phases.isNotEmpty())
        assertEquals(legacy.tasks.size, n.groups().sumOf { it.tasks.size })
        assertTrue(n.groups().none { it.key == "_sin_fase" })
    }
}
