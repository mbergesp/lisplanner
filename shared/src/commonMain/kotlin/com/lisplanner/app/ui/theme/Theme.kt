package com.lisplanner.app.ui.theme

import com.lisplanner.app.data.*

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lisplanner.app.data.IntegrationStatus
import com.lisplanner.app.data.LisPhase
import com.lisplanner.app.data.ProjectStatus
import com.lisplanner.app.data.TaskStatus

private val Light = lightColorScheme(
    primary = Color(0xFF0B5C7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E7F5),
    onPrimaryContainer = Color(0xFF002233),
    secondary = Color(0xFF16867A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2EFE7),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFB4610B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFEAEEF4),
    surfaceContainerHigh = Color(0xFFE4E8EE),
    surfaceContainerHighest = Color(0xFFDEE3E9),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86CFEF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D67),
    onPrimaryContainer = Color(0xFFC6E7F5),
    secondary = Color(0xFF7FD8CA),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFC2EFE7),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00),
    onTertiaryContainer = Color(0xFFFFDCC0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF252B2F),
    surfaceContainerHighest = Color(0xFF30363A),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun LisPlannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

val KpiNumberStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

/* ---------- Colores semánticos del dominio ---------- */

val PhaseColorsLight = listOf(
    0xFF5C6BC0, 0xFF0288D1, 0xFF00897B, 0xFF7CB342, 0xFF8E24AA,
    0xFF6D4C41, 0xFFF4511E, 0xFFC0CA33, 0xFFD81B60, 0xFF546E7A,
).map(::Color)
private val phaseDark = listOf(
    0xFF9FA8DA, 0xFF4FC3F7, 0xFF4DB6AC, 0xFFAED581, 0xFFCE93D8,
    0xFFBCAAA4, 0xFFFF8A65, 0xFFDCE775, 0xFFF48FB1, 0xFF90A4AE,
).map(::Color)

@Composable
@ReadOnlyComposable
fun phaseColor(phase: LisPhase): Color = groupColor(phase.ordinal)

/** Color de un grupo de tareas (fase LIS o tarea resumen importada). */
@Composable
@ReadOnlyComposable
fun groupColor(index: Int): Color =
    (if (isSystemInDarkTheme()) phaseDark else PhaseColorsLight)[index % PhaseColorsLight.size]

data class StatusColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
private fun tone(light: Long, dark: Long, lightBg: Long, darkBg: Long): StatusColors =
    if (isSystemInDarkTheme()) StatusColors(Color(darkBg), Color(dark)) else StatusColors(Color(lightBg), Color(light))

@Composable
@ReadOnlyComposable
private fun green() = tone(0xFF1B6E3A, 0xFF8FD9A3, 0xFFD5F5DE, 0xFF12361F)

@Composable
@ReadOnlyComposable
private fun blue() = tone(0xFF0B5C7A, 0xFF86CFEF, 0xFFD3ECF7, 0xFF0D3344)

@Composable
@ReadOnlyComposable
private fun amber() = tone(0xFF8A4B00, 0xFFFFC27F, 0xFFFFE8CC, 0xFF3D2504)

@Composable
@ReadOnlyComposable
private fun red() = tone(0xFFB3261E, 0xFFFFB4AB, 0xFFFDE0DC, 0xFF4A1512)

@Composable
@ReadOnlyComposable
private fun grey() = tone(0xFF4A5258, 0xFFC1C7CE, 0xFFE4E8EC, 0xFF2A3034)

@Composable
@ReadOnlyComposable
fun ProjectStatus.colors(): StatusColors = when (this) {
    ProjectStatus.PLANIFICACION -> grey()
    ProjectStatus.EN_CURSO -> blue()
    ProjectStatus.EN_RIESGO -> red()
    ProjectStatus.PAUSADO -> amber()
    ProjectStatus.COMPLETADO -> green()
}

@Composable
@ReadOnlyComposable
fun TaskStatus.colors(): StatusColors = when (this) {
    TaskStatus.PENDIENTE -> grey()
    TaskStatus.EN_CURSO -> blue()
    TaskStatus.ATRASADA -> red()
    TaskStatus.COMPLETADA -> green()
}

@Composable
@ReadOnlyComposable
fun IntegrationStatus.colors(): StatusColors = when (this) {
    IntegrationStatus.PENDIENTE -> grey()
    IntegrationStatus.CONFIGURANDO -> amber()
    IntegrationStatus.PRUEBAS -> blue()
    IntegrationStatus.VALIDADO -> green()
    IntegrationStatus.BLOQUEADO -> red()
}

@Composable
@ReadOnlyComposable
private fun orange() = tone(0xFFB3500B, 0xFFFFB68A, 0xFFFFE1CF, 0xFF45230A)

@Composable
@ReadOnlyComposable
fun com.lisplanner.app.data.Severity.colors(): StatusColors = when (this) {
    com.lisplanner.app.data.Severity.BAJA -> green()
    com.lisplanner.app.data.Severity.MEDIA -> amber()
    com.lisplanner.app.data.Severity.ALTA -> orange()
    com.lisplanner.app.data.Severity.CRITICA -> red()
}

@Composable
@ReadOnlyComposable
fun com.lisplanner.app.data.RiskStatus.colors(): StatusColors = when (this) {
    com.lisplanner.app.data.RiskStatus.ABIERTO -> red()
    com.lisplanner.app.data.RiskStatus.EN_GESTION -> blue()
    com.lisplanner.app.data.RiskStatus.CERRADO -> green()
}
