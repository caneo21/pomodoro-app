package com.pomodoro.timer.wear.ui

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerScreenColorTest {

    private val primary = Color(0xFFE85D4A)
    private val longBreakAccent = Color(0xFF5C6BC0)

    @Test
    fun `progressColorFor returns primary for FOCUS`() {
        assertEquals(primary, progressColorFor(TimerPhase.FOCUS, primary, longBreakAccent))
    }

    @Test
    fun `progressColorFor returns primary for SHORT_BREAK`() {
        assertEquals(primary, progressColorFor(TimerPhase.SHORT_BREAK, primary, longBreakAccent))
    }

    @Test
    fun `progressColorFor returns accent for LONG_BREAK`() {
        assertEquals(longBreakAccent, progressColorFor(TimerPhase.LONG_BREAK, primary, longBreakAccent))
    }
}
