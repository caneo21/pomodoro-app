package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.pomodoro.timer.core.ColorTheme

data class WearPalette(
    val primary: Color,
    val primaryVariant: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val longBreakAccent: Color
)

// The watch has no system dark-mode concept and its background is fixed at black regardless of
// theme (existing behavior) — so every theme here uses its phone dark-palette's brighter primary
// and accent (better contrast against black than the light-mode primary would give).
private val WearPalettes: Map<ColorTheme, WearPalette> = mapOf(
    ColorTheme.DEFAULT to WearPalette(
        primary = Color(0xFFE85D4A),
        primaryVariant = Color(0xFFB6432F),
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFF5C6BC0)
    ),
    ColorTheme.OCEAN to WearPalette(
        primary = Color(0xFF64B5F6),
        primaryVariant = Color(0xFF1E88E5),
        onPrimary = Color(0xFF00263D),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFF4DB6AC)
    ),
    ColorTheme.FOREST to WearPalette(
        primary = Color(0xFF81C784),
        primaryVariant = Color(0xFF43A047),
        onPrimary = Color(0xFF0B3B12),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFFFFB74D)
    ),
    ColorTheme.LAVENDER to WearPalette(
        primary = Color(0xFFB69CF0),
        primaryVariant = Color(0xFF8E5DE8),
        onPrimary = Color(0xFF2C0F5C),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFFF06292)
    )
)

fun wearPaletteFor(theme: ColorTheme): WearPalette = WearPalettes.getValue(theme)

fun wearColorScheme(palette: WearPalette): ColorScheme = ColorScheme(
    primary = palette.primary,
    primaryContainer = palette.primaryVariant,
    onPrimary = palette.onPrimary,
    background = palette.background,
    onBackground = palette.onBackground
)

@Composable
fun PomodoroWearTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val palette = wearPaletteFor(colorTheme)
    MaterialTheme(colorScheme = wearColorScheme(palette), content = content)
}
