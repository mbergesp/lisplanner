package com.lisplanner.app.export.pdf

import com.lisplanner.app.data.formatDecimalUs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * Generador de PDF en Kotlin puro (Android e iOS producen exactamente el mismo documento).
 *
 * La API imita a la de android.graphics (Canvas, Paint, RectF, Path) para que el código de los
 * informes se lea igual que antes: coordenadas en puntos con el origen arriba a la izquierda.
 * Fuentes: Helvetica y Helvetica-Bold estándar con codificación WinAnsi (tildes, ñ, «», €…).
 */

class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

class Path {
    internal val ops = mutableListOf<Pair<Char, FloatArray>>()
    fun moveTo(x: Float, y: Float) { ops += 'M' to floatArrayOf(x, y) }
    fun lineTo(x: Float, y: Float) { ops += 'L' to floatArrayOf(x, y) }
    fun close() { ops += 'Z' to floatArrayOf() }
}

enum class PaintStyle { FILL, STROKE }
enum class Cap { BUTT, ROUND }

/** Color en formato ARGB (como android.graphics.Color). La transparencia se mezcla con fondo blanco. */
class Paint(
    var color: Int = 0xFF000000.toInt(),
    var style: PaintStyle = PaintStyle.FILL,
    var strokeWidth: Float = 1f,
    var textSize: Float = 12f,
    var bold: Boolean = false,
    var strokeCap: Cap = Cap.BUTT,
    var dash: FloatArray? = null,
) {
    fun measureText(s: String): Float = Fonts.width(s, textSize, bold)
}

object Fonts {
    fun width(s: String, size: Float, bold: Boolean): Float {
        val table = if (bold) FontMetrics.HELVETICA_BOLD else FontMetrics.HELVETICA
        var w = 0
        WinAnsi.encode(s).forEach { b ->
            val code = b.toInt() and 0xFF
            w += if (code in 32..255) table[code - 32] else 0
        }
        return w * size / 1000f
    }

    /** Recorta el texto con «…» para que quepa en [maxWidth]. */
    fun ellipsize(s: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(s) <= maxWidth) return s
        val ell = "…"
        var lo = 0
        var hi = s.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(s.take(mid).trimEnd() + ell) <= maxWidth) lo = mid else hi = mid - 1
        }
        return if (lo == 0) ell else s.take(lo).trimEnd() + ell
    }

    /** Parte el texto en líneas que caben en [width] (respetando los saltos de línea). */
    fun wrap(s: String, paint: Paint, width: Float): List<String> = s.split('\n').flatMap { para ->
        if (para.isBlank()) return@flatMap listOf("")
        val lines = mutableListOf<String>()
        var line = ""
        para.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width || line.isEmpty()) {
                line = candidate
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        lines
    }
}

/** Conversión a WinAnsiEncoding (cp1252). Lo que no existe se sustituye por un equivalente. */
internal object WinAnsi {
    private val specials = mapOf(
        '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87,
        'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E,
        '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95, '–' to 0x96, '—' to 0x97,
        '˜' to 0x98, '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F,
    )
    private val replacements = mapOf(
        '→' to "->", '←' to "<-", '↔' to "<->", '◆' to "*", '⏱' to "", '✓' to "v", '≥' to ">=", '≤' to "<=",
        ' ' to " ", '\t' to " ",
    )

    fun encode(s: String): ByteArray {
        val out = ArrayList<Byte>(s.length)
        s.forEach { c ->
            val code = c.code
            when {
                code in 32..126 || code in 0xA0..0xFF -> out += code.toByte()
                c in specials -> out += specials.getValue(c).toByte()
                c in replacements -> replacements.getValue(c).forEach { out += it.code.toByte() }
                else -> out += '?'.code.toByte()
            }
        }
        return out.toByteArray()
    }
}

class PdfCanvas internal constructor(private val pageHeight: Float) {
    internal val sb = StringBuilder()

    private fun n(v: Float) = formatDecimalUs(v.toDouble(), 2).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    private fun yy(y: Float) = pageHeight - y

    private fun rgb(color: Int): String {
        val a = (color ushr 24 and 0xFF) / 255f
        fun ch(shift: Int) = ((color ushr shift and 0xFF) / 255f).let { it * a + (1 - a) } // mezcla con blanco
        return "${n(ch(16))} ${n(ch(8))} ${n(ch(0))}"
    }

    private fun applyPaint(p: Paint) {
        if (p.style == PaintStyle.FILL) {
            sb.append(rgb(p.color)).append(" rg\n")
        } else {
            sb.append(rgb(p.color)).append(" RG ").append(n(p.strokeWidth)).append(" w ")
                .append(if (p.strokeCap == Cap.ROUND) 1 else 0).append(" J ")
                .append(p.dash?.let { d -> "[${d.joinToString(" ") { n(it) }}] 0 d" } ?: "[] 0 d").append('\n')
        }
    }

    private fun paintOp(p: Paint) = if (p.style == PaintStyle.FILL) "f\n" else "S\n"

    private fun moveTo(x: Float, y: Float) = sb.append(n(x)).append(' ').append(n(yy(y))).append(" m\n")
    private fun lineTo(x: Float, y: Float) = sb.append(n(x)).append(' ').append(n(yy(y))).append(" l\n")
    private fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        sb.append("${n(x1)} ${n(yy(y1))} ${n(x2)} ${n(yy(y2))} ${n(x3)} ${n(yy(y3))} c\n")

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        sb.append("q\n"); applyPaint(paint)
        sb.append("${n(left)} ${n(yy(bottom))} ${n(right - left)} ${n(bottom - top)} re\n").append(paintOp(paint)).append("Q\n")
    }

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        if (w <= 0f || h <= 0f) return
        val r = min(min(rx, ry), min(w, h) / 2)
        val k = 0.5523f * r
        val (l, t, ri, b) = listOf(rect.left, rect.top, rect.right, rect.bottom)
        sb.append("q\n"); applyPaint(paint)
        moveTo(l + r, t)
        lineTo(ri - r, t); curveTo(ri - r + k, t, ri, t + r - k, ri, t + r)
        lineTo(ri, b - r); curveTo(ri, b - r + k, ri - r + k, b, ri - r, b)
        lineTo(l + r, b); curveTo(l + r - k, b, l, b - r + k, l, b - r)
        lineTo(l, t + r); curveTo(l, t + r - k, l + r - k, t, l + r, t)
        sb.append("h\n").append(paintOp(paint)).append("Q\n")
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        sb.append("q\n"); applyPaint(Paint(paint.color, PaintStyle.STROKE, paint.strokeWidth, strokeCap = paint.strokeCap, dash = paint.dash))
        moveTo(x1, y1); lineTo(x2, y2); sb.append("S\nQ\n")
    }

    fun drawOval(rect: RectF, paint: Paint) = ellipse((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2, (rect.right - rect.left) / 2, (rect.bottom - rect.top) / 2, paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = ellipse(cx, cy, radius, radius, paint)

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, paint: Paint) {
        val kx = 0.5523f * rx
        val ky = 0.5523f * ry
        sb.append("q\n"); applyPaint(paint)
        moveTo(cx + rx, cy)
        curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        sb.append("h\n").append(paintOp(paint)).append("Q\n")
    }

    /**
     * Arco como en Android: ángulos en grados, 0° = las 3 en punto y sentido horario (eje Y hacia abajo).
     * Solo se usa con trazo (velocímetro, anillos).
     */
    fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, @Suppress("UNUSED_PARAMETER") useCenter: Boolean, paint: Paint) {
        if (abs(sweepAngle) < 0.01f) return
        val cx = (oval.left + oval.right) / 2
        val cy = (oval.top + oval.bottom) / 2
        val rx = (oval.right - oval.left) / 2
        val ry = (oval.bottom - oval.top) / 2
        val segments = ceil(abs(sweepAngle) / 90f).toInt().coerceAtLeast(1)
        val step = sweepAngle / segments
        fun rad(d: Float) = d * PI.toFloat() / 180f
        sb.append("q\n"); applyPaint(paint)
        var a = rad(startAngle)
        moveTo(cx + rx * cos(a), cy + ry * sin(a))
        repeat(segments) {
            val b = a + rad(step)
            val k = 4f / 3f * tan((b - a) / 4f)
            val p1x = cx + rx * (cos(a) - k * sin(a)); val p1y = cy + ry * (sin(a) + k * cos(a))
            val p2x = cx + rx * (cos(b) + k * sin(b)); val p2y = cy + ry * (sin(b) - k * cos(b))
            curveTo(p1x, p1y, p2x, p2y, cx + rx * cos(b), cy + ry * sin(b))
            a = b
        }
        sb.append(if (paint.style == PaintStyle.FILL) "f\n" else "S\n").append("Q\n")
    }

    fun drawPath(path: Path, paint: Paint) {
        sb.append("q\n"); applyPaint(paint)
        path.ops.forEach { (op, v) ->
            when (op) {
                'M' -> moveTo(v[0], v[1])
                'L' -> lineTo(v[0], v[1])
                'Z' -> sb.append("h\n")
            }
        }
        sb.append(paintOp(paint)).append("Q\n")
    }

    /** Texto con la línea base en [y]. */
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        sb.append("BT\n").append(rgb(paint.color)).append(" rg\n")
            .append(if (paint.bold) "/F2 " else "/F1 ").append(n(paint.textSize)).append(" Tf\n")
            .append(n(x)).append(' ').append(n(yy(y))).append(" Td\n(")
        WinAnsi.encode(text).forEach { b ->
            val code = b.toInt() and 0xFF
            when {
                code == '('.code || code == ')'.code || code == '\\'.code -> sb.append('\\').append(code.toChar())
                code < 32 || code > 126 -> sb.append('\\').append(code.toString(8).padStart(3, '0'))
                else -> sb.append(code.toChar())
            }
        }
        sb.append(") Tj\nET\n")
    }
}

class PdfPage internal constructor(val width: Int, val height: Int) {
    val canvas = PdfCanvas(height.toFloat())
}

/** Documento PDF: varias páginas, cada una con su tamaño (vertical o apaisada). */
class PdfDocument {
    private val pages = mutableListOf<PdfPage>()

    fun startPage(width: Int, height: Int): PdfPage = PdfPage(width, height).also { pages += it }

    fun toByteArray(): ByteArray {
        val out = StringBuilder()
        val offsets = mutableListOf<Int>()
        fun obj(body: String) {
            offsets += out.length
            out.append("${offsets.size} 0 obj\n").append(body).append("\nendobj\n")
        }
        out.append("%PDF-1.4\n%âãÏÓ\n")
        // 1 catálogo, 2 árbol de páginas, 3-4 fuentes; después, por página: objeto página + contenido
        val pageIds = pages.indices.map { 5 + it * 2 }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { i, p ->
            val content = p.canvas.sb.toString()
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${p.width} ${p.height}] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${pageIds[i] + 1} 0 R >>",
            )
            obj("<< /Length ${content.length} >>\nstream\n$content\nendstream")
        }
        val xref = out.length
        out.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        // Todo el documento es ASCII salvo la línea de cabecera binaria: se codifica byte a byte.
        return ByteArray(out.length) { out[it].code.toByte() }
    }
}
