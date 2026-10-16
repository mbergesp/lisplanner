package com.lisplanner.app.data

import kotlinx.datetime.LocalDate

/* ---------------------------------------------------------------------------
 *  Analizador XML mínimo (sin dependencias, multiplataforma)
 * --------------------------------------------------------------------------- */

class XmlNode(val name: String) {
    val children = mutableListOf<XmlNode>()
    internal val textBuilder = StringBuilder()
    val text: String get() = textBuilder.toString().trim()

    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }
    fun all(name: String): List<XmlNode> = children.filter { it.name == name }
    fun value(name: String): String? = child(name)?.text?.takeIf { it.isNotEmpty() }
}

object MiniXml {
    fun parse(xml: String): XmlNode {
        val root = XmlNode("#document")
        val stack = ArrayDeque<XmlNode>().apply { addLast(root) }
        var i = 0
        val n = xml.length
        while (i < n) {
            val c = xml[i]
            if (c != '<') {
                val end = xml.indexOf('<', i).let { if (it < 0) n else it }
                stack.last().textBuilder.append(decode(xml.substring(i, end)))
                i = end
                continue
            }
            when {
                xml.startsWith("<!--", i) -> i = xml.indexOf("-->", i).let { if (it < 0) n else it + 3 }
                xml.startsWith("<![CDATA[", i) -> {
                    val end = xml.indexOf("]]>", i).let { if (it < 0) n else it }
                    stack.last().textBuilder.append(xml, i + 9, end)
                    i = end + 3
                }
                xml.startsWith("<?", i) -> i = xml.indexOf("?>", i).let { if (it < 0) n else it + 2 }
                xml.startsWith("<!", i) -> i = xml.indexOf('>', i).let { if (it < 0) n else it + 1 }
                xml.startsWith("</", i) -> {
                    i = xml.indexOf('>', i).let { if (it < 0) n else it + 1 }
                    if (stack.size > 1) stack.removeLast()
                }
                else -> {
                    val end = tagEnd(xml, i)
                    val raw = xml.substring(i + 1, end)
                    val selfClosing = raw.endsWith("/")
                    val name = raw.trimEnd('/').trim().split(' ', '\t', '\n', '\r').first()
                        .substringAfter(':') // sin prefijo de espacio de nombres
                    val node = XmlNode(name)
                    stack.last().children += node
                    if (!selfClosing) stack.addLast(node)
                    i = end + 1
                }
            }
        }
        return root
    }

    /** Fin de etiqueta respetando comillas en los atributos. */
    private fun tagEnd(xml: String, start: Int): Int {
        var quote: Char? = null
        var i = start + 1
        while (i < xml.length) {
            val ch = xml[i]
            if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '>') {
                return i
            }
            i++
        }
        return xml.length - 1
    }

    fun decode(s: String): String {
        if ('&' !in s) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i)
                if (semi > i) {
                    val ent = s.substring(i + 1, semi)
                    val rep = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") -> ent.drop(2).toIntOrNull(16)?.let { codePointToString(it) }
                        ent.startsWith("#") -> ent.drop(1).toIntOrNull()?.let { codePointToString(it) }
                        else -> null
                    }
                    if (rep != null) {
                        out.append(rep)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun codePointToString(cp: Int): String =
        if (cp < 0x10000) cp.toChar().toString()
        else {
            val v = cp - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
}

/* ---------------------------------------------------------------------------
 *  Importación del formato XML de Microsoft Project (MSPDI)
 * --------------------------------------------------------------------------- */

/**
 * Importa el XML de MS Project («Guardar como → Formato XML»). Funciona en Android e iOS y
 * convierte igual que el lector de .mpp: tareas resumen → fases, trabajo → horas, recursos → equipo.
 */
object MspdiImporter {

    private class RawTask(
        val uid: String,
        val id: Int,
        val name: String,
        val level: Int,
        val summary: Boolean,
        val milestone: Boolean,
        val start: LocalDate,
        val finish: LocalDate,
        val work: Double,
        val actual: Double,
        val percent: Int,
        val notes: String,
        val preds: List<Pair<String, Pair<Int, Boolean>>>, // UID → (tipo, tiene lag)
    )

    fun read(xml: String, fileName: String): ImportPreview {
        val doc = MiniXml.parse(xml)
        val root = doc.child("Project") ?: error("El fichero no es un XML de Microsoft Project (falta <Project>).")
        val warnings = mutableListOf<String>()

        val raw = root.child("Tasks")?.all("Task").orEmpty().mapNotNull { t ->
            if (t.value("IsNull") == "1") return@mapNotNull null
            val name = t.value("Name") ?: return@mapNotNull null
            val start = t.value("Start")?.let(::date) ?: return@mapNotNull null
            val finish = t.value("Finish")?.let(::date) ?: start
            RawTask(
                uid = t.value("UID") ?: return@mapNotNull null,
                id = t.value("ID")?.toIntOrNull() ?: 0,
                name = name.trim(),
                level = t.value("OutlineLevel")?.toIntOrNull() ?: 1,
                summary = t.value("Summary") == "1",
                milestone = t.value("Milestone") == "1",
                start = start,
                finish = if (finish < start) start else finish,
                work = hours(t.value("Work")),
                actual = hours(t.value("ActualWork")),
                percent = t.value("PercentComplete")?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                notes = t.value("Notes").orEmpty(),
                preds = t.all("PredecessorLink").mapNotNull { l ->
                    val p = l.value("PredecessorUID") ?: return@mapNotNull null
                    p to ((l.value("Type")?.toIntOrNull() ?: 1) to ((l.value("LinkLag")?.toIntOrNull() ?: 0) != 0))
                },
            )
        }.sortedBy { it.id }
        val skipped = (root.child("Tasks")?.all("Task")?.size ?: 0) - raw.size

        // Recursos y asignaciones
        val resources = root.child("Resources")?.all("Resource").orEmpty().mapNotNull { r ->
            val uid = r.value("UID") ?: return@mapNotNull null
            val name = r.value("Name") ?: return@mapNotNull null
            uid to (name.trim() to r.value("Group"))
        }.toMap()
        val assignments = root.child("Assignments")?.all("Assignment").orEmpty().mapNotNull { a ->
            val task = a.value("TaskUID") ?: return@mapNotNull null
            val res = resources[a.value("ResourceUID")]?.first ?: return@mapNotNull null
            Triple(task, res, hours(a.value("Work")))
        }.groupBy({ it.first }, { it.second to it.third })

        // Jerarquía: cada tarea cuelga de la última tarea resumen de nivel inferior
        val parent = mutableMapOf<String, RawTask>()
        val lastAtLevel = mutableMapOf<Int, RawTask>()
        raw.forEach { t ->
            if (t.level >= 2) lastAtLevel[t.level - 1]?.let { parent[t.uid] = it }
            lastAtLevel[t.level] = t
            (t.level + 1..t.level + 20).forEach { lastAtLevel.remove(it) }
        }
        val summaries = raw.filter { it.summary && it.level >= 1 }
        val nameCount = summaries.groupingBy { it.name }.eachCount()
        fun groupName(t: RawTask): String {
            val p = parent[t.uid]?.takeIf { it.level >= 1 } ?: return ""
            if ((nameCount[p.name] ?: 0) <= 1) return p.name
            val g = parent[p.uid]?.takeIf { it.level >= 1 }
            return if (g != null) "${g.name} › ${p.name}" else p.name
        }

        val leaves = raw.filter { !it.summary && it.level >= 1 }
        val ids = leaves.associate { it.uid to newId() }
        var deps = 0
        var nonFs = 0
        var lagged = 0
        val tasks = leaves.map { t ->
            val assigned = assignments[t.uid].orEmpty().sortedByDescending { it.second }
            val preds = t.preds.mapNotNull { (uid, info) ->
                val id = ids[uid] ?: return@mapNotNull null
                if (info.first != 1) nonFs++
                if (info.second) lagged++
                id
            }
            deps += preds.size
            val notes = buildList {
                t.notes.trim().takeIf { it.isNotEmpty() }?.let { add(it.take(1000)) }
                if (assigned.size > 1) add("Recursos: " + assigned.joinToString(", ") { "${it.first} (${it.second.toLong()} h)" })
            }.joinToString("\n\n")
            Task(
                id = ids.getValue(t.uid),
                name = t.name,
                phase = guessPhase(t.name),
                start = t.start,
                end = if (t.milestone) t.start else t.finish,
                progress = t.percent,
                assignee = assigned.firstOrNull()?.first.orEmpty(),
                isMilestone = t.milestone,
                notes = notes,
                dependsOn = preds,
                estimatedHours = t.work,
                spentHours = t.actual,
                group = groupName(t),
            )
        }

        // Trabajo asignado directamente a tareas resumen: se importa como una tarea más del grupo
        val summaryWork = summaries.filter { assignments[it.uid].orEmpty().isNotEmpty() }.map { s ->
            val own = assignments.getValue(s.uid).sortedByDescending { it.second }
            Task(
                name = "${s.name} (trabajo de la tarea resumen)",
                phase = guessPhase(s.name),
                start = s.start, end = s.finish, progress = s.percent,
                assignee = own.first().first,
                estimatedHours = own.sumOf { it.second },
                spentHours = 0.0,
                group = s.name,
            )
        }

        if (skipped > 0) warnings += "$skipped filas vacías o sin fechas ignoradas."
        if (summaryWork.isNotEmpty()) warnings += "${summaryWork.size} tareas resumen con recursos propios se han importado como tareas."
        if (nonFs > 0) warnings += "$nonFs dependencias que no son fin→inicio se han importado como fin→inicio."
        if (lagged > 0) warnings += "$lagged dependencias tenían adelanto o retraso (lag), que no se conserva."

        val usedResources = assignments.values.flatten().map { it.first }.toSet()
        val team = resources.values.filter { it.first in usedResources }
            .map { (name, group) -> TeamMember(name = name, role = group?.takeIf { it.isNotBlank() } ?: "Recurso de Project") }
            .distinctBy { it.name }

        val all = tasks + summaryWork
        val start = all.minOfOrNull { it.start } ?: today()
        val end = all.maxOfOrNull { it.end } ?: start.plusMonths(6)
        val goLive = all.filter { it.phase == LisPhase.ARRANQUE }.maxOfOrNull { it.end }?.takeIf { it > start }
            ?: end.takeIf { it > start } ?: start.plusDays(1)
        val topSummary = summaries.filter { it.level == 1 }.maxByOrNull { it.work }?.name

        val title = root.value("Title")
        val project = Project(
            name = fileName.substringBeforeLast('.').trim().ifBlank { title ?: "Proyecto importado" },
            client = root.value("Company") ?: topSummary.orEmpty(),
            projectManager = root.value("Manager").orEmpty(),
            startDate = start,
            goLiveDate = goLive,
            status = if (all.any { it.progress > 0 }) ProjectStatus.EN_CURSO else ProjectStatus.PLANIFICACION,
            description = "Importado de Microsoft Project ($fileName).",
            tasks = all,
            team = team,
        ).normalized()

        return ImportPreview(project, fileName, title, summaries.size, deps, warnings)
    }

    private fun date(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()

    /** Duración ISO-8601 de MSPDI (PT40H0M0S, P1DT2H…) a horas. Los días cuentan como 8 h. */
    fun hours(s: String?): Double {
        if (s.isNullOrBlank()) return 0.0
        val m = Regex("""^-?P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""").matchEntire(s.trim())
            ?: return 0.0
        val (d, h, mi, se) = m.destructured
        val total = (d.toDoubleOrNull() ?: 0.0) * 8 + (h.toDoubleOrNull() ?: 0.0) + (mi.toDoubleOrNull() ?: 0.0) / 60 + (se.toDoubleOrNull() ?: 0.0) / 3600
        return (total * 10).let { kotlin.math.round(it) } / 10.0
    }
}
