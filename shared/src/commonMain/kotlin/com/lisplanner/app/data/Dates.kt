package com.lisplanner.app.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

/*
 * Utilidades de fechas multiplataforma sobre kotlinx-datetime, con los mismos nombres que se
 * usaban con java.time para que el código de negocio y de interfaz apenas cambie.
 */

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Int): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Long): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Int): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(n: Int): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Int): LocalDate = minus(n, DateTimeUnit.MONTH)
fun LocalDate.plusYears(n: Int): LocalDate = plus(n, DateTimeUnit.YEAR)

fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
fun LocalDate.isAfter(other: LocalDate): Boolean = this > other

fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, month, day)
fun LocalDate.withDayOfYear(dayOfYear: Int): LocalDate = LocalDate(year, 1, 1).plusDays(dayOfYear - 1)

/** Días entre dos fechas (como ChronoUnit.DAYS.between). */
fun daysBetween(from: LocalDate, to: LocalDate): Long = from.daysUntil(to).toLong()

/** Meses completos entre dos fechas (como ChronoUnit.MONTHS.between). */
fun monthsBetween(from: LocalDate, to: LocalDate): Long = from.monthsUntil(to).toLong()

fun LocalDate.previousOrSame(dow: DayOfWeek): LocalDate =
    minusDays(((dayOfWeek.isoDayNumber - dow.isoDayNumber) + 7) % 7)

fun LocalDate.nextOrSame(dow: DayOfWeek): LocalDate =
    plusDays(((dow.isoDayNumber - dayOfWeek.isoDayNumber) + 7) % 7)

val DATE_MIN = LocalDate(1, 1, 1)
val DATE_MAX = LocalDate(9999, 12, 31)

private const val MILLIS_PER_DAY = 86_400_000L

/** Milisegundos UTC a medianoche de la fecha (formato que usa el DatePicker de Material). */
fun LocalDate.toEpochMillisUtc(): Long = toEpochDays() * MILLIS_PER_DAY

fun dateFromEpochMillisUtc(millis: Long): LocalDate = LocalDate.fromEpochDays(Math.floorDiv(millis, MILLIS_PER_DAY))

private object Math {
    fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q
    }
}

/* ---------------------------------------------------------------------------
 *  Formato de fechas en español (sustituye a DateTimeFormatter + Locale es-ES)
 * --------------------------------------------------------------------------- */

private val monthsLong = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val monthsShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
private val daysLong = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val daysShort = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/**
 * Formateador con la sintaxis habitual de patrones: d, dd, M, MM, MMM, MMMM, yy, yyyy, EEE, EEEE
 * y texto literal entre comillas simples ('de').
 */
class EsDateFormat(private val pattern: String) {
    fun format(date: LocalDate): String {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                out.append(pattern, i + 1, end)
                i = end + 1
                continue
            }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            when (c) {
                'd' -> out.append(if (n >= 2) date.day.toString().padStart(2, '0') else date.day.toString())
                'M' -> out.append(
                    when {
                        n >= 4 -> monthsLong[date.month.number - 1]
                        n == 3 -> monthsShort[date.month.number - 1]
                        n == 2 -> date.month.number.toString().padStart(2, '0')
                        else -> date.month.number.toString()
                    },
                )
                'y' -> out.append(if (n == 2) (date.year % 100).toString().padStart(2, '0') else date.year.toString())
                'E' -> out.append(if (n >= 4) daysLong[date.dayOfWeek.isoDayNumber - 1] else daysShort[date.dayOfWeek.isoDayNumber - 1])
                else -> repeat(n) { out.append(c) }
            }
            i += n
        }
        return out.toString()
    }
}

fun LocalDate.format(f: EsDateFormat): String = f.format(this)

/* ---------------------------------------------------------------------------
 *  Números (sustituye a String.format y NumberFormat, que no existen en iOS)
 * --------------------------------------------------------------------------- */

/** Formatea con [decimals] decimales, coma decimal y punto de miles (es-ES). */
fun formatNumberEs(value: Double, decimals: Int = 1, thousands: Boolean = true, trimZeros: Boolean = true): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = scaled / factor
    val frac = scaled % factor
    val intStr = intPart.toString().let { s ->
        if (!thousands || s.length <= 3) s else s.reversed().chunked(3).joinToString(".").reversed()
    }
    val fracStr = if (decimals == 0) "" else frac.toString().padStart(decimals, '0').let { if (trimZeros) it.trimEnd('0') else it }
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return sign + intStr + if (fracStr.isNotEmpty()) ",$fracStr" else ""
}

/** Número con punto decimal y [decimals] decimales fijos (formatos técnicos: XML, PDF). */
fun formatDecimalUs(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (decimals == 0) sign + scaled.toString()
    else sign + (scaled / factor).toString() + "." + (scaled % factor).toString().padStart(decimals, '0')
}

/** Quita tildes y diéresis (sustituye a java.text.Normalizer). */
fun String.withoutAccents(): String = map { c ->
    when (c) {
        'á', 'à', 'ä', 'â' -> 'a'
        'é', 'è', 'ë', 'ê' -> 'e'
        'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô' -> 'o'
        'ú', 'ù', 'ü', 'û' -> 'u'
        'Á', 'À', 'Ä', 'Â' -> 'A'
        'É', 'È', 'Ë', 'Ê' -> 'E'
        'Í', 'Ì', 'Ï', 'Î' -> 'I'
        'Ó', 'Ò', 'Ö', 'Ô' -> 'O'
        'Ú', 'Ù', 'Ü', 'Û' -> 'U'
        else -> c
    }
}.joinToString("")
