package com.lisplanner.app.data

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Importa un .mpp real indicado en la variable de entorno MPP_SAMPLE
 * (no se incluye en el repositorio para no versionar planificaciones de clientes).
 */
class MppImporterTest {
    @Test
    fun importsSampleFile() {
        val path = System.getenv("MPP_SAMPLE")
        assumeTrue("MPP_SAMPLE no definido", path != null && File(path).exists())
        val file = File(path!!)
        val preview = file.inputStream().use { MppImporter.read(it, file.name) }
        val p = preview.project

        println("Proyecto: ${p.name} | cliente=${p.client} | ${p.startDate} → go-live ${p.goLiveDate}")
        println("Tareas=${p.tasks.size} resumen=${preview.summaryTasks} deps=${preview.dependencies} equipo=${p.team.size}")
        println("Horas estimadas=${p.estimatedHours} imputadas=${p.spentHours}")
        p.groups().forEach { g -> println("  [${g.colorIndex}] ${g.label}: ${g.tasks.size} tareas, ${g.estimatedHours} h, ${g.start} → ${g.end}") }
        p.tasks.take(12).forEach { println("    ${it.group} / ${it.name} [${it.phase}] ${it.start}→${it.end} ${it.estimatedHours}h resp=${it.assignee} deps=${it.dependsOn.size}") }
        preview.warnings.forEach { println("  AVISO: $it") }

        assertTrue(p.tasks.isNotEmpty())
        assertTrue(p.usesCustomGroups)
        assertTrue(p.estimatedHours > 0)
    }
}
