package com.lisplanner.app.data

import kotlinx.datetime.LocalDate

/**
 * Exporta un proyecto al formato XML de Microsoft Project (MSPDI), que Project abre con
 * Archivo → Abrir → "Formato XML". Ninguna librería escribe .mpp; MSPDI es el formato de intercambio oficial.
 *
 * - Calendario estándar: lunes a viernes, 08:00–12:00 y 13:00–17:00 (8 h).
 * - Un nivel de tareas resumen por grupo (fase LIS o tarea resumen importada).
 * - Tareas de duración fija con restricción "No comenzar antes de" en su fecha de inicio,
 *   salvo las programadas por horas que tienen predecesoras (se dejan "Lo antes posible").
 * - Recursos y asignaciones a partir del responsable y, en tareas importadas con varios
 *   recursos, del reparto guardado en las notas ("Recursos: A (72 h), B (72 h)").
 */
object MspdiExporter {

    private data class Assign(val resource: String, val hours: Double, val actual: Double)

    private val resourcesLine = Regex("""^Recursos: (.+)$""", RegexOption.MULTILINE)

    fun write(project: Project): String {
        val groups = project.groups()
        val x = StringBuilder()
        fun tag(name: String, value: Any?) {
            if (value == null) return
            x.append('<').append(name).append('>').append(esc(value.toString())).append("</").append(name).append(">\n")
        }

        val start = (project.tasks.map { it.start } + project.startDate).min()
        val finish = (project.tasks.map { it.end } + project.goLiveDate).max()

        // ---- Recursos ----
        val assignmentsByTask = project.tasks.associate { it.id to assignmentsOf(it) }
        val resourceNames = (project.team.map { it.name } + assignmentsByTask.values.flatten().map { it.resource })
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val resourceUid = resourceNames.withIndex().associate { (i, n) -> n to i + 1 }

        x.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        x.append("""<Project xmlns="http://schemas.microsoft.com/project">""").append('\n')
        tag("SaveVersion", 14)
        tag("Name", "${project.name}.xml")
        tag("Title", project.name)
        tag("Company", project.client.ifBlank { null })
        tag("Manager", project.projectManager.ifBlank { null })
        tag("ScheduleFromStart", 1)
        tag("StartDate", dt(start, true))
        tag("FinishDate", dt(finish, false))
        tag("CalendarUID", 1)
        tag("DefaultStartTime", "08:00:00")
        tag("DefaultFinishTime", "17:00:00")
        tag("MinutesPerDay", 480)
        tag("MinutesPerWeek", 2400)
        tag("DaysPerMonth", 20)
        tag("DefaultTaskType", 1)

        // ---- Calendario estándar ----
        x.append("<Calendars>\n<Calendar>\n")
        tag("UID", 1); tag("Name", "Estándar"); tag("IsBaseCalendar", 1); tag("BaseCalendarUID", -1)
        x.append("<WeekDays>\n")
        for (day in 1..7) { // 1 = domingo … 7 = sábado
            x.append("<WeekDay>\n")
            tag("DayType", day)
            if (day == 1 || day == 7) {
                tag("DayWorking", 0)
            } else {
                tag("DayWorking", 1)
                x.append("<WorkingTimes>\n")
                x.append("<WorkingTime><FromTime>08:00:00</FromTime><ToTime>12:00:00</ToTime></WorkingTime>\n")
                x.append("<WorkingTime><FromTime>13:00:00</FromTime><ToTime>17:00:00</ToTime></WorkingTime>\n")
                x.append("</WorkingTimes>\n")
            }
            x.append("</WeekDay>\n")
        }
        x.append("</WeekDays>\n</Calendar>\n</Calendars>\n")

        // ---- Tareas ----
        val taskUid = mutableMapOf<String, Int>()
        var nextUid = 1
        // Fase a fase, en el orden manual de las tareas.
        val ordered = groups.map { g -> g to g.tasks }
        ordered.forEach { (_, tasks) ->
            nextUid++ // UID reservado para la tarea resumen del grupo
            tasks.forEach { taskUid[it.id] = nextUid++ }
        }

        x.append("<Tasks>\n")
        // Tarea resumen del proyecto (UID 0)
        x.append("<Task>\n")
        tag("UID", 0); tag("ID", 0); tag("Name", project.name); tag("Type", 1); tag("IsNull", 0)
        tag("OutlineNumber", 0); tag("OutlineLevel", 0)
        tag("Start", dt(start, true)); tag("Finish", dt(finish, false))
        tag("Duration", hoursDuration(WorkCalendar.workdays(start, finish) * 8.0)); tag("DurationFormat", 7)
        tag("Summary", 1)
        x.append("</Task>\n")

        var id = 1
        var uid = 1
        ordered.forEachIndexed { gi, (group, tasks) ->
            val gStart = tasks.minOf { it.start }
            val gEnd = tasks.maxOf { it.end }
            x.append("<Task>\n")
            tag("UID", uid++); tag("ID", id++); tag("Name", group.label); tag("Type", 1); tag("IsNull", 0)
            tag("OutlineNumber", gi + 1); tag("OutlineLevel", 1)
            tag("Start", dt(gStart, true)); tag("Finish", dt(gEnd, false))
            tag("Duration", hoursDuration(WorkCalendar.workdays(gStart, gEnd) * 8.0)); tag("DurationFormat", 7)
            tag("Summary", 1)
            tag("PercentComplete", (group.progress * 100).toInt())
            x.append("</Task>\n")

            tasks.forEachIndexed { ti, t ->
                val days = if (t.isMilestone) 0 else WorkCalendar.workdays(t.start, t.end)
                val asap = t.autoSchedule && t.dependsOn.isNotEmpty()
                x.append("<Task>\n")
                tag("UID", uid++); tag("ID", id++); tag("Name", t.name); tag("Type", 1); tag("IsNull", 0)
                tag("OutlineNumber", "${gi + 1}.${ti + 1}"); tag("OutlineLevel", 2)
                tag("Start", dt(t.start, true))
                tag("Finish", if (t.isMilestone) dt(t.start, true) else dt(t.end, false))
                tag("Duration", hoursDuration(days * 8.0)); tag("DurationFormat", 7)
                if (t.estimatedHours > 0) tag("Work", hoursDuration(t.estimatedHours))
                tag("EffortDriven", 0)
                tag("Milestone", if (t.isMilestone) 1 else 0)
                tag("Summary", 0)
                tag("PercentComplete", t.progress)
                if (t.spentHours > 0) tag("ActualWork", hoursDuration(t.spentHours))
                tag("ConstraintType", if (asap) 0 else 4)
                if (!asap) tag("ConstraintDate", dt(t.start, true))
                tag("Notes", stripResources(t.notes).ifBlank { null })
                t.dependsOn.mapNotNull { taskUid[it] }.forEach { p ->
                    x.append("<PredecessorLink><PredecessorUID>$p</PredecessorUID><Type>1</Type><CrossProject>0</CrossProject><LinkLag>0</LinkLag><LagFormat>7</LagFormat></PredecessorLink>\n")
                }
                x.append("</Task>\n")
            }
        }
        x.append("</Tasks>\n")

        // ---- Recursos ----
        x.append("<Resources>\n")
        resourceNames.forEach { name ->
            x.append("<Resource>\n")
            tag("UID", resourceUid.getValue(name)); tag("ID", resourceUid.getValue(name)); tag("Name", name); tag("Type", 1)
            project.team.firstOrNull { it.name == name }?.role?.let { tag("Group", it) }
            tag("MaxUnits", "1.00")
            x.append("</Resource>\n")
        }
        x.append("</Resources>\n")

        // ---- Asignaciones ----
        x.append("<Assignments>\n")
        var aUid = 1
        ordered.flatMap { it.second }.forEach { t ->
            val tu = taskUid.getValue(t.id)
            val days = if (t.isMilestone) 0 else WorkCalendar.workdays(t.start, t.end)
            assignmentsByTask.getValue(t.id).forEach { a ->
                val ru = resourceUid[a.resource] ?: return@forEach
                x.append("<Assignment>\n")
                tag("UID", aUid++); tag("TaskUID", tu); tag("ResourceUID", ru)
                if (a.actual > 0) tag("ActualWork", hoursDuration(a.actual))
                tag("Finish", if (t.isMilestone) dt(t.start, true) else dt(t.end, false))
                tag("Start", dt(t.start, true))
                tag("Units", formatDecimalUs(if (days > 0) a.hours / (days * 8.0) else 1.0, 2))
                tag("Work", hoursDuration(a.hours))
                x.append("</Assignment>\n")
            }
        }
        x.append("</Assignments>\n")
        x.append("</Project>\n")
        return x.toString()
    }

    /** Reparto de horas por recurso: el de las notas de importación si cuadra, si no todo al responsable. */
    private fun assignmentsOf(t: Task): List<Assign> {
        if (t.estimatedHours <= 0 && t.assignee.isBlank()) return emptyList()
        return Workload.shares(t).map { (name, share) -> Assign(name, t.estimatedHours * share, t.spentHours * share) }
    }

    private fun stripResources(notes: String) = notes.replace(resourcesLine, "").trim()

    private fun dt(d: LocalDate, start: Boolean) = "${d}T${if (start) "08:00:00" else "17:00:00"}"

    /** Duración ISO-8601 al estilo MSPDI: PT40H0M0S. */
    private fun hoursDuration(h: Double): String {
        val totalMin = kotlin.math.round(h * 60).toLong()
        return "PT${totalMin / 60}H${totalMin % 60}M0S"
    }

    private fun esc(s: String) = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (c.code < 0x20 && c != '\n' && c != '\r' && c != '\t') append(' ') else append(c)
            }
        }
    }
}
