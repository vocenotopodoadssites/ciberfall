package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NeonfallColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidBlack,
    primaryContainer = CyberSurfaceElevated,
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = CyberSurface,
    onSecondaryContainer = NeonMagenta,
    tertiary = ElectricViolet,
    onTertiary = TextPrimary,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DeepSlate,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurface,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    error = CrimsonDanger,
    onError = TextPrimary
)

val NeonfallShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun NeonfallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonfallColorScheme,
        typography = NeonfallTypography,
        shapes = NeonfallShapes,
        content = content
    )
}
