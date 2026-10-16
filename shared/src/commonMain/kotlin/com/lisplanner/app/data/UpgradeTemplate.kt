package com.lisplanner.app.data

import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * Plantilla de actualización de versión de un LIS ya implantado.
 *
 * Referencia de 12 semanas hasta el paso a producción, escalada a las fechas reales.
 * El soporte posterior se define en días desde el paso a producción.
 */
object UpgradeTemplate {
    private const val REFERENCE_WEEKS = 12.0
    private const val GO_LIVE = "Paso a producción"
    private const val INSTALL = "Instalación en producción (ventana de parada)"

    private data class P(val name: String, val category: LisPhase, val color: Int)

    private val phases = listOf(
        P("Planificación de la actualización", LisPhase.KICKOFF, 0),
        P("Análisis de impacto", LisPhase.ANALISIS, 1),
        P("Entorno de pruebas", LisPhase.CONFIGURACION, 9),
        P("Adaptación de configuración", LisPhase.CONFIGURACION, 2),
        P("Interfaces y analizadores", LisPhase.ANALIZADORES, 3),
        P("Pruebas y validación", LisPhase.VALIDACION, 6),
        P("Formación y comunicación", LisPhase.FORMACION, 7),
        P("Paso a producción", LisPhase.ARRANQUE, 8),
        P("Soporte post-actualización", LisPhase.SOPORTE, 4),
    )

    private data class T(
        val phase: Int,
        val name: String,
        val fromWeek: Double,
        val toWeek: Double,
        val hours: Int,
        val milestone: Boolean = false,
        val role: String = "",
        val after: List<String> = emptyList(),
    )

    private val tasks = listOf(
        T(0, "Reunión de lanzamiento de la actualización", 0.0, 0.0, 4, milestone = true),
        T(0, "Revisión de notas de versión y novedades", 0.0, 1.0, 16, role = "Consultor funcional"),
        T(0, "Plan de actualización y calendario de parada", 0.5, 1.5, 16, role = "Jefe de proyecto", after = listOf("Reunión de lanzamiento de la actualización")),

        T(1, "Impacto en parametrización y catálogo", 1.0, 2.5, 24, role = "Consultor funcional", after = listOf("Revisión de notas de versión y novedades")),
        T(1, "Impacto en interfaces HL7 con el HIS", 1.0, 2.5, 16, role = "Ingeniero de integración", after = listOf("Revisión de notas de versión y novedades")),
        T(1, "Compatibilidad de drivers y middleware de analizadores", 1.0, 2.5, 16, role = "Ingeniero de integración", after = listOf("Revisión de notas de versión y novedades")),
        T(1, "Requisitos de infraestructura (servidores, SO, BD)", 1.0, 2.0, 8, role = "Técnico de sistemas"),
        T(
            1, "Aprobación del análisis de impacto", 2.5, 2.5, 2, milestone = true,
            after = listOf("Impacto en parametrización y catálogo", "Impacto en interfaces HL7 con el HIS", "Compatibilidad de drivers y middleware de analizadores"),
        ),

        T(2, "Preparación del entorno de pruebas", 2.0, 3.0, 16, role = "Técnico de sistemas", after = listOf("Requisitos de infraestructura (servidores, SO, BD)")),
        T(2, "Copia de la base de datos de producción", 3.0, 3.5, 8, role = "Técnico de sistemas", after = listOf("Preparación del entorno de pruebas")),
        T(2, "Instalación de la nueva versión en pruebas", 3.5, 4.0, 8, role = "Técnico de sistemas", after = listOf("Copia de la base de datos de producción")),

        T(3, "Ajustes de parametrización y catálogo", 4.0, 6.0, 40, role = "Consultor funcional", after = listOf("Instalación de la nueva versión en pruebas", "Aprobación del análisis de impacto")),
        T(3, "Revisión de informes, etiquetas y plantillas", 4.5, 6.0, 24, role = "Consultor funcional", after = listOf("Instalación de la nueva versión en pruebas")),
        T(3, "Revisión de reglas de autovalidación", 5.0, 6.5, 16, role = "Consultor funcional", after = listOf("Instalación de la nueva versión en pruebas")),

        T(4, "Actualización de drivers y middleware", 4.0, 5.5, 24, role = "Ingeniero de integración", after = listOf("Instalación de la nueva versión en pruebas")),
        T(4, "Pruebas de comunicación con analizadores", 5.5, 7.0, 32, role = "Ingeniero de integración", after = listOf("Actualización de drivers y middleware")),
        T(4, "Pruebas de interfaces HL7 con el HIS", 5.0, 7.0, 24, role = "Ingeniero de integración", after = listOf("Instalación de la nueva versión en pruebas")),

        T(5, "Plan de pruebas de regresión", 4.0, 5.0, 8, role = "Responsable de calidad", after = listOf("Aprobación del análisis de impacto")),
        T(
            5, "Pruebas de regresión funcional", 6.5, 8.5, 40, role = "Consultor funcional",
            after = listOf("Ajustes de parametrización y catálogo", "Revisión de reglas de autovalidación", "Plan de pruebas de regresión"),
        ),
        T(
            5, "Validación por el laboratorio (UAT)", 8.5, 9.5, 32, role = "Responsable de laboratorio",
            after = listOf("Pruebas de regresión funcional", "Pruebas de comunicación con analizadores", "Pruebas de interfaces HL7 con el HIS"),
        ),
        T(5, "Documentación de validación (ISO 15189)", 9.0, 10.0, 12, role = "Responsable de calidad"),
        T(
            5, "Aprobación para el paso a producción", 10.0, 10.0, 2, milestone = true,
            after = listOf("Validación por el laboratorio (UAT)", "Documentación de validación (ISO 15189)"),
        ),

        T(6, "Guía de novedades para usuarios", 8.0, 9.0, 12, role = "Formador", after = listOf("Revisión de informes, etiquetas y plantillas")),
        T(6, "Formación de usuarios clave", 9.0, 10.5, 16, role = "Formador", after = listOf("Guía de novedades para usuarios")),
        T(6, "Comunicación de la parada a servicios clínicos", 10.0, 11.0, 4, role = "Jefe de proyecto"),

        T(7, "Plan de contingencia y vuelta atrás", 10.0, 11.0, 8, role = "Jefe de proyecto", after = listOf("Aprobación para el paso a producción")),
        T(7, "Copia de seguridad previa", 11.5, 11.8, 4, role = "Técnico de sistemas", after = listOf("Plan de contingencia y vuelta atrás")),
        T(7, INSTALL, 12.0, 12.0, 16, role = "Técnico de sistemas", after = listOf("Copia de seguridad previa", "Comunicación de la parada a servicios clínicos")),
        T(7, "Verificación post-instalación", 12.0, 12.0, 8, role = "Consultor funcional", after = listOf(INSTALL)),
        T(7, GO_LIVE, 12.0, 12.0, 0, milestone = true, after = listOf("Verificación post-instalación")),
    )

    private data class S(val name: String, val fromDay: Long, val toDay: Long, val hours: Int, val milestone: Boolean = false, val after: String)

    private val support = listOf(
        S("Soporte reforzado post-actualización", 1, 7, 40, after = GO_LIVE),
        S("Seguimiento de incidencias", 3, 21, 24, after = GO_LIVE),
        S("Cierre de la actualización", 28, 28, 2, milestone = true, after = "Seguimiento de incidencias"),
    )

    data class Plan(val phases: List<PhaseDef>, val tasks: List<Task>)

    fun generate(start: LocalDate, goLive: LocalDate, team: List<TeamMember> = emptyList()): Plan {
        val defs = phases.map { PhaseDef(name = it.name, colorIndex = it.color) }
        val totalDays = daysBetween(start, goLive).coerceAtLeast(14)
        val scale = totalDays / (REFERENCE_WEEKS * 7)
        fun day(week: Double) = start.plusDays((week * 7 * scale).roundToLong())
        fun assignee(role: String) = team.firstOrNull { it.role.equals(role, ignoreCase = true) }?.name.orEmpty()

        val planned = tasks.map { t ->
            // La instalación, la verificación y el hito caen el día de paso a producción indicado.
            val from = if (t.fromWeek >= REFERENCE_WEEKS) goLive else day(t.fromWeek)
            val to = if (t.milestone || t.toWeek <= t.fromWeek) from else day(t.toWeek).minusDays(1).let { if (it.isBefore(from)) from else it }
            Task(
                name = t.name, phase = phases[t.phase].category, phaseId = defs[t.phase].id,
                start = from, end = to, isMilestone = t.milestone,
                assignee = assignee(t.role), estimatedHours = t.hours.toDouble(),
            )
        }
        val post = support.map { s ->
            Task(
                name = s.name, phase = LisPhase.SOPORTE, phaseId = defs.last().id,
                start = goLive.plusDays(s.fromDay), end = goLive.plusDays(s.toDay),
                isMilestone = s.milestone, estimatedHours = s.hours.toDouble(), assignee = assignee("Jefe de proyecto"),
            )
        }
        val all = planned + post
        val ids = all.associate { it.name to it.id }
        val deps = tasks.associate { it.name to it.after } + support.associate { it.name to listOf(it.after) }
        return Plan(defs, all.map { t -> t.copy(dependsOn = deps[t.name].orEmpty().mapNotNull { ids[it] }) })
    }
}

/** Aplica la plantilla que corresponda al tipo de proyecto. */
object ProjectTemplates {
    /** Genera el plan del tipo de proyecto repartido en las fases por defecto (Consultoría, Sistemas…). */
    fun apply(project: Project): Project {
        val tasks = when (project.type) {
            ProjectType.IMPLANTACION -> LisTemplate.generate(project.startDate, project.goLiveDate, project.team)
            ProjectType.ACTUALIZACION -> UpgradeTemplate.generate(project.startDate, project.goLiveDate, project.team).tasks
        }
        val (phases, ordered) = DefaultPhases.assign(tasks)
        return project.copy(phases = phases, tasks = ordered)
    }

    fun summary(type: ProjectType) = when (type) {
        ProjectType.IMPLANTACION -> "Crea ~40 tareas estándar de implantación repartidas en las 6 fases, ajustadas a las fechas."
        ProjectType.ACTUALIZACION -> "Crea ~33 tareas de actualización de versión (impacto, regresión, paso a producción…) en las 6 fases."
    }
}
