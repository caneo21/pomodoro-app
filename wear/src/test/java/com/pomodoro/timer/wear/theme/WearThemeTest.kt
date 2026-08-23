package com.pomodoro.timer.wear.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WearThemeTest {

    @Test
    fun `wearPaletteFor DEFAULT matches the original hardcoded colors`() {
        val palette = wearPaletteFor(ColorTheme.DEFAULT)

        assertEquals(Color(0xFFE85D4A), palette.primary)
        assertEquals(Color(0xFFB6432F), palette.primaryVariant)
        assertEquals(Color(0xFFFFFFFF), palette.onPrimary)
        assertEquals(Color(0xFF000000), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.onBackground)
        assertEquals(Color(0xFF5C6BC0), palette.longBreakAccent)
    }

    @Test
    fun `wearPaletteFor returns a distinct primary per theme`() {
        val primaries = ColorTheme.entries.map { wearPaletteFor(it).primary }

        assertEquals(ColorTheme.entries.size, primaries.distinct().size)
    }

    @Test
    fun `wearColorScheme carries the palette's core colors into the M3 scheme`() {
        val palette = wearPaletteFor(ColorTheme.OCEAN)

        val scheme = wearColorScheme(palette)

        assertEquals(palette.primary, scheme.primary)
        assertEquals(palette.onPrimary, scheme.onPrimary)
        assertEquals(palette.background, scheme.background)
        assertEquals(palette.onBackground, scheme.onBackground)
    }
}
