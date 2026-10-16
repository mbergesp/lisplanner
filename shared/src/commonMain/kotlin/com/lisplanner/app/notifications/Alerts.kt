package com.lisplanner.app.notifications

import com.lisplanner.app.data.*
import com.lisplanner.app.ui.components.relativeDays
import com.lisplanner.app.ui.components.short
import kotlinx.datetime.LocalDate

@kotlinx.serialization.Serializable
data class ReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 30,
    /** Con cuántos días de antelación avisar de inicios y vencimientos. */
    val leadDays: Int = 2,
    val startSoon: Boolean = true,
    val dueSoon: Boolean = true,
    val overdue: Boolean = true,
    val milestones: Boolean = true,
    val risks: Boolean = true,
    /** Si no está vacío, solo se avisa de las tareas asignadas a esta persona. */
    val onlyAssignee: String = "",
)

/* ======================================================================
 *  Cálculo de avisos (función pura: se usa en el worker y en la vista previa)
 * ====================================================================== */

enum class AlertKind(val label: String) {
    ATRASADA("Atrasada"),
    VENCE("Vence pronto"),
    EMPIEZA("Empieza pronto"),
    HITO("Hito próximo"),
    RIESGO("Riesgo / incidencia"),
}

data class Alert(val projectId: String, val projectName: String, val kind: AlertKind, val title: String, val detail: String)

fun computeAlerts(projects: List<Project>, s: ReminderSettings, today: LocalDate = today()): List<Alert> {
    val horizon = today.plusDays(s.leadDays.toLong())
    fun LocalDate.inWindow() = !isBefore(today) && !isAfter(horizon)
    fun whenText(d: LocalDate) = "${relativeDays(daysBetween(today, d))} (${d.short()})"

    return projects.filter { it.status != ProjectStatus.COMPLETADO }.flatMap { p ->
        val mine = p.tasks.filter { it.progress < 100 && (s.onlyAssignee.isBlank() || it.assignee.equals(s.onlyAssignee, ignoreCase = true)) }
        buildList {
            if (s.overdue) mine.filter { it.end.isBefore(today) }.sortedBy { it.end }.forEach {
                add(Alert(p.id, p.name, AlertKind.ATRASADA, it.name, "Venció ${whenText(it.end)} · ${it.progress}%"))
            }
            if (s.milestones) mine.filter { it.isMilestone && it.start.inWindow() }.forEach {
                add(Alert(p.id, p.name, AlertKind.HITO, it.name, "Hito ${whenText(it.start)}"))
            }
            if (s.dueSoon) mine.filter { !it.isMilestone && it.end.inWindow() }.sortedBy { it.end }.forEach {
                add(Alert(p.id, p.name, AlertKind.VENCE, it.name, "Termina ${whenText(it.end)} · ${it.progress}%"))
            }
            if (s.startSoon) mine.filter { !it.isMilestone && it.start.inWindow() && it.progress == 0 }.sortedBy { it.start }.forEach {
                add(Alert(p.id, p.name, AlertKind.EMPIEZA, it.name, "Empieza ${whenText(it.start)}" + if (it.assignee.isNotBlank()) " · ${it.assignee}" else ""))
            }
            if (s.risks) p.risks.filter { it.status != RiskStatus.CERRADO && it.due != null && !it.due.isAfter(horizon) }
                .filter { s.onlyAssignee.isBlank() || it.owner.equals(s.onlyAssignee, ignoreCase = true) }
                .forEach { add(Alert(p.id, p.name, AlertKind.RIESGO, it.title, "${it.kind.label} · límite ${whenText(it.due!!)}")) }
        }
    }
}


/** Resumen corto de los avisos de un proyecto: «2 atrasadas · 1 vence pronto · …». */
fun alertSummary(list: List<Alert>): String = buildList {
    val overdue = list.count { it.kind == AlertKind.ATRASADA }
    if (overdue > 0) add("$overdue atrasada${if (overdue > 1) "s" else ""}")
    val due = list.count { it.kind == AlertKind.VENCE }
    if (due > 0) add("$due vence${if (due > 1) "n" else ""} pronto")
    val start = list.count { it.kind == AlertKind.EMPIEZA }
    if (start > 0) add("$start empieza${if (start > 1) "n" else ""}")
    val other = list.count { it.kind == AlertKind.HITO || it.kind == AlertKind.RIESGO }
    if (other > 0) add("$other hito${if (other > 1) "s" else ""}/riesgo${if (other > 1) "s" else ""}")
}.joinToString(" · ")
