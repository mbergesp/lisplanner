package com.lisplanner.app.export

import com.lisplanner.app.data.MspdiExporter
import com.lisplanner.app.data.Project

/** XML de Microsoft Project (MSPDI) listo para compartir. */
object ProjectXmlExport {
    fun export(project: Project): ByteArray = MspdiExporter.write(project).encodeToByteArray()
}
