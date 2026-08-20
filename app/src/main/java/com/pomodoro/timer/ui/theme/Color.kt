package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme

data class PomodoroPalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val longBreakAccent: Color
)

private val LightPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFFFFFBF7), Color(0xFF5C6BC0)),
    ColorTheme.OCEAN to PomodoroPalette(Color(0xFF1E88E5), Color(0xFFFFFFFF), Color(0xFFF5FAFF), Color(0xFF26A69A)),
    ColorTheme.FOREST to PomodoroPalette(Color(0xFF43A047), Color(0xFFFFFFFF), Color(0xFFF6FBF4), Color(0xFFFB8C00)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFF8E5DE8), Color(0xFFFFFFFF), Color(0xFFFAF7FF), Color(0xFFEC407A))
)

private val DarkPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFF1C1614), Color(0xFF7986CB)),
    ColorTheme.OCEAN to PomodoroPalette(Color(0xFF64B5F6), Color(0xFF00263D), Color(0xFF0D1B24), Color(0xFF4DB6AC)),
    ColorTheme.FOREST to PomodoroPalette(Color(0xFF81C784), Color(0xFF0B3B12), Color(0xFF10190F), Color(0xFFFFB74D)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFFB69CF0), Color(0xFF2C0F5C), Color(0xFF16101F), Color(0xFFF06292))
)

fun paletteFor(theme: ColorTheme, darkTheme: Boolean): PomodoroPalette =
    (if (darkTheme) DarkPalettes else LightPalettes).getValue(theme)
