package com.lisplanner.app.data

/** Resultado de leer un .mpp: el proyecto ya convertido y datos para la pantalla de revisión. */
data class ImportPreview(
    val project: Project,
    val fileName: String,
    val sourceTitle: String?,
    val summaryTasks: Int,
    val dependencies: Int,
    val warnings: List<String>,
)
