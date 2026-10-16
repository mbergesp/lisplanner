package com.lisplanner.app.data

import com.lisplanner.app.export.Reports
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Los informes PDF se generan en código común: se comprueba que salen y que son PDF válidos. */
class ReportsTest {
    @Test
    fun generatesStatusAndGanttPdfs() {
        val out = System.getenv("PDF_OUT")?.let(::File)
        SampleData.projects().take(2).forEachIndexed { i, p ->
            val status = Reports.statusReport(p)
            val gantt = Reports.gantt(p)
            listOf(status, gantt).forEach { pdf ->
                val head = pdf.copyOfRange(0, 8).decodeToString()
                assertTrue(head.startsWith("%PDF-1."))
                assertTrue(pdf.decodeToString().trimEnd().endsWith("%%EOF"))
            }
            println("Proyecto $i: informe ${status.size} bytes, Gantt ${gantt.size} bytes")
            out?.let {
                it.mkdirs()
                File(it, "informe_$i.pdf").writeBytes(status)
                File(it, "gantt_$i.pdf").writeBytes(gantt)
            }
        }
    }
}
