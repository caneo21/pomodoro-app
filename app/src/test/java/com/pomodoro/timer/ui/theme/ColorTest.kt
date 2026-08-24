package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

// Exact values from docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md, Section A.
class ColorTest {

    @Test
    fun `dark neutrals match the shared token spec`() {
        val palette = paletteFor(ColorTheme.DEFAULT, darkTheme = true)
        assertEquals(Color(0xFF0D0D0F), palette.background)
        assertEquals(Color(0xFF17171A), palette.surface)
        assertEquals(Color(0xFFF2F2F4), palette.textPrimary)
        assertEquals(Color(0xFF8A8A92), palette.textSecondary)
        assertEquals(Color(0xFF2A2A2E), palette.border)
        assertEquals(Color(0xFF6B7BFF), palette.longBreakAccent)
    }

    @Test
    fun `light neutrals match the shared token spec`() {
        val palette = paletteFor(ColorTheme.DEFAULT, darkTheme = false)
        assertEquals(Color(0xFFFAFAF9), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.surface)
        assertEquals(Color(0xFF17171A), palette.textPrimary)
        assertEquals(Color(0xFF6B6B72), palette.textSecondary)
        assertEquals(Color(0xFFE4E4E7), palette.border)
        assertEquals(Color(0xFF4A57C7), palette.longBreakAccent)
    }

    @Test
    fun `per-theme accent matches the shared token spec`() {
        assertEquals(Color(0xFFFF6B57), paletteFor(ColorTheme.DEFAULT, darkTheme = true).accent)
        assertEquals(Color(0xFFE14B34), paletteFor(ColorTheme.DEFAULT, darkTheme = false).accent)
        assertEquals(Color(0xFF5AA9FF), paletteFor(ColorTheme.OCEAN, darkTheme = true).accent)
        assertEquals(Color(0xFF1E76E0), paletteFor(ColorTheme.OCEAN, darkTheme = false).accent)
        assertEquals(Color(0xFF6FCB7A), paletteFor(ColorTheme.FOREST, darkTheme = true).accent)
        assertEquals(Color(0xFF2E8F45), paletteFor(ColorTheme.FOREST, darkTheme = false).accent)
        assertEquals(Color(0xFFB08CF0), paletteFor(ColorTheme.LAVENDER, darkTheme = true).accent)
        assertEquals(Color(0xFF7C4FD6), paletteFor(ColorTheme.LAVENDER, darkTheme = false).accent)
    }
}
