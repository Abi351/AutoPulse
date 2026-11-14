package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Thème Cockpit Automobile (Optimisé OLED & faible consommation 4 Go RAM)
val ObsidianBg = Color(0xFF08080B)
val CarbonSurface = Color(0xFF101016)
val CarbonSurfaceElevated = Color(0xFF181822)
val CarbonSurfaceBright = Color(0xFF231B20)
val CarbonBorder = Color(0xFF2B2B38)

val RacingRed = Color(0xFFFF2A3C)
val RacingRedDim = Color(0xFFC81E2E)
val DeepRedContainer = Color(0xFF3E0B13)

val CoralAccent = Color(0xFFFF7582)
val EmeraldOk = Color(0xFF10B981)
val AmberWarn = Color(0xFFF59E0B)
val CrimsonAlert = Color(0xFFEF4444)
val CrimsonContainer = Color(0xFF450A0A)

val TextPrimary = Color(0xFFF4F4F6)
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)

/**
 * Palette Arc-en-ciel (7 couleurs) configurable en temps réel dans les Réglages.
 */
enum class AccentThemeColor(
    val id: String,
    val label: String,
    val primary: Color,
    val softAccent: Color,
    val container: Color,
    val border: Color,
    val onPrimary: Color = Color.White,
) {
    RED(
        id = "RED",
        label = "Rouge Racing",
        primary = Color(0xFFFF2A3C),
        softAccent = Color(0xFFFF7582),
        container = Color(0xFF3E0B13),
        border = Color(0xFF4A1E24),
        onPrimary = Color.White,
    ),
    ORANGE(
        id = "ORANGE",
        label = "Orange",
        primary = Color(0xFFFF7A00),
        softAccent = Color(0xFFFFAD5C),
        container = Color(0xFF3D1D06),
        border = Color(0xFF4F2A10),
        onPrimary = Color.White,
    ),
    YELLOW(
        id = "YELLOW",
        label = "Jaune",
        primary = Color(0xFFFFD600),
        softAccent = Color(0xFFFFEA75),
        container = Color(0xFF382F05),
        border = Color(0xFF4B3F10),
        onPrimary = Color(0xFF0A0A0E),
    ),
    GREEN(
        id = "GREEN",
        label = "Vert",
        primary = Color(0xFF00E676),
        softAccent = Color(0xFF69F0AE),
        container = Color(0xFF063820),
        border = Color(0xFF144D31),
        onPrimary = Color(0xFF0A0A0E),
    ),
    BLUE(
        id = "BLUE",
        label = "Bleu",
        primary = Color(0xFF2979FF),
        softAccent = Color(0xFF82B1FF),
        container = Color(0xFF0A214A),
        border = Color(0xFF193463),
        onPrimary = Color.White,
    ),
    INDIGO(
        id = "INDIGO",
        label = "Indigo",
        primary = Color(0xFF536DFE),
        softAccent = Color(0xFF8C9EFF),
        container = Color(0xFF141B4A),
        border = Color(0xFF232C66),
        onPrimary = Color.White,
    ),
    VIOLET(
        id = "VIOLET",
        label = "Violet",
        primary = Color(0xFFD500F9),
        softAccent = Color(0xFFEA80FC),
        container = Color(0xFF3A0645),
        border = Color(0xFF4D1259),
        onPrimary = Color.White,
    );

    companion object {
        fun fromId(id: String?): AccentThemeColor =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RED
    }
}
