package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {

    @Test
    fun `paletteFor DEFAULT light matches the original hardcoded colors`() {
        val palette = paletteFor(ColorTheme.DEFAULT, darkTheme = false)

        assertEquals(Color(0xFFE85D4A), palette.primary)
        assertEquals(Color(0xFFFFFFFF), palette.onPrimary)
        assertEquals(Color(0xFFFFFBF7), palette.background)
        assertEquals(Color(0xFF5C6BC0), palette.longBreakAccent)
    }

    @Test
    fun `paletteFor returns a distinct palette per theme`() {
        val themes = ColorTheme.entries
        val lightPrimaries = themes.map { paletteFor(it, darkTheme = false).primary }
        val darkPrimaries = themes.map { paletteFor(it, darkTheme = true).primary }

        assertEquals(themes.size, lightPrimaries.distinct().size)
        assertEquals(themes.size, darkPrimaries.distinct().size)
    }

    @Test
    fun `paletteFor OCEAN differs between light and dark`() {
        val light = paletteFor(ColorTheme.OCEAN, darkTheme = false)
        val dark = paletteFor(ColorTheme.OCEAN, darkTheme = true)

        assertEquals(Color(0xFF1E88E5), light.primary)
        assertEquals(Color(0xFF64B5F6), dark.primary)
    }
}
