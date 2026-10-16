package com.lisplanner.app.data

import org.mpxj.ProjectFile
import org.mpxj.RelationType
import org.mpxj.TimeUnit
import org.mpxj.mpp.MPPReader
import java.io.InputStream
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlin.math.roundToLong

/**
 * Importa planificaciones de Microsoft Project (.mpp) con MPXJ.
 *
 * - Las tareas resumen se convierten en **grupos** (p. ej. un grupo por hospital).
 * - Las tareas hoja se importan con fechas, % completado, trabajo (horas estimadas),
 *   trabajo real (horas imputadas), recurso principal, notas y predecesoras.
 * - Los recursos asignados pasan a formar el equipo del proyecto.
 */
object MppImporter {

    fun read(input: InputStream, fileName: String): ImportPreview {
        val reader = MPPReader().apply {
            // Las vistas, tablas y estilos de barras usan java.awt, que no existe en Android.
            setReadPresentationData(false)
        }
        return convert(reader.read(input), fileName)
    }

    fun convert(pf: ProjectFile, fileName: String): ImportPreview {
        val props = pf.projectProperties
        val warnings = mutableListOf<String>()

        fun hours(d: org.mpxj.Duration?): Double {
            val h = d?.convertUnits(TimeUnit.HOURS, props)?.duration ?: 0.0
            return (h * 10).roundToLong() / 10.0
        }

        val all = pf.tasks.filter { it.name != null && it.start != null && it.finish != null }
        val leaves = all.filter { it.summary != true }
        val summaries = all.filter { it.summary == true && (it.outlineLevel ?: 0) >= 1 }
        if (pf.tasks.size - all.size > 0) {
            warnings += "${pf.tasks.size - all.size} filas vacías o sin fechas ignoradas."
        }

        // Nombre de grupo = tarea resumen padre. Si dos resúmenes se llaman igual se antepone el abuelo.
        fun parentSummary(t: org.mpxj.Task) = t.parentTask?.takeIf { (it.outlineLevel ?: 0) >= 1 }
        val nameCount = summaries.groupingBy { it.name.trim() }.eachCount()
        fun groupName(t: org.mpxj.Task): String {
            val parent = parentSummary(t) ?: return ""
            val name = parent.name.trim()
            if ((nameCount[name] ?: 0) <= 1) return name
            val grand = parentSummary(parent)?.name?.trim()
            return if (grand != null) "$grand › $name" else name
        }

        val idMap = leaves.associate { it.uniqueID to newId() }
        var deps = 0
        var nonFs = 0
        var lagged = 0

        val tasks = leaves.map { t ->
            val start = t.start.toLocalDate().toKotlinLocalDate()
            val finish = t.finish.toLocalDate().toKotlinLocalDate().let { if (it.isBefore(start)) start else it }
            val milestone = t.milestone == true
            val assignments = t.resourceAssignments
                .filter { it.resource?.name != null }
                .sortedByDescending { hours(it.work) }
            val preds = t.predecessors.orEmpty().mapNotNull { r ->
                val id = idMap[r.predecessorTask?.uniqueID] ?: return@mapNotNull null
                if (r.type != RelationType.FINISH_START) nonFs++
                if ((r.lag?.duration ?: 0.0) != 0.0) lagged++
                id
            }
            deps += preds.size
            val notes = buildList {
                t.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.take(1000)) }
                if (assignments.size > 1) {
                    add("Recursos: " + assignments.joinToString(", ") { "${it.resource.name.trim()} (${hours(it.work).toLong()} h)" })
                }
            }.joinToString("\n\n")
            Task(
                id = idMap.getValue(t.uniqueID),
                name = t.name.trim(),
                phase = guessPhase(t.name),
                start = start,
                end = if (milestone) start else finish,
                progress = t.percentageComplete?.toInt()?.coerceIn(0, 100) ?: 0,
                assignee = assignments.firstOrNull()?.resource?.name?.trim().orEmpty(),
                isMilestone = milestone,
                notes = notes,
                dependsOn = preds,
                estimatedHours = hours(t.work),
                spentHours = hours(t.actualWork),
                group = groupName(t),
            )
        }
        // Project permite asignar recursos a una tarea resumen: ese trabajo propio se importa como una tarea más del grupo.
        val summaryWork = summaries.filter { s -> s.resourceAssignments.any { it.resource?.name != null } }.map { s ->
            val own = s.resourceAssignments.filter { it.resource?.name != null }.sortedByDescending { hours(it.work) }
            Task(
                name = "${s.name.trim()} (trabajo de la tarea resumen)",
                phase = guessPhase(s.name),
                start = s.start.toLocalDate().toKotlinLocalDate(),
                end = s.finish.toLocalDate().toKotlinLocalDate(),
                progress = s.percentageComplete?.toInt()?.coerceIn(0, 100) ?: 0,
                assignee = own.first().resource.name.trim(),
                estimatedHours = own.sumOf { hours(it.work) },
                spentHours = own.sumOf { hours(it.actualWork) },
                group = s.name.trim(),
            )
        }
        if (summaryWork.isNotEmpty()) {
            warnings += "${summaryWork.size} tareas resumen con recursos propios se han importado como tareas."
        }

        if (nonFs > 0) warnings += "$nonFs dependencias que no son fin→inicio se han importado como fin→inicio."
        if (lagged > 0) warnings += "$lagged dependencias tenían adelanto o retraso (lag), que no se conserva."

        val team = pf.resources
            .filter { r -> r.name != null && r.taskAssignments.isNotEmpty() }
            .map { TeamMember(name = it.name.trim(), role = it.group?.takeIf { g -> g.isNotBlank() } ?: "Recurso de Project") }
            .distinctBy { it.name }

        val start = tasks.minOfOrNull { it.start } ?: today()
        val end = tasks.maxOfOrNull { it.end } ?: start.plusMonths(6)
        // Go-Live por defecto: el último "arranque" del plan (en despliegues por centros, el último centro).
        val goLive = tasks.filter { it.phase == LisPhase.ARRANQUE }.maxOfOrNull { it.end }
            ?.takeIf { it.isAfter(start) } ?: end.takeIf { it.isAfter(start) } ?: start.plusDays(1)
        val topSummary = summaries.filter { it.outlineLevel == 1 }.maxByOrNull { hours(it.work) }?.name?.trim()

        val project = Project(
            name = fileName.substringBeforeLast('.').trim(),
            client = props.company?.takeIf { it.isNotBlank() } ?: topSummary.orEmpty(),
            projectManager = props.manager.orEmpty(),
            startDate = start,
            goLiveDate = goLive,
            status = if (tasks.any { it.progress > 0 }) ProjectStatus.EN_CURSO else ProjectStatus.PLANIFICACION,
            description = "Importado de Microsoft Project ($fileName).",
            tasks = tasks + summaryWork,
            team = team,
        )
        return ImportPreview(
            project = project,
            fileName = fileName,
            sourceTitle = props.projectTitle?.takeIf { it.isNotBlank() },
            summaryTasks = summaries.size,
            dependencies = deps,
            warnings = warnings,
        )
    }
}
