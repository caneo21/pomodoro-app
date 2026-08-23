package com.pomodoro.timer.wear.ui

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.wear.theme.WearLongBreakAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerScreenColorTest {

    private val primary = Color(0xFFFF6B57)
    private val longBreakAccent = WearLongBreakAccent

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
