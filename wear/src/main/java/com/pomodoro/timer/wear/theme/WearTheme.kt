package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import com.pomodoro.timer.core.ColorTheme

data class WearPalette(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val onBackground: Color
)

// Minimal-monochrome tokens (docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md,
// Section A). The watch is always dark (unchanged, battery-driven), so only the dark-bg accent
// column from the spec applies here — there's no light variant. Background/onBackground are
// identical across every theme; only the accent differs, which is the whole point of moving from
// a 2-tone (primary + primaryVariant) palette to a single restrained accent per theme.
private val WearNeutralBackground = Color(0xFF0D0D0F)
private val WearNeutralOnBackground = Color(0xFFF2F2F4)
val WearLongBreakAccent = Color(0xFF6B7BFF)

private val WearPalettes: Map<ColorTheme, WearPalette> = mapOf(
    ColorTheme.DEFAULT to WearPalette(
        accent = Color(0xFFFF6B57),
        onAccent = Color(0xFFFFFFFF),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.OCEAN to WearPalette(
        accent = Color(0xFF5AA9FF),
        onAccent = Color(0xFF00263D),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.FOREST to WearPalette(
        accent = Color(0xFF6FCB7A),
        onAccent = Color(0xFF0B3B12),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.LAVENDER to WearPalette(
        accent = Color(0xFFB08CF0),
        onAccent = Color(0xFF2C0F5C),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    )
)

fun wearPaletteFor(theme: ColorTheme): WearPalette = WearPalettes.getValue(theme)

fun wearColorScheme(palette: WearPalette): ColorScheme = ColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    background = palette.background,
    onBackground = palette.onBackground
)

@Composable
fun PomodoroWearTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val palette = wearPaletteFor(colorTheme)
    MaterialTheme(colorScheme = wearColorScheme(palette)) {
        CompositionLocalProvider(LocalContentColor provides palette.onBackground, content = content)
    }
}
