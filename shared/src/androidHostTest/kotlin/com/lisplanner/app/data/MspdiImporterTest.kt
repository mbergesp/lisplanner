package com.lisplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Lector XML de MS Project (común a Android e iOS). */
class MspdiImporterTest {

    private fun compare(expected: Project, actual: Project) {
        assertEquals(expected.tasks.size, actual.tasks.size)
        assertEquals(expected.estimatedHours, actual.estimatedHours, 1.0)
        assertEquals(expected.tasks.sumOf { it.dependsOn.size }, actual.tasks.sumOf { it.dependsOn.size })
        assertEquals(expected.phases.map { it.name }, actual.phases.map { it.name })
        // Mismas tareas, fechas y horas en el mismo orden de fases
        val e = expected.groups().flatMap { g -> g.tasks.map { "${g.label}|${it.name}|${it.start}|${it.end}|${it.estimatedHours}|${it.isMilestone}" } }
        val a = actual.groups().flatMap { g -> g.tasks.map { "${g.label}|${it.name}|${it.start}|${it.end}|${it.estimatedHours}|${it.isMilestone}" } }
        assertEquals(e.sorted(), a.sorted())
    }

    @Test
    fun exportThenImportGivesSameProject() {
        val original = SampleData.projects().first()
        val back = MspdiImporter.read(MspdiExporter.write(original), "ejemplo.xml").project
        println("Ida y vuelta: ${back.tasks.size} tareas, ${back.phases.size} fases, ${back.estimatedHours} h, equipo ${back.team.size}")
        compare(original, back)
    }

    @Test
    fun xmlImportMatchesMppImport() {
        val path = System.getenv("MPP_SAMPLE")
        assumeTrue(path != null && File(path).exists())
        val fromMpp = File(path!!).inputStream().use { MppImporter.read(it, "cronograma.mpp") }.project.normalized()
        // MS Project guardaría el mismo plan como XML; aquí se usa el XML que genera la propia app.
        val preview = MspdiImporter.read(MspdiExporter.write(fromMpp), "cronograma.xml")
        println("Cronograma vía XML: ${preview.project.tasks.size} tareas, ${preview.dependencies} dependencias, ${preview.project.estimatedHours} h · avisos ${preview.warnings}")
        compare(fromMpp, preview.project)
        assertTrue(preview.project.team.size >= 10)
    }

    @Test
    fun parsesIsoDurations() {
        assertEquals(40.0, MspdiImporter.hours("PT40H0M0S"), 0.0)
        assertEquals(8.5, MspdiImporter.hours("PT8H30M0S"), 0.0)
        assertEquals(12.0, MspdiImporter.hours("P1DT4H"), 0.0)
        assertEquals(0.0, MspdiImporter.hours(null), 0.0)
    }
}
