package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme

data class PomodoroPalette(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val longBreakAccent: Color
)

// Shared minimal-monochrome tokens (docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md,
// Section A). Neutrals and long-break accent are identical across all 4 themes; only accent/onAccent
// vary per theme.
private val DarkNeutralBackground = Color(0xFF0D0D0F)
private val DarkNeutralSurface = Color(0xFF17171A)
private val DarkNeutralTextPrimary = Color(0xFFF2F2F4)
private val DarkNeutralTextSecondary = Color(0xFF8A8A92)
private val DarkNeutralBorder = Color(0xFF2A2A2E)
private val DarkLongBreakAccent = Color(0xFF6B7BFF)

private val LightNeutralBackground = Color(0xFFFAFAF9)
private val LightNeutralSurface = Color(0xFFFFFFFF)
private val LightNeutralTextPrimary = Color(0xFF17171A)
private val LightNeutralTextSecondary = Color(0xFF6B6B72)
private val LightNeutralBorder = Color(0xFFE4E4E7)
private val LightLongBreakAccent = Color(0xFF4A57C7)

private val LightPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(
        LightNeutralBackground, LightNeutralSurface, LightNeutralTextPrimary, LightNeutralTextSecondary,
        LightNeutralBorder, accent = Color(0xFFE14B34), onAccent = Color(0xFFFFFFFF), longBreakAccent = LightLongBreakAccent
    ),
    ColorTheme.OCEAN to PomodoroPalette(
        LightNeutralBackground, LightNeutralSurface, LightNeutralTextPrimary, LightNeutralTextSecondary,
        LightNeutralBorder, accent = Color(0xFF1E76E0), onAccent = Color(0xFFFFFFFF), longBreakAccent = LightLongBreakAccent
    ),
    ColorTheme.FOREST to PomodoroPalette(
        LightNeutralBackground, LightNeutralSurface, LightNeutralTextPrimary, LightNeutralTextSecondary,
        LightNeutralBorder, accent = Color(0xFF2E8F45), onAccent = Color(0xFFFFFFFF), longBreakAccent = LightLongBreakAccent
    ),
    ColorTheme.LAVENDER to PomodoroPalette(
        LightNeutralBackground, LightNeutralSurface, LightNeutralTextPrimary, LightNeutralTextSecondary,
        LightNeutralBorder, accent = Color(0xFF7C4FD6), onAccent = Color(0xFFFFFFFF), longBreakAccent = LightLongBreakAccent
    )
)

private val DarkPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(
        DarkNeutralBackground, DarkNeutralSurface, DarkNeutralTextPrimary, DarkNeutralTextSecondary,
        DarkNeutralBorder, accent = Color(0xFFFF6B57), onAccent = Color(0xFFFFFFFF), longBreakAccent = DarkLongBreakAccent
    ),
    ColorTheme.OCEAN to PomodoroPalette(
        DarkNeutralBackground, DarkNeutralSurface, DarkNeutralTextPrimary, DarkNeutralTextSecondary,
        DarkNeutralBorder, accent = Color(0xFF5AA9FF), onAccent = Color(0xFF00263D), longBreakAccent = DarkLongBreakAccent
    ),
    ColorTheme.FOREST to PomodoroPalette(
        DarkNeutralBackground, DarkNeutralSurface, DarkNeutralTextPrimary, DarkNeutralTextSecondary,
        DarkNeutralBorder, accent = Color(0xFF6FCB7A), onAccent = Color(0xFF0B3B12), longBreakAccent = DarkLongBreakAccent
    ),
    ColorTheme.LAVENDER to PomodoroPalette(
        DarkNeutralBackground, DarkNeutralSurface, DarkNeutralTextPrimary, DarkNeutralTextSecondary,
        DarkNeutralBorder, accent = Color(0xFFB08CF0), onAccent = Color(0xFF2C0F5C), longBreakAccent = DarkLongBreakAccent
    )
)

fun paletteFor(theme: ColorTheme, darkTheme: Boolean): PomodoroPalette =
    (if (darkTheme) DarkPalettes else LightPalettes).getValue(theme)
