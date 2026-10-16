package com.lisplanner.app.data

import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * Plantilla estándar de implantación de un LIS.
 *
 * Las tareas están definidas en semanas sobre un proyecto de referencia de [REFERENCE_WEEKS]
 * semanas hasta el arranque y se escalan a la duración real (inicio → go-live).
 * Las tareas de soporte se definen en días relativos al go-live y no se escalan.
 */
object LisTemplate {
    private const val REFERENCE_WEEKS = 24.0

    private data class T(
        val phase: LisPhase,
        val name: String,
        val fromWeek: Double,
        val toWeek: Double,
        val milestone: Boolean = false,
        val role: String = "",
    )

    private data class S(val name: String, val fromDay: Long, val toDay: Long, val milestone: Boolean = false)

    private val tasks = listOf(
        T(LisPhase.KICKOFF, "Reunión de kick-off", 0.0, 0.0, milestone = true),
        T(LisPhase.KICKOFF, "Plan de proyecto y cronograma", 0.0, 1.0, role = "Jefe de proyecto"),
        T(LisPhase.KICKOFF, "Constitución de equipo y comités", 0.2, 1.2, role = "Jefe de proyecto"),

        T(LisPhase.ANALISIS, "Levantamiento de procesos pre y postanalíticos", 1.0, 3.0, role = "Consultor funcional"),
        T(LisPhase.ANALISIS, "Inventario de analizadores y conexiones", 1.0, 2.5, role = "Ingeniero de integración"),
        T(LisPhase.ANALISIS, "Catálogo de pruebas, perfiles y paneles", 2.0, 4.0, role = "Consultor funcional"),
        T(LisPhase.ANALISIS, "Requisitos de informes, etiquetas y validación", 3.0, 4.5, role = "Consultor funcional"),
        T(LisPhase.ANALISIS, "Aprobación del documento de análisis", 5.0, 5.0, milestone = true),

        T(LisPhase.CONFIGURACION, "Instalación de servidores y entornos", 3.0, 5.0, role = "Técnico de sistemas"),
        T(LisPhase.CONFIGURACION, "Parametrización del catálogo de pruebas", 5.0, 9.0, role = "Consultor funcional"),
        T(LisPhase.CONFIGURACION, "Rangos de referencia, unidades y valores críticos", 7.0, 10.0, role = "Consultor funcional"),
        T(LisPhase.CONFIGURACION, "Reglas de autovalidación y delta check", 9.0, 12.0, role = "Consultor funcional"),
        T(LisPhase.CONFIGURACION, "Plantillas de informes y etiquetas", 8.0, 11.0, role = "Consultor funcional"),
        T(LisPhase.CONFIGURACION, "Usuarios, roles y permisos", 10.0, 11.0, role = "Técnico de sistemas"),

        T(LisPhase.ANALIZADORES, "Configuración de middleware y drivers", 8.0, 12.0, role = "Ingeniero de integración"),
        T(LisPhase.ANALIZADORES, "Conexión bioquímica e inmunoquímica", 10.0, 14.0, role = "Ingeniero de integración"),
        T(LisPhase.ANALIZADORES, "Conexión hematología y coagulación", 11.0, 15.0, role = "Ingeniero de integración"),
        T(LisPhase.ANALIZADORES, "Conexión microbiología, orinas y gasometría", 13.0, 16.0, role = "Ingeniero de integración"),
        T(LisPhase.ANALIZADORES, "Pruebas de comunicación bidireccional (host query)", 15.0, 17.0, role = "Ingeniero de integración"),

        T(LisPhase.INTERFACES, "Especificación de mensajería HL7 con el HIS", 5.0, 7.0, role = "Ingeniero de integración"),
        T(LisPhase.INTERFACES, "Interfaz de peticiones (OML/ORM)", 8.0, 12.0, role = "Ingeniero de integración"),
        T(LisPhase.INTERFACES, "Interfaz de resultados (ORU)", 10.0, 14.0, role = "Ingeniero de integración"),
        T(LisPhase.INTERFACES, "Pruebas integradas HIS ↔ LIS", 14.0, 17.0, role = "Ingeniero de integración"),

        T(LisPhase.MIGRACION, "Mapeo de datos históricos", 9.0, 12.0, role = "Técnico de sistemas"),
        T(LisPhase.MIGRACION, "Extracción y carga de históricos", 12.0, 16.0, role = "Técnico de sistemas"),
        T(LisPhase.MIGRACION, "Verificación de datos migrados", 16.0, 18.0, role = "Responsable de laboratorio"),

        T(LisPhase.VALIDACION, "Plan y protocolo de validación", 11.0, 12.0, role = "Responsable de calidad"),
        T(LisPhase.VALIDACION, "Pruebas unitarias de configuración", 14.0, 17.0, role = "Consultor funcional"),
        T(LisPhase.VALIDACION, "Pruebas integradas end-to-end", 17.0, 20.0, role = "Responsable de laboratorio"),
        T(LisPhase.VALIDACION, "Validación por el laboratorio (UAT)", 19.0, 21.0, role = "Responsable de laboratorio"),
        T(LisPhase.VALIDACION, "Aprobación para el arranque", 21.0, 21.0, milestone = true),

        T(LisPhase.FORMACION, "Material formativo y manuales", 16.0, 19.0, role = "Formador"),
        T(LisPhase.FORMACION, "Formación de usuarios clave", 17.0, 19.0, role = "Formador"),
        T(LisPhase.FORMACION, "Formación de usuarios finales", 20.0, 23.0, role = "Formador"),

        T(LisPhase.ARRANQUE, "Plan de contingencia y vuelta atrás", 21.0, 22.0, role = "Jefe de proyecto"),
        T(LisPhase.ARRANQUE, "Simulacro de arranque", 22.0, 23.0, role = "Jefe de proyecto"),
        T(LisPhase.ARRANQUE, "Go-Live", 24.0, 24.0, milestone = true),
    )

    /** Esfuerzo estimado de referencia (horas) para cada tarea de la plantilla. */
    private val hours = mapOf(
        "Reunión de kick-off" to 8, "Plan de proyecto y cronograma" to 24, "Constitución de equipo y comités" to 8,
        "Levantamiento de procesos pre y postanalíticos" to 80, "Inventario de analizadores y conexiones" to 24,
        "Catálogo de pruebas, perfiles y paneles" to 60, "Requisitos de informes, etiquetas y validación" to 40,
        "Aprobación del documento de análisis" to 4,
        "Instalación de servidores y entornos" to 40, "Parametrización del catálogo de pruebas" to 160,
        "Rangos de referencia, unidades y valores críticos" to 60, "Reglas de autovalidación y delta check" to 80,
        "Plantillas de informes y etiquetas" to 48, "Usuarios, roles y permisos" to 16,
        "Configuración de middleware y drivers" to 60, "Conexión bioquímica e inmunoquímica" to 64,
        "Conexión hematología y coagulación" to 48, "Conexión microbiología, orinas y gasometría" to 56,
        "Pruebas de comunicación bidireccional (host query)" to 32,
        "Especificación de mensajería HL7 con el HIS" to 32, "Interfaz de peticiones (OML/ORM)" to 80,
        "Interfaz de resultados (ORU)" to 64, "Pruebas integradas HIS ↔ LIS" to 48,
        "Mapeo de datos históricos" to 40, "Extracción y carga de históricos" to 56, "Verificación de datos migrados" to 32,
        "Plan y protocolo de validación" to 24, "Pruebas unitarias de configuración" to 80, "Pruebas integradas end-to-end" to 64,
        "Validación por el laboratorio (UAT)" to 80, "Aprobación para el arranque" to 4,
        "Material formativo y manuales" to 40, "Formación de usuarios clave" to 32, "Formación de usuarios finales" to 80,
        "Plan de contingencia y vuelta atrás" to 16, "Simulacro de arranque" to 24, "Go-Live" to 40,
        "Soporte in situ (hypercare)" to 120, "Seguimiento de incidencias y ajustes" to 60, "Cierre del proyecto y lecciones aprendidas" to 8,
    )

    /** Dependencias fin → inicio habituales en una implantación (por nombre de tarea). */
    private val dependencies = mapOf(
        "Plan de proyecto y cronograma" to listOf("Reunión de kick-off"),
        "Aprobación del documento de análisis" to listOf("Catálogo de pruebas, perfiles y paneles", "Requisitos de informes, etiquetas y validación"),
        "Parametrización del catálogo de pruebas" to listOf("Aprobación del documento de análisis"),
        "Conexión bioquímica e inmunoquímica" to listOf("Instalación de servidores y entornos"),
        "Pruebas de comunicación bidireccional (host query)" to listOf("Conexión bioquímica e inmunoquímica"),
        "Interfaz de peticiones (OML/ORM)" to listOf("Especificación de mensajería HL7 con el HIS"),
        "Pruebas integradas HIS ↔ LIS" to listOf("Interfaz de peticiones (OML/ORM)"),
        "Extracción y carga de históricos" to listOf("Mapeo de datos históricos"),
        "Verificación de datos migrados" to listOf("Extracción y carga de históricos"),
        "Pruebas integradas end-to-end" to listOf("Pruebas integradas HIS ↔ LIS", "Pruebas de comunicación bidireccional (host query)"),
        "Validación por el laboratorio (UAT)" to listOf("Pruebas unitarias de configuración"),
        "Aprobación para el arranque" to listOf("Validación por el laboratorio (UAT)", "Verificación de datos migrados"),
        "Formación de usuarios finales" to listOf("Formación de usuarios clave"),
        "Simulacro de arranque" to listOf("Aprobación para el arranque"),
        "Go-Live" to listOf("Simulacro de arranque", "Formación de usuarios finales"),
        "Soporte in situ (hypercare)" to listOf("Go-Live"),
        "Cierre del proyecto y lecciones aprendidas" to listOf("Seguimiento de incidencias y ajustes"),
    )

    private val support = listOf(
        S("Soporte in situ (hypercare)", 0, 14),
        S("Seguimiento de incidencias y ajustes", 7, 28),
        S("Cierre del proyecto y lecciones aprendidas", 35, 35, milestone = true),
    )

    fun generate(start: LocalDate, goLive: LocalDate, team: List<TeamMember> = emptyList()): List<Task> {
        val totalDays = daysBetween(start, goLive).coerceAtLeast(14)
        val scale = totalDays / (REFERENCE_WEEKS * 7)
        fun day(week: Double) = start.plusDays((week * 7 * scale).roundToLong())
        fun assignee(role: String) = team.firstOrNull { it.role.equals(role, ignoreCase = true) }?.name.orEmpty()

        val planned = tasks.map { t ->
            val from = if (t.phase == LisPhase.ARRANQUE && t.milestone) goLive else day(t.fromWeek)
            val to = if (t.milestone) from else day(t.toWeek).minusDays(1).coerceAtLeast(from)
            Task(
                name = t.name, phase = t.phase, start = from, end = to,
                isMilestone = t.milestone, assignee = assignee(t.role),
            )
        }
        val post = support.map { s ->
            Task(
                name = s.name, phase = LisPhase.SOPORTE,
                start = goLive.plusDays(s.fromDay), end = goLive.plusDays(s.toDay),
                isMilestone = s.milestone, assignee = assignee("Jefe de proyecto"),
            )
        }
        val all = planned + post
        val idByName = all.associate { it.name to it.id }
        return all.map { t ->
            t.copy(
                dependsOn = dependencies[t.name].orEmpty().mapNotNull { idByName[it] },
                estimatedHours = hours[t.name]?.toDouble() ?: 0.0,
            )
        }
    }

    val defaultRoles = listOf(
        "Jefe de proyecto",
        "Consultor funcional",
        "Ingeniero de integración",
        "Técnico de sistemas",
        "Responsable de laboratorio",
        "Responsable de calidad",
        "Formador",
    )
}

private fun LocalDate.coerceAtLeast(other: LocalDate) = if (isBefore(other)) other else this

/** Datos de ejemplo para la primera ejecución. */
object SampleData {
    fun projects(today: LocalDate = today()): List<Project> =
        listOf(valleNorte(today), actualizacionSierra(today), redCosta(today), clinicaMar(today))
            .map { p -> DefaultPhases.assign(p.tasks).let { (phases, tasks) -> p.copy(phases = phases, tasks = tasks) } }

    private fun withProgress(
        tasks: List<Task>,
        today: LocalDate,
        laggards: Set<String> = emptySet(),
        replanned: Set<String> = emptySet(),
    ): List<Task> =
        tasks.map { t0 ->
            // Las tareas replanificadas se alargan más allá de hoy y chocan con sus sucesoras.
            val t = if (t0.name in replanned) t0.copy(end = today.plusDays(10)) else t0
            val p = when {
                t.name in laggards -> 40
                t.name in replanned -> 60
                t.end.isBefore(today) -> 100
                t.start.isAfter(today) -> 0
                else -> {
                    val done = daysBetween(t.start, today) + 1
                    (done * 100 / t.durationDays).toInt().coerceIn(5, 95)
                }
            }
            // Horas imputadas proporcionales al avance, con una desviación realista según la tarea.
            val deviation = if (t.name in laggards || t.name in replanned) 1.3 else 0.85 + (t.name.hashCode() and 0xff) / 255.0 * 0.35
            t.copy(progress = p, spentHours = (t.estimatedHours * p / 100.0 * deviation).roundToLong().toDouble())
        }

    private fun valleNorte(today: LocalDate): Project {
        val team = listOf(
            TeamMember(name = "Laura Gómez", role = "Jefe de proyecto"),
            TeamMember(name = "Andrés Ruiz", role = "Consultor funcional"),
            TeamMember(name = "Marta Iglesias", role = "Ingeniero de integración"),
            TeamMember(name = "Javier Ortega", role = "Técnico de sistemas"),
            TeamMember(name = "Dra. Carmen Vidal", role = "Responsable de laboratorio"),
            TeamMember(name = "Pablo Serrano", role = "Responsable de calidad"),
            TeamMember(name = "Elena Navarro", role = "Formador"),
        )
        val start = today.minusWeeks(11)
        val goLive = today.plusWeeks(13)
        val tasks = withProgress(
            LisTemplate.generate(start, goLive, team), today,
            laggards = setOf("Inventario de analizadores y conexiones", "Requisitos de informes, etiquetas y validación"),
            replanned = setOf("Especificación de mensajería HL7 con el HIS"),
        )
        return Project(
            name = "Implantación LIS – H. U. Valle Norte",
            client = "Hospital Universitario Valle Norte",
            site = "Laboratorio Core + Microbiología",
            lisSystem = "LIS corporativo v8",
            projectManager = "Laura Gómez",
            startDate = start, goLiveDate = goLive,
            status = ProjectStatus.EN_RIESGO,
            description = "Sustitución del LIS actual en el laboratorio de respuesta rápida, core y microbiología. " +
                "Integración con HIS mediante HL7 v2.5 y conexión de 14 analizadores.",
            tasks = tasks,
            team = team,
            risks = listOf(
                RiskItem(
                    kind = RiskKind.INCIDENCIA, title = "Especificación HL7 del HIS sin cerrar",
                    description = "El proveedor del HIS no ha entregado la definición de los segmentos Z. Bloquea la interfaz de peticiones.",
                    impact = Level.ALTA, status = RiskStatus.EN_GESTION, owner = "Marta Iglesias",
                    mitigation = "Escalado al comité de dirección. Reunión técnica semanal con el proveedor del HIS.",
                    raised = today.minusWeeks(2), due = today.plusDays(7),
                ),
                RiskItem(
                    title = "Disponibilidad de usuarios clave para la UAT",
                    description = "Coincide con el periodo vacacional de diciembre.",
                    probability = Level.ALTA, impact = Level.ALTA, owner = "Dra. Carmen Vidal",
                    mitigation = "Reservar agendas ya y planificar la UAT por turnos.",
                    raised = today.minusWeeks(3), due = today.plusWeeks(5),
                ),
                RiskItem(
                    title = "Retraso en la entrega del cableado de microbiología",
                    probability = Level.MEDIA, impact = Level.MEDIA, owner = "Javier Ortega",
                    mitigation = "Conexión provisional por concentrador serie-TCP.",
                    raised = today.minusWeeks(1),
                ),
                RiskItem(
                    title = "Calidad de los datos históricos a migrar",
                    probability = Level.MEDIA, impact = Level.ALTA, owner = "Javier Ortega",
                    mitigation = "Muestreo de 500 pacientes y validación cruzada con el laboratorio.",
                    raised = today.minusWeeks(6),
                ),
                RiskItem(
                    kind = RiskKind.INCIDENCIA, title = "Licencias de middleware recibidas con retraso",
                    impact = Level.MEDIA, status = RiskStatus.CERRADO, owner = "Laura Gómez",
                    raised = today.minusWeeks(8),
                ),
            ),
            integrations = listOf(
                Integration(name = "cobas pro integrated solutions", type = IntegrationType.ANALIZADOR, manufacturer = "Roche", protocol = Protocol.ASTM, area = "Bioquímica", status = IntegrationStatus.PRUEBAS),
                Integration(name = "Atellica Solution", type = IntegrationType.ANALIZADOR, manufacturer = "Siemens Healthineers", protocol = Protocol.LIS2A2, area = "Inmunoquímica", status = IntegrationStatus.CONFIGURANDO),
                Integration(name = "Sysmex XN-3100", type = IntegrationType.ANALIZADOR, manufacturer = "Sysmex", protocol = Protocol.ASTM, area = "Hematología", status = IntegrationStatus.VALIDADO),
                Integration(name = "ACL TOP 750", type = IntegrationType.ANALIZADOR, manufacturer = "Werfen", protocol = Protocol.ASTM, area = "Coagulación", status = IntegrationStatus.PENDIENTE),
                Integration(name = "VITEK 2", type = IntegrationType.ANALIZADOR, manufacturer = "bioMérieux", protocol = Protocol.ASTM, area = "Microbiología", status = IntegrationStatus.PENDIENTE),
                Integration(name = "Middleware de área", type = IntegrationType.MIDDLEWARE, manufacturer = "Proveedor LIS", protocol = Protocol.ASTM, area = "Core", status = IntegrationStatus.PRUEBAS),
                Integration(name = "HIS – Peticiones (OML^O21)", type = IntegrationType.HIS, manufacturer = "Proveedor HIS", protocol = Protocol.HL7V2, status = IntegrationStatus.BLOQUEADO, notes = "Pendiente de la especificación de segmentos Z del HIS."),
                Integration(name = "HIS – Resultados (ORU^R01)", type = IntegrationType.HIS, manufacturer = "Proveedor HIS", protocol = Protocol.HL7V2, status = IntegrationStatus.CONFIGURANDO),
            ),
        )
    }

    private fun actualizacionSierra(today: LocalDate): Project {
        val team = listOf(
            TeamMember(name = "Nuria Pascual", role = "Jefe de proyecto"),
            TeamMember(name = "Andrés Ruiz", role = "Consultor funcional"),
            TeamMember(name = "Marta Iglesias", role = "Ingeniero de integración"),
            TeamMember(name = "Javier Ortega", role = "Técnico de sistemas"),
            TeamMember(name = "Dr. Tomás Gil", role = "Responsable de laboratorio"),
            TeamMember(name = "Pablo Serrano", role = "Responsable de calidad"),
            TeamMember(name = "Elena Navarro", role = "Formador"),
        )
        val start = today.minusWeeks(4)
        val goLive = today.plusWeeks(8)
        val plan = UpgradeTemplate.generate(start, goLive, team)
        return Project(
            name = "Actualización LIS 8.4 → 9.1 – H. Comarcal de la Sierra",
            client = "Hospital Comarcal de la Sierra",
            site = "Laboratorio de urgencias y rutina",
            lisSystem = "LIS corporativo",
            projectManager = "Nuria Pascual",
            startDate = start, goLiveDate = goLive,
            status = ProjectStatus.EN_CURSO,
            type = ProjectType.ACTUALIZACION,
            currentVersion = "8.4", targetVersion = "9.1",
            description = "Actualización de versión con cambio de servidor de base de datos y nuevos drivers de analizadores.",
            phases = plan.phases,
            tasks = withProgress(plan.tasks, today),
            team = team,
            risks = listOf(
                RiskItem(
                    title = "Driver del analizador de orinas no certificado para la 9.1",
                    probability = Level.MEDIA, impact = Level.ALTA, owner = "Marta Iglesias",
                    mitigation = "Solicitar al fabricante la versión certificada; plan B: middleware intermedio.",
                    raised = today.minusWeeks(1), due = today.plusWeeks(3),
                ),
            ),
        )
    }

    private fun redCosta(today: LocalDate): Project {
        val team = listOf(
            TeamMember(name = "Sergio Campos", role = "Jefe de proyecto"),
            TeamMember(name = "Lucía Martín", role = "Consultor funcional"),
            TeamMember(name = "Raúl Prieto", role = "Ingeniero de integración"),
        )
        val start = today.plusWeeks(2)
        val goLive = start.plusWeeks(30)
        return Project(
            name = "LIS Red de Laboratorios Costa",
            client = "Red Sanitaria Costa",
            site = "Laboratorio central + 4 periféricos",
            lisSystem = "LIS corporativo v8",
            projectManager = "Sergio Campos",
            startDate = start, goLiveDate = goLive,
            status = ProjectStatus.PLANIFICACION,
            description = "Consolidación de cuatro laboratorios periféricos en un único LIS multicentro con peticionario electrónico para atención primaria.",
            tasks = LisTemplate.generate(start, goLive, team),
            team = team,
            integrations = listOf(
                Integration(name = "Peticionario de Atención Primaria", type = IntegrationType.PETICIONARIO, protocol = Protocol.HL7V2),
                Integration(name = "HCE regional", type = IntegrationType.HIS, protocol = Protocol.FHIR),
            ),
        )
    }

    private fun clinicaMar(today: LocalDate): Project {
        val team = listOf(TeamMember(name = "Laura Gómez", role = "Jefe de proyecto"), TeamMember(name = "Marta Iglesias", role = "Ingeniero de integración"))
        val start = today.minusWeeks(30)
        val goLive = today.minusWeeks(8)
        return Project(
            name = "Ampliación POCT – Clínica del Mar",
            client = "Clínica del Mar",
            site = "Urgencias y UCI",
            lisSystem = "LIS corporativo v8",
            projectManager = "Laura Gómez",
            startDate = start, goLiveDate = goLive,
            status = ProjectStatus.COMPLETADO,
            description = "Integración de gasómetros y dispositivos POCT en el LIS.",
            tasks = withProgress(LisTemplate.generate(start, goLive, team), today),
            team = team,
            integrations = listOf(
                Integration(name = "Gasómetros GEM Premier", type = IntegrationType.ANALIZADOR, manufacturer = "Werfen", protocol = Protocol.POCT1A, area = "Urgencias", status = IntegrationStatus.VALIDADO),
                Integration(name = "cobas b 123", type = IntegrationType.ANALIZADOR, manufacturer = "Roche", protocol = Protocol.POCT1A, area = "UCI", status = IntegrationStatus.VALIDADO),
            ),
        )
    }
}
