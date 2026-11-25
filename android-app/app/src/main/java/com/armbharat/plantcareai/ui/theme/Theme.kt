package com.armbharat.plantcareai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PlantCareLightColors = lightColorScheme(
    primary = PlantGreen,
    onPrimary = CardWhite,
    secondary = Terracotta,
    onSecondary = CardWhite,
    tertiary = SeverityModerate,
    onTertiary = Charcoal,
    background = CanvasBackground,
    onBackground = Charcoal,
    surface = CardWhite,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFECEAE3),
    onSurfaceVariant = SecondaryText,
    outline = BorderColor
)

private val PlantCareDarkColors = darkColorScheme(
    primary = Color(0xFF9AD89B),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF205329),
    onPrimaryContainer = Color(0xFFB7F3B6),

    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF542110),
    secondaryContainer = Color(0xFF703626),
    onSecondaryContainer = Color(0xFFFFDBD0),

    tertiary = Color(0xFFFFC76F),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF5D420D),
    onTertiaryContainer = Color(0xFFFFE1A8),

    background = Color(0xFF141814),
    onBackground = Color(0xFFE1E6DD),
    surface = Color(0xFF141814),
    onSurface = Color(0xFFE1E6DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainerLow = Color(0xFF1C211C),
    surfaceContainer = Color(0xFF202520),
    surfaceContainerHigh = Color(0xFF2A302A),
    outline = Color(0xFF8C968A),
    outlineVariant = Color(0xFF414941)
)

@Composable
fun PlantCareAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PlantCareDarkColors else PlantCareLightColors,
        typography = Typography,
        content = content
    )
}