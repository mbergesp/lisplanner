package com.lisplanner.app.export

import com.lisplanner.app.data.*
import com.lisplanner.app.export.pdf.*
import kotlin.math.PI
import kotlin.math.roundToInt

import androidx.compose.ui.graphics.toArgb
import com.lisplanner.app.data.IntegrationStatus
import com.lisplanner.app.data.Project
import com.lisplanner.app.data.Severity
import com.lisplanner.app.data.Task
import com.lisplanner.app.data.TaskStatus
import com.lisplanner.app.ui.components.long
import com.lisplanner.app.ui.components.short
import com.lisplanner.app.ui.components.hoursLabel
import com.lisplanner.app.ui.theme.PhaseColorsLight
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Informes PDF comunes a Android e iOS (generador propio en [com.lisplanner.app.export.pdf]).
 * Unidades en puntos PostScript: A4 = 595 × 842.
 */
object Reports {
    private const val A4_W = 595
    private const val A4_H = 842
    private const val MARGIN = 40f

    private val PRIMARY = 0xFF0B5C7A.toInt()
    private val SECONDARY = 0xFF16867A.toInt()
    private val TEXT = 0xFF181C20.toInt()
    private val MUTED = 0xFF5F676D.toInt()
    private val LINE = 0xFFD6DCE2.toInt()
    private val TRACK = 0xFFE7ECF0.toInt()
    private val ERROR = 0xFFB3261E.toInt()
    private val TODAY = 0xFFB4610B.toInt()
    private val GREEN = 0xFF1B6E3A.toInt()
    private val AMBER = 0xFF8A4B00.toInt()
    private val ORANGE = 0xFFB3500B.toInt()

    private fun groupColor(index: Int) = PhaseColorsLight[index % PhaseColorsLight.size].toArgb()

    private fun text(size: Float, color: Int = TEXT, bold: Boolean = false) = Paint(color, PaintStyle.FILL, textSize = size, bold = bold)

    private fun fill(color: Int) = Paint(color, PaintStyle.FILL)
    private fun stroke(color: Int, width: Float = 1f) = Paint(color, PaintStyle.STROKE, width)

    private fun PdfCanvas.textFit(s: String, x: Float, y: Float, maxWidth: Float, paint: Paint) =
        drawText(Fonts.ellipsize(s, paint, maxWidth), x, y, paint)

    fun fileName(project: Project, kind: String, ext: String = "pdf"): String {
        val slug = project.client.ifBlank { project.name }.lowercase()
            .replace(Regex("[^a-z0-9áéíóúñ]+"), "-").trim('-').take(40)
        return "${kind}_${slug}_${today()}.$ext"
    }

    /* ======================================================================
     *  Informe de estado
     * ====================================================================== */

    /** Escritor con salto de página automático. */
    private class Writer(val doc: PdfDocument, val project: Project, val footer: String) {
        var pageNo = 0
        lateinit var page: PdfPage
        val canvas: PdfCanvas get() = page.canvas
        var y = 0f
        val bottom = A4_H - MARGIN - 20f
        val width = A4_W - MARGIN * 2

        fun newPage() {
            if (pageNo > 0) finishPage()
            pageNo++
            page = doc.startPage(A4_W, A4_H)
            y = MARGIN
        }

        fun finishPage() {
            val p = text(8f, MUTED)
            canvas.drawLine(MARGIN, A4_H - MARGIN, A4_W - MARGIN, A4_H - MARGIN, stroke(LINE, 0.5f))
            canvas.drawText(footer, MARGIN, A4_H - MARGIN + 12f, p)
            val n = "Página $pageNo"
            canvas.drawText(n, A4_W - MARGIN - p.measureText(n), A4_H - MARGIN + 12f, p)
        }

        fun ensure(h: Float) {
            if (y + h > bottom) newPage()
        }

        fun section(title: String) {
            // Reserva sitio para el título y al menos un par de filas, para no dejarlo solo al pie de página.
            ensure(90f)
            y += 14f
            canvas.drawRect(MARGIN, y, MARGIN + 3f, y + 14f, fill(PRIMARY))
            canvas.drawText(title, MARGIN + 10f, y + 11.5f, text(12f, PRIMARY, bold = true))
            y += 24f
        }

        fun paragraph(s: String, size: Float = 9.5f, color: Int = TEXT) {
            val paint = text(size, color)
            val lines = Fonts.wrap(s, paint, width)
            val lineH = size * 1.2f + 2f
            ensure(lines.size * lineH)
            lines.forEachIndexed { i, line -> canvas.drawText(line, MARGIN, y + size + i * lineH, paint) }
            y += lines.size * lineH + 4f
        }

        /** Tabla simple: columnas con anchura relativa, cabecera gris y filas con separador. */
        fun table(headers: List<String>, weights: List<Float>, rows: List<List<String>>, rowColor: (Int, Int) -> Int? = { _, _ -> null }) {
            val total = weights.sum()
            val widths = weights.map { it / total * width }
            fun xs() = widths.runningFold(MARGIN) { acc, w -> acc + w }
            val rh = 17f
            fun header() {
                canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + width, y + rh), 3f, 3f, fill(0xFFF0F3F7.toInt()))
                headers.forEachIndexed { i, h -> canvas.textFit(h, xs()[i] + 5f, y + 11.5f, widths[i] - 8f, text(8f, MUTED, bold = true)) }
                y += rh
            }
            ensure(rh * 2)
            header()
            rows.forEachIndexed { r, cells ->
                if (y + rh > bottom) { newPage(); header() }
                cells.forEachIndexed { c, cell ->
                    canvas.textFit(cell, xs()[c] + 5f, y + 11.5f, widths[c] - 8f, text(8.5f, rowColor(r, c) ?: TEXT))
                }
                y += rh
                canvas.drawLine(MARGIN, y, MARGIN + width, y, stroke(LINE, 0.5f))
            }
            y += 4f
        }
    }

    /* ---------- Cuadro de mando (mismos gráficos que la pestaña Resumen) ---------- */

    private val TL_RED = 0xFFE53935.toInt()
    private val TL_YELLOW = 0xFFFDD835.toInt()
    private val TL_GREEN = 0xFF43A047.toInt()
    private val DONUT_TRACK = 0xFFCFE0F3.toInt()

    private fun statusDashboard(w: Writer, project: Project, today: LocalDate) {
        w.section("Estado del proyecto")
        w.ensure(130f)
        val c = w.canvas
        val top = w.y

        // --- Velocímetro: la aguja marca el avance real (0 % rojo → 100 % verde) ---
        val gw = 170f
        val cx = MARGIN + gw / 2
        val stroke = 13f
        val radius = gw / 2 - stroke
        val cy = top + radius + stroke + 12f
        c.drawText("Estado general", cx - text(9.5f, bold = true).measureText("Estado general") / 2, top + 8f, text(9.5f, bold = true))
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 180f / 10
        for (i in 0 until 10) {
            val col = when { i < 4 -> TL_RED; i < 7 -> TL_YELLOW; else -> TL_GREEN }
            c.drawArc(oval, 180f + i * sweep + 1.5f, sweep - 3f, false, stroke(col, stroke).apply { strokeCap = Cap.BUTT })
        }
        val angle = ((180f + 180f * project.progress.coerceIn(0f, 1f)) * PI / 180.0)
        val needleLen = radius - stroke
        val nx = cx + needleLen * kotlin.math.cos(angle).toFloat()
        val ny = cy + needleLen * kotlin.math.sin(angle).toFloat()
        c.drawLine(cx, cy, nx, ny, stroke(TEXT, 4f).apply { strokeCap = Cap.ROUND })
        c.drawOval(RectF(cx - 12f, cy - 8f, cx + 12f, cy + 8f), fill(TEXT))
        val pct = "${(project.progress * 100).toInt()}% completado"
        val pp = text(11f, bold = true)
        c.drawText(pct, cx - pp.measureText(pct) / 2, cy + 24f, pp)
        val health = project.scheduleHealth(today)
        val hColor = when (health) {
            com.lisplanner.app.data.ScheduleHealth.RETRASADO -> TL_RED
            com.lisplanner.app.data.ScheduleHealth.LIGERO_RETRASO -> TL_YELLOW
            com.lisplanner.app.data.ScheduleHealth.EN_PLAZO -> TL_GREEN
        }
        val hl = "${health.label} · previsto hoy ${(project.expectedProgress(today) * 100).toInt()}%"
        val hp = text(8f, MUTED)
        val hx = cx - (hp.measureText(hl) + 12f) / 2
        c.drawRect(hx, cy + 31f, hx + 8f, cy + 39f, fill(hColor))
        c.drawText(hl, hx + 12f, cy + 38.5f, hp)

        // --- Barra de avance de actividades ---
        val bx = MARGIN + gw + 30f
        val bw = MARGIN + w.width - bx
        c.drawText("Avance de actividades", bx, top + 8f, text(9.5f, bold = true))
        val total = project.tasks.size.coerceAtLeast(1)
        val parts = listOf(
            Triple("No iniciado", project.tasks.count { it.progress == 0 }, TL_RED),
            Triple("En proceso", project.tasks.count { it.progress in 1..99 }, TL_YELLOW),
            Triple("Completado", project.tasks.count { it.progress >= 100 }, TL_GREEN),
        )
        val by = top + 22f
        var x = bx
        parts.filter { it.second > 0 }.forEach { (_, n, col) ->
            val segW = bw * n / total
            c.drawRect(x, by, x + segW, by + 24f, fill(col))
            if (n * 100 / total >= 7) {
                val label = "${(n * 100f / total).roundToInt()}%"
                val lp = text(10f, 0xFF000000.toInt(), bold = true)
                c.drawText(label, x + (segW - lp.measureText(label)) / 2, by + 16f, lp)
            }
            x += segW
        }
        var lx = bx
        parts.forEach { (label, n, col) ->
            c.drawRect(lx, by + 34f, lx + 9f, by + 43f, fill(col))
            val t = "$label ($n)"
            c.drawText(t, lx + 13f, by + 42f, text(8f, MUTED))
            lx += 13f + text(8f).measureText(t) + 16f
        }
        w.y = cy + 50f

        // --- Anillos de avance por fase ---
        val phases = project.phases
        if (phases.isNotEmpty()) {
            val cols = 6
            val cellW = w.width / cols
            val ringR = 24f
            val rowH = 92f
            phases.chunked(cols).forEach { row ->
                w.ensure(rowH)
                val rc = w.canvas
                row.forEachIndexed { i, p ->
                    val tasks = project.tasks.filter { it.phaseId == p.id }
                    val work = tasks.filterNot { it.isMilestone }
                    val dur = work.sumOf { it.durationDays }
                    val prog = when {
                        tasks.isEmpty() -> 0f
                        dur == 0L -> if (tasks.all { it.progress >= 100 }) 1f else 0f
                        else -> work.sumOf { it.durationDays * it.progress } / (dur * 100f)
                    }
                    val ccx = MARGIN + cellW * i + cellW / 2
                    val name = Fonts.ellipsize(p.name, text(8.5f, bold = true), cellW - 6f)
                    val np = text(8.5f, PRIMARY, bold = true)
                    rc.drawText(name, ccx - np.measureText(name) / 2, w.y + 10f, np)
                    val ccy = w.y + 20f + ringR + 6f
                    val ro = RectF(ccx - ringR, ccy - ringR, ccx + ringR, ccy + ringR)
                    rc.drawArc(ro, 0f, 360f, false, stroke(DONUT_TRACK, 9f))
                    if (prog > 0f) rc.drawArc(ro, -90f, 360f * prog, false, stroke(TL_GREEN, 9f))
                    val t = if (tasks.isEmpty()) "—" else "${(prog * 100).toInt()}%"
                    val tp = text(10f, bold = true)
                    rc.drawText(t, ccx - tp.measureText(t) / 2, ccy + 3.5f, tp)
                }
                w.y += rowH
            }
        }
    }

    fun statusReport(project: Project): ByteArray {
        val today = today()
        val doc = PdfDocument()
        val w = Writer(doc, project, "LIS Planner · Informe de estado · ${project.name}")
        w.newPage()
        val c = w.canvas

        // ---- Cabecera ----
        val headH = 96f
        c.drawRoundRect(RectF(MARGIN, w.y, A4_W - MARGIN, w.y + headH), 12f, 12f, fill(PRIMARY))
        c.drawText("INFORME DE ESTADO", MARGIN + 18f, w.y + 24f, text(8.5f, 0xCCFFFFFF.toInt(), bold = true))
        c.textFit(project.name, MARGIN + 18f, w.y + 48f, w.width - 150f, text(17f, 0xFFFFFFFF.toInt(), bold = true))
        c.textFit(listOf(project.client, project.site, project.lisSystem).filter { it.isNotBlank() }.joinToString(" · "), MARGIN + 18f, w.y + 66f, w.width - 150f, text(9.5f, 0xE6FFFFFF.toInt()))
        c.textFit("Jefe de proyecto: ${project.projectManager.ifBlank { "—" }}", MARGIN + 18f, w.y + 82f, w.width - 150f, text(8.5f, 0xCCFFFFFF.toInt()))
        val dateStr = today.format(EsDateFormat("d 'de' MMMM yyyy"))
        val dp = text(9f, 0xFFFFFFFF.toInt(), bold = true)
        c.drawText(dateStr, A4_W - MARGIN - 18f - dp.measureText(dateStr), w.y + 24f, dp)
        val statusTxt = project.status.label.uppercase()
        val sp = text(8.5f, PRIMARY, bold = true)
        val sw = sp.measureText(statusTxt) + 16f
        c.drawRoundRect(RectF(A4_W - MARGIN - 18f - sw, w.y + 36f, A4_W - MARGIN - 18f, w.y + 54f), 9f, 9f, fill(0xFFFFFFFF.toInt()))
        c.drawText(statusTxt, A4_W - MARGIN - 18f - sw + 8f, w.y + 48.5f, sp)
        w.y += headH + 16f

        // ---- Indicadores ----
        val done = project.tasks.count { it.progress >= 100 }
        val overdue = project.overdueTasks(today)
        val openRisks = project.openRisks()
        val kpis = listOf(
            Triple("Avance", "${(project.progress * 100).toInt()}%", "ponderado por duración"),
            Triple("Go-Live", project.goLiveDate.short(), "${project.daysToGoLive(today)} días"),
            Triple("Tareas", "$done / ${project.tasks.size}", "completadas"),
            Triple("Atrasadas", "${overdue.size}", if (overdue.isEmpty()) "al día" else "a resolver"),
            Triple("Riesgos", "${openRisks.size}", "${openRisks.count { it.severity >= Severity.ALTA }} altos o críticos"),
            Triple("Esfuerzo", hoursLabel(project.estimatedHours), "${hoursLabel(project.spentHours)} imputadas"),
        )
        val kw = (w.width - 8f * (kpis.size - 1)) / kpis.size
        kpis.forEachIndexed { i, (label, value, cap) ->
            val x = MARGIN + i * (kw + 8f)
            c.drawRoundRect(RectF(x, w.y, x + kw, w.y + 58f), 8f, 8f, stroke(LINE))
            c.drawText(label, x + 10f, w.y + 15f, text(8f, MUTED, bold = true))
            val alert = (label == "Atrasadas" && overdue.isNotEmpty()) || (label == "Riesgos" && openRisks.any { it.severity >= Severity.ALTA })
            c.textFit(value, x + 10f, w.y + 36f, kw - 16f, text(16f, if (alert) ERROR else TEXT, bold = true))
            c.textFit(cap, x + 10f, w.y + 50f, kw - 16f, text(7.5f, MUTED))
        }
        w.y += 66f

        // Barra global de avance
        val barW = w.width
        c.drawRoundRect(RectF(MARGIN, w.y, MARGIN + barW, w.y + 6f), 3f, 3f, fill(TRACK))
        c.drawRoundRect(RectF(MARGIN, w.y, MARGIN + barW * project.progress, w.y + 6f), 3f, 3f, fill(SECONDARY))
        w.y += 10f

        statusDashboard(w, project, today)

        if (project.description.isNotBlank()) {
            w.section("Alcance")
            w.paragraph(project.description)
        }

        // ---- Fases ----
        val groupWord = "Fase"
        w.section("Avance por ${groupWord.lowercase()}")
        project.groups().forEach { g ->
            val p = g.progress
            val tasks = g.tasks
            w.ensure(20f)
            val cy = w.y + 8f
            w.canvas.drawCircle(MARGIN + 4f, cy, 3.5f, fill(groupColor(g.colorIndex)))
            w.canvas.textFit(g.label, MARGIN + 14f, cy + 3.5f, 170f, text(9f))
            w.canvas.drawText("${tasks.minOf { it.start }.short()} – ${tasks.maxOf { it.end }.short()}", MARGIN + 190f, cy + 3.5f, text(8f, MUTED))
            val bx = MARGIN + 290f
            val bw = w.width - 290f - 40f
            w.canvas.drawRoundRect(RectF(bx, cy - 3f, bx + bw, cy + 3f), 3f, 3f, fill(TRACK))
            w.canvas.drawRoundRect(RectF(bx, cy - 3f, bx + bw * p, cy + 3f), 3f, 3f, fill(groupColor(g.colorIndex)))
            val pct = "${(p * 100).toInt()}%"
            val pp = text(9f, bold = true)
            w.canvas.drawText(pct, MARGIN + w.width - pp.measureText(pct), cy + 3.5f, pp)
            w.y += 18f
        }

        // ---- Esfuerzo ----
        if (project.estimatedHours > 0) {
            w.section("Esfuerzo (horas)")
            val phases = project.groups().filter { it.estimatedHours > 0 }
            val rowsH = phases.map { ph ->
                val tasks = ph.tasks
                val e = tasks.sumOf { it.estimatedHours }
                val s = tasks.sumOf { it.spentHours }
                val r = tasks.sumOf { it.estimatedHours * (100 - it.progress) / 100.0 }
                listOf(ph.label, hoursLabel(e), hoursLabel(s), hoursLabel(r), if (e > 0) "${(s / e * 100).toInt()}%" else "—")
            } + listOf(listOf("TOTAL", hoursLabel(project.estimatedHours), hoursLabel(project.spentHours), hoursLabel(project.remainingHours), "${(project.spentHours / project.estimatedHours * 100).toInt()}%"))
            w.table(
                listOf(groupWord, "Estimadas", "Imputadas", "Pendientes", "Consumo"), listOf(2.6f, 1f, 1f, 1f, 0.8f), rowsH,
                rowColor = { r, col ->
                    when {
                        r == rowsH.lastIndex -> PRIMARY
                        col == 2 && r < phases.size && phases[r].spentHours > phases[r].estimatedHours -> ERROR
                        else -> null
                    }
                },
            )
        }

        // ---- Hitos ----
        val milestones = project.tasks.filter { it.isMilestone }.sortedBy { it.start }
        if (milestones.isNotEmpty()) {
            w.section("Hitos")
            w.table(
                listOf("Hito", "Fase", "Fecha", "Estado"), listOf(3f, 1.6f, 1.2f, 1f),
                milestones.map { m ->
                    listOf(m.name, (project.phaseOf(m)?.name ?: m.phase.short), m.start.long(), when { m.progress >= 100 -> "Cumplido"; m.start.isBefore(today) -> "Vencido"; else -> "Pendiente" })
                },
                rowColor = { r, col ->
                    val m = milestones[r]
                    if (col == 3) when { m.progress >= 100 -> GREEN; m.start.isBefore(today) -> ERROR; else -> MUTED } else null
                },
            )
        }

        // ---- Atrasadas ----
        w.section("Tareas atrasadas (${overdue.size})")
        if (overdue.isEmpty()) {
            w.paragraph("No hay tareas atrasadas.", color = MUTED)
        } else {
            w.table(
                listOf("Tarea", "Fase", "Fin previsto", "Retraso", "Responsable", "Avance"), listOf(3f, 1.3f, 1.2f, 0.8f, 1.5f, 0.7f),
                overdue.sortedBy { it.end }.map { t ->
                    listOf(t.name, (project.phaseOf(t)?.name ?: t.phase.short), t.end.long(), "${daysBetween(t.end, today)} d", t.assignee.ifBlank { "—" }, "${t.progress}%")
                },
                rowColor = { _, col -> if (col == 3) ERROR else null },
            )
        }

        // ---- Conflictos de dependencias ----
        val conflicts = project.dependencyConflicts()
        if (conflicts.isNotEmpty()) {
            w.section("Conflictos de planificación (${conflicts.size})")
            w.table(
                listOf("Tarea", "Empieza", "Depende de", "Termina"), listOf(2.5f, 1f, 2.5f, 1f),
                conflicts.map { (pred, succ) -> listOf(succ.name, succ.start.long(), pred.name, pred.end.long()) },
                rowColor = { _, col -> if (col == 1) ERROR else null },
            )
        }

        // ---- Próximas 4 semanas ----
        val upcoming = project.tasks.filter { it.progress < 100 && !it.start.isAfter(today.plusWeeks(4)) && !it.end.isBefore(today) }.sortedBy { it.start }
        if (upcoming.isNotEmpty()) {
            w.section("Próximas 4 semanas")
            w.table(
                listOf("Tarea", "Fase", "Inicio", "Fin", "Responsable", "Avance"), listOf(3f, 1.3f, 1f, 1f, 1.5f, 0.7f),
                upcoming.map { t -> listOf(t.name, (project.phaseOf(t)?.name ?: t.phase.short), t.start.short(), t.end.short(), t.assignee.ifBlank { "—" }, "${t.progress}%") },
            )
        }

        // ---- Riesgos ----
        w.section("Riesgos e incidencias abiertos (${openRisks.size})")
        if (openRisks.isEmpty()) {
            w.paragraph("No hay riesgos ni incidencias abiertos.", color = MUTED)
        } else {
            val sorted = openRisks.sortedByDescending { it.score }
            w.table(
                listOf("Tipo", "Descripción", "Severidad", "Responsable", "Límite", "Estado"), listOf(0.9f, 3f, 0.9f, 1.4f, 1f, 0.9f),
                sorted.map { r -> listOf(r.kind.label, r.title, "${r.severity.label} (${r.score})", r.owner.ifBlank { "—" }, r.due?.short() ?: "—", r.status.label) },
                rowColor = { row, col ->
                    if (col == 2) when (sorted[row].severity) { Severity.CRITICA -> ERROR; Severity.ALTA -> ORANGE; Severity.MEDIA -> AMBER; Severity.BAJA -> GREEN } else null
                },
            )
            val withPlan = sorted.filter { it.mitigation.isNotBlank() }
            if (withPlan.isNotEmpty()) {
                w.y += 4f
                withPlan.forEach { r -> w.paragraph("• ${r.title}: ${r.mitigation}", size = 8.5f, color = MUTED) }
            }
        }

        // ---- Integraciones ----
        if (project.integrations.isNotEmpty()) {
            val validated = project.integrations.count { it.status == IntegrationStatus.VALIDADO }
            w.section("Integraciones ($validated de ${project.integrations.size} validadas)")
            w.table(
                listOf("Equipo / sistema", "Tipo", "Fabricante", "Protocolo", "Estado"), listOf(2.4f, 1.1f, 1.4f, 1.3f, 1f),
                project.integrations.sortedBy { it.type }.map { listOf(it.name, it.type.label, it.manufacturer.ifBlank { "—" }, it.protocol.label, it.status.label) },
                rowColor = { r, col ->
                    if (col == 4) when (project.integrations.sortedBy { it.type }[r].status) {
                        IntegrationStatus.VALIDADO -> GREEN; IntegrationStatus.BLOQUEADO -> ERROR; IntegrationStatus.PRUEBAS -> PRIMARY; else -> AMBER
                    } else null
                },
            )
        }

        // ---- Equipo ----
        if (project.team.isNotEmpty()) {
            w.section("Equipo")
            w.table(
                listOf("Nombre", "Rol", "Tareas abiertas"), listOf(2f, 2f, 1f),
                project.team.map { m -> listOf(m.name, m.role, "${project.tasks.count { it.assignee == m.name && it.progress < 100 }}") },
            )
        }

        w.finishPage()
        return doc.toByteArray()
    }

    /* ======================================================================
     *  Diagrama de Gantt (A4 apaisado, paginado por filas)
     * ====================================================================== */

    private sealed interface Row {
        data class Group(val label: String, val colorIndex: Int, val start: LocalDate, val end: LocalDate, val progress: Float, val hours: Double) : Row
        data class Item(val task: Task, val colorIndex: Int) : Row
    }

    fun gantt(project: Project): ByteArray {
        val today = today()
        val pageW = A4_H
        val pageH = A4_W
        val labelW = 200f
        val hoursW = 46f
        val left = MARGIN + labelW + hoursW
        val right = pageW - MARGIN
        val chartW = right - left
        val headerTop = MARGIN + 34f
        val axisH = 28f
        val rowH = 15f
        val bodyTop = headerTop + axisH
        val bodyBottom = pageH - MARGIN - 18f
        val rowsPerPage = ((bodyBottom - bodyTop) / rowH).toInt()

        val from = (project.tasks.map { it.start } + project.startDate).min().previousOrSame(DayOfWeek.MONDAY)
        val to = (project.tasks.map { it.end } + project.goLiveDate).max().plusDays(7)
        val days = daysBetween(from, to).toFloat() + 1
        fun x(d: LocalDate) = left + daysBetween(from, d) / days * chartW
        val dayW = chartW / days

        val rows: List<Row> = project.groups().flatMap { g ->
            val sorted = g.tasks // orden manual del proyecto
            listOf<Row>(Row.Group(g.label, g.colorIndex, g.start, g.end, g.progress, g.estimatedHours)) + sorted.map { Row.Item(it, g.colorIndex) }
        }
        val pages = rows.chunked(rowsPerPage.coerceAtLeast(1))
        val doc = PdfDocument()
        val monthFmt = EsDateFormat(if (days > 300) "MMM yy" else "MMMM yyyy")

        pages.forEachIndexed { pi, pageRows ->
            val page = doc.startPage(pageW, pageH)
            val c = page.canvas

            // Título
            c.textFit("Diagrama de Gantt · ${project.name}", MARGIN, MARGIN + 12f, chartW, text(13f, PRIMARY, bold = true))
            c.textFit(
                "${project.client}  ·  ${project.startDate.long()} → Go-Live ${project.goLiveDate.long()}  ·  Avance ${(project.progress * 100).toInt()}%  ·  Esfuerzo total ${hoursLabel(project.estimatedHours)} (${hoursLabel(project.spentHours)} imputadas)  ·  Emitido ${today.long()}",
                MARGIN, MARGIN + 26f, pageW - MARGIN * 2, text(8.5f, MUTED),
            )

            // Eje temporal
            c.drawRect(MARGIN, headerTop, right, headerTop + axisH, fill(0xFFF0F3F7.toInt()))
            c.drawText("Fase / tarea", MARGIN + 6f, headerTop + 18f, text(8f, MUTED, bold = true))
            c.drawText("Horas", MARGIN + labelW + 4f, headerTop + 18f, text(8f, MUTED, bold = true))
            var d = from
            while (!d.isAfter(to)) {
                val xx = x(d)
                if (d.day == 1 || d == from) {
                    if (d.day == 1) c.drawLine(xx, headerTop, xx, bodyTop + pageRows.size * rowH, stroke(LINE, 0.8f))
                    val label = monthFmt.format(d).replaceFirstChar { it.uppercase() }
                    val monthEnd = x(d.withDayOfMonth(1).plusMonths(1))
                    c.textFit(label, xx + 3f, headerTop + 11f, (monthEnd - xx - 6f).coerceAtLeast(10f), text(7.5f, TEXT, bold = true))
                }
                if (d.dayOfWeek == DayOfWeek.MONDAY && dayW * 7 >= 16f) {
                    c.drawLine(xx, headerTop + 16f, xx, headerTop + axisH, stroke(LINE, 0.5f))
                    c.drawText("${d.day}", xx + 2f, headerTop + 24f, text(6.5f, MUTED))
                    c.drawLine(xx, bodyTop, xx, bodyTop + pageRows.size * rowH, stroke(0x55D6DCE2, 0.4f))
                }
                d = d.plusDays(1)
            }

            // Filas
            val rowIndex = mutableMapOf<String, Pair<Int, Task>>()
            pageRows.forEachIndexed { i, r -> if (r is Row.Item) rowIndex[r.task.id] = i to r.task }
            pageRows.forEachIndexed { i, r ->
                val top = bodyTop + i * rowH
                val cy = top + rowH / 2
                when (r) {
                    is Row.Group -> {
                        val col = groupColor(r.colorIndex)
                        c.drawRect(MARGIN, top, right, top + rowH, fill((col and 0x00FFFFFF) or 0x16000000))
                        c.drawCircle(MARGIN + 6f, cy, 3f, fill(col))
                        c.textFit("${r.label}  ${(r.progress * 100).toInt()}%", MARGIN + 14f, cy + 3f, labelW - 18f, text(8f, TEXT, bold = true))
                        val ph = r.hours
                        if (ph > 0) c.drawText(hoursLabel(ph), MARGIN + labelW + 4f, cy + 3f, text(7.5f, TEXT, bold = true))
                        val x0 = x(r.start)
                        val x1 = x(r.end) + dayW
                        c.drawRect(x0, cy - 2.5f, x1, cy + 2.5f, fill((col and 0x00FFFFFF) or 0x60000000))
                        c.drawRect(x0, cy - 2.5f, x0 + (x1 - x0) * r.progress, cy + 2.5f, fill(col))
                    }
                    is Row.Item -> {
                        val t = r.task
                        val col = groupColor(r.colorIndex)
                        val late = t.status(today) == TaskStatus.ATRASADA
                        c.textFit(t.name, MARGIN + 14f, cy + 3f, labelW - 18f, text(7.5f, if (late) ERROR else TEXT))
                        if (t.estimatedHours > 0) c.drawText(hoursLabel(t.estimatedHours), MARGIN + labelW + 4f, cy + 3f, text(7f, if (t.spentHours > t.estimatedHours) ERROR else MUTED))
                        if (t.isMilestone) {
                            val mx = x(t.start) + dayW / 2
                            val diamond = Path().apply { moveTo(mx, cy - 5f); lineTo(mx + 5f, cy); lineTo(mx, cy + 5f); lineTo(mx - 5f, cy); close() }
                            c.drawPath(diamond, fill(if (t.progress >= 100) col else 0xFFFFFFFF.toInt()))
                            c.drawPath(diamond, stroke(col, 1.4f))
                        } else {
                            val x0 = x(t.start)
                            val bw = (x(t.end) + dayW - x0).coerceAtLeast(2f)
                            val rect = RectF(x0, cy - 4.5f, x0 + bw, cy + 4.5f)
                            c.drawRoundRect(rect, 3f, 3f, fill((col and 0x00FFFFFF) or 0x48000000))
                            if (t.progress > 0) c.drawRoundRect(RectF(x0, cy - 4.5f, x0 + bw * t.progress / 100f, cy + 4.5f), 3f, 3f, fill(col))
                            if (late) c.drawRoundRect(rect, 3f, 3f, stroke(ERROR, 1.1f))
                        }
                    }
                }
                c.drawLine(MARGIN, top + rowH, right, top + rowH, stroke(0x66D6DCE2, 0.4f))
            }

            // Dependencias (solo entre filas de la misma página)
            pageRows.forEachIndexed { i, r ->
                val succ = (r as? Row.Item)?.task ?: return@forEachIndexed
                succ.dependsOn.forEach { pid ->
                    val (pIdx, pred) = rowIndex[pid] ?: return@forEach
                    val fx = if (pred.isMilestone) x(pred.start) + dayW / 2 + 5f else x(pred.end) + dayW
                    val fy = bodyTop + pIdx * rowH + rowH / 2
                    val tx = if (succ.isMilestone) x(succ.start) + dayW / 2 - 5f else x(succ.start)
                    val ty = bodyTop + i * rowH + rowH / 2
                    val color = if (succ.start.isBefore(pred.end)) ERROR else 0xAA5F676D.toInt()
                    val path = Path().apply {
                        moveTo(fx, fy); lineTo(fx + 3f, fy)
                        if (tx - fx >= 6f) {
                            lineTo(fx + 3f, ty)
                        } else {
                            val ey = if (ty > fy) ty - rowH / 2 else ty + rowH / 2
                            lineTo(fx + 3f, ey); lineTo(tx - 6f, ey); lineTo(tx - 6f, ty)
                        }
                        lineTo(tx - 3f, ty)
                    }
                    c.drawPath(path, stroke(color, 0.7f))
                    c.drawPath(Path().apply { moveTo(tx, ty); lineTo(tx - 3.5f, ty - 2.5f); lineTo(tx - 3.5f, ty + 2.5f); close() }, fill(color))
                }
            }

            // Hoy y Go-Live
            val bodyEnd = bodyTop + pageRows.size * rowH
            if (!today.isBefore(from) && !today.isAfter(to)) {
                val tx = x(today) + dayW / 2
                c.drawLine(tx, headerTop, tx, bodyEnd, stroke(TODAY, 1.2f))
                val tp = text(6.5f, 0xFFFFFFFF.toInt(), bold = true)
                val tw = tp.measureText("HOY") + 6f
                c.drawRoundRect(RectF(tx - tw / 2, headerTop + axisH - 9f, tx + tw / 2, headerTop + axisH), 4f, 4f, fill(TODAY))
                c.drawText("HOY", tx - tw / 2 + 3f, headerTop + axisH - 2.2f, tp)
            }
            val gx = x(project.goLiveDate) + dayW / 2
            c.drawLine(gx, headerTop, gx, bodyEnd, stroke(PRIMARY, 1.2f).apply { dash = floatArrayOf(4f, 3f) })

            // Pie con leyenda
            val fy = pageH - MARGIN + 4f
            val lp = text(7f, MUTED)
            var lx = MARGIN
            fun legend(label: String, draw: (Float) -> Unit) { draw(lx); lx += 16f; c.drawText(label, lx, fy + 3f, lp); lx += lp.measureText(label) + 14f }
            legend("Avance") { c.drawRoundRect(RectF(it, fy - 2f, it + 12f, fy + 4f), 2f, 2f, fill(SECONDARY)) }
            legend("Atrasada") { c.drawRoundRect(RectF(it, fy - 2f, it + 12f, fy + 4f), 2f, 2f, stroke(ERROR, 1.1f)) }
            legend("Hito") { c.drawPath(Path().apply { moveTo(it + 6f, fy - 4f); lineTo(it + 10f, fy + 1f); lineTo(it + 6f, fy + 6f); lineTo(it + 2f, fy + 1f); close() }, fill(SECONDARY)) }
            legend("Dependencia") { c.drawLine(it, fy + 1f, it + 12f, fy + 1f, stroke(MUTED, 0.8f)) }
            legend("Hoy") { c.drawLine(it + 6f, fy - 4f, it + 6f, fy + 6f, stroke(TODAY, 1.4f)) }
            legend("Go-Live") { c.drawLine(it + 6f, fy - 4f, it + 6f, fy + 6f, stroke(PRIMARY, 1.4f)) }
            val pn = "Página ${pi + 1} de ${pages.size}"
            c.drawText(pn, right - lp.measureText(pn), fy + 3f, lp)

        }

        return doc.toByteArray()
    }
}
