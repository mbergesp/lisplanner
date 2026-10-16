package com.lisplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mpxj.ProjectFile
import org.mpxj.TimeUnit
import org.mpxj.mspdi.MSPDIReader
import java.io.File
import kotlinx.datetime.toKotlinLocalDate

class MspdiExporterTest {

    private fun reread(xml: String): ProjectFile = MSPDIReader().read(xml.byteInputStream())

    private fun hours(pf: ProjectFile) = pf.tasks.filter { it.summary != true && it.name != null }
        .sumOf { it.work?.convertUnits(TimeUnit.HOURS, pf.projectProperties)?.duration ?: 0.0 }

    private fun check(project: Project, label: String) {
        val xml = MspdiExporter.write(project)
        System.getenv("MSPDI_OUT")?.let { File(it, "$label.xml").writeText(xml) }
        val pf = reread(xml)
        val leaves = pf.tasks.filter { it.summary != true && it.name != null }
        val preds = leaves.sumOf { it.predecessors.size }
        println("$label: tareas=${leaves.size} resumen=${pf.tasks.count { it.summary == true }} recursos=${pf.resources.count { it.name != null }} " +
            "asignaciones=${pf.resourceAssignments.size} predecesoras=$preds horas=${"%.1f".format(hours(pf))}")
        leaves.take(4).forEach { println("   ${it.name} ${it.start} → ${it.finish} work=${it.work}") }

        assertEquals(project.tasks.size, leaves.size)
        assertEquals(project.tasks.sumOf { it.dependsOn.size }, preds)
        assertEquals(project.estimatedHours, hours(pf), 1.0)
        // Mismo orden que el exportador: fase a fase, en el orden manual del proyecto (hay nombres repetidos entre fases).
        val expected = project.groups().flatMap { g -> g.tasks }
        expected.zip(leaves.sortedBy { it.id }).filter { !it.first.isMilestone }.forEach { (t, m) ->
            assertEquals(t.name, m.name)
            assertEquals(t.name, t.start, m.start.toLocalDate().toKotlinLocalDate())
            assertEquals(t.name, t.end, m.finish.toLocalDate().toKotlinLocalDate())
        }
    }

    @Test
    fun sampleProjectRoundTrips() = check(SampleData.projects().first(), "ejemplo")

    @Test
    fun importedMppRoundTrips() {
        val path = System.getenv("MPP_SAMPLE")
        assumeTrue(path != null && File(path).exists())
        val project = File(path!!).inputStream().use { MppImporter.read(it, File(path).name) }.project
        check(project, "importado")
    }
}
