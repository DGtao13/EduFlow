package com.eduflow.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EduFlowLightColors = lightColorScheme(
    primary = EduFlowDeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2B6B),
    secondary = EduFlowTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8EEE7),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = EduFlowOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1B7),
    onTertiaryContainer = Color(0xFF3C2100),
    background = EduFlowBackground,
    onBackground = EduFlowOnSurface,
    surface = EduFlowSurface,
    onSurface = EduFlowOnSurface,
    surfaceVariant = EduFlowSurfaceVariant,
    onSurfaceVariant = EduFlowOnSurfaceVariant,
    outline = EduFlowOutline,
    outlineVariant = EduFlowOutlineVariant,
    surfaceContainerLowest = EduFlowSurfaceContainerLowest,
    surfaceContainerLow = EduFlowSurfaceContainerLow,
    surfaceContainer = EduFlowSurfaceContainer,
    surfaceContainerHigh = EduFlowSurfaceContainerHigh,
    surfaceContainerHighest = EduFlowSurfaceContainerHighest,
    error = EduFlowError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = EduFlowLightBlue
)

private val EduFlowDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF004A8F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF75D8C8),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFA2F0E0),
    tertiary = Color(0xFFFFB95F),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3A00),
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CD),
    outline = Color(0xFF8B9297),
    outlineVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF171B1F),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF252A2F),
    surfaceContainerHighest = Color(0xFF30353A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun EduFlowTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EduFlowDarkColors else EduFlowLightColors,
        content = content
    )
}
