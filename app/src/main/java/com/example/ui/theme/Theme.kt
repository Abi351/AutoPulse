package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AutoPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AutoPulseTheme(
    accentColor: AccentThemeColor = AccentThemeColor.RED,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(accentColor) {
        darkColorScheme(
            primary = accentColor.primary,
            onPrimary = accentColor.onPrimary,
            primaryContainer = accentColor.container,
            onPrimaryContainer = accentColor.softAccent,
            secondary = accentColor.softAccent,
            onSecondary = Color(0xFF111118),
            secondaryContainer = CarbonSurfaceBright,
            onSecondaryContainer = TextPrimary,
            tertiary = EmeraldOk,
            onTertiary = Color(0xFF002116),
            background = ObsidianBg,
            onBackground = TextPrimary,
            surface = CarbonSurface,
            onSurface = TextPrimary,
            surfaceVariant = CarbonSurfaceElevated,
            onSurfaceVariant = TextSecondary,
            outline = accentColor.border,
            outlineVariant = Color(0xFF24242F),
            error = CrimsonAlert,
            onError = Color.White,
            errorContainer = CrimsonContainer,
            onErrorContainer = CoralAccent,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutoPulseTypography,
        shapes = AutoPulseShapes,
        content = content,
    )
}
