package com.lisplanner.app.data

/**
 * Fases con las que se crean los proyectos nuevos. El usuario puede renombrarlas, reordenarlas,
 * añadir o eliminar fases después desde «Gestionar fases».
 */
object DefaultPhases {
    val names = listOf("Consultoría", "Sistemas", "Configuración", "Interoperabilidad", "Formación", "Despliegue")
    private val colors = listOf(0, 9, 2, 4, 7, 6)

    const val CONSULTORIA = 0
    const val SISTEMAS = 1
    const val CONFIGURACION = 2
    const val INTEROPERABILIDAD = 3
    const val FORMACION = 4
    const val DESPLIEGUE = 5

    fun create(): List<PhaseDef> = names.mapIndexed { i, n -> PhaseDef(name = n, colorIndex = colors[i]) }

    /** Fase por defecto que corresponde a una tarea de plantilla, según su nombre y su categoría LIS. */
    fun indexFor(task: Task): Int {
        val n = task.name.lowercase()
        fun has(vararg k: String) = k.any { it in n }
        return when {
            has(
                "paso a producción", "en producción", "arranque", "go-live", "simulacro", "contingencia", "soporte",
                "hypercare", "seguimiento de incidencias", "cierre", "(uat)", "aprobación para", "verificación post",
                "documentación de validación", "end-to-end", "copia de seguridad previa",
            ) -> DESPLIEGUE
            has("formación", "formativo", "manual", "guía de novedades", "comunicación de la parada") -> FORMACION
            has(
                "impacto", "notas de versión", "plan de actualización", "kick-off", "lanzamiento", "plan de proyecto",
                "comités", "levantamiento", "catálogo de pruebas, perfiles", "requisitos de informes", "aprobación del",
            ) -> CONSULTORIA
            has("servidor", "entorno", "infraestructura", "usuarios, roles", "migra", "histór", "base de datos", "instalación") -> SISTEMAS
            has("hl7", "interfaz", "interfaces", "analizador", "middleware", "driver", "bidireccional") -> INTEROPERABILIDAD
            else -> when (task.phase) {
                LisPhase.KICKOFF, LisPhase.ANALISIS -> CONSULTORIA
                LisPhase.ANALIZADORES, LisPhase.INTERFACES -> INTEROPERABILIDAD
                LisPhase.MIGRACION -> SISTEMAS
                LisPhase.FORMACION -> FORMACION
                LisPhase.ARRANQUE, LisPhase.SOPORTE -> DESPLIEGUE
                else -> CONFIGURACION
            }
        }
    }

    /** Reparte las tareas de un plan en las fases por defecto (mismo orden de tareas, agrupadas por fase). */
    fun assign(tasks: List<Task>): Pair<List<PhaseDef>, List<Task>> {
        val defs = create()
        val mapped = tasks.map { t -> t.copy(phaseId = defs[indexFor(t)].id) }
        val ordered = defs.flatMap { d -> mapped.filter { it.phaseId == d.id } }
        return defs to ordered
    }
}
