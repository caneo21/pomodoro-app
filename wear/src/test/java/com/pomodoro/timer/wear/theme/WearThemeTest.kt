package com.pomodoro.timer.wear.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WearThemeTest {

    @Test
    fun `wearPaletteFor DEFAULT matches the monochrome token values`() {
        val palette = wearPaletteFor(ColorTheme.DEFAULT)

        assertEquals(Color(0xFFFF6B57), palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.onAccent)
        assertEquals(Color(0xFF0D0D0F), palette.background)
        assertEquals(Color(0xFFF2F2F4), palette.onBackground)
    }

    @Test
    fun `wearPaletteFor returns a distinct accent per theme`() {
        val accents = ColorTheme.entries.map { wearPaletteFor(it).accent }

        assertEquals(ColorTheme.entries.size, accents.distinct().size)
    }

    @Test
    fun `every theme shares the same monochrome background and a single shared long-break accent`() {
        val backgrounds = ColorTheme.entries.map { wearPaletteFor(it).background }.distinct()
        val onBackgrounds = ColorTheme.entries.map { wearPaletteFor(it).onBackground }.distinct()

        assertEquals(listOf(Color(0xFF0D0D0F)), backgrounds)
        assertEquals(listOf(Color(0xFFF2F2F4)), onBackgrounds)
        assertEquals(Color(0xFF6B7BFF), WearLongBreakAccent)
    }

    @Test
    fun `wearColorScheme carries the palette's accent and neutrals into the M3 scheme`() {
        val palette = wearPaletteFor(ColorTheme.OCEAN)

        val scheme = wearColorScheme(palette)

        assertEquals(palette.accent, scheme.primary)
        assertEquals(palette.onAccent, scheme.onPrimary)
        assertEquals(palette.background, scheme.background)
        assertEquals(palette.onBackground, scheme.onBackground)
    }
}
