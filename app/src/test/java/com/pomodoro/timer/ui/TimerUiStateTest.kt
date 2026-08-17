package com.pomodoro.timer.ui

import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerUiStateTest {

    @Test
    fun `buildUiState combines timer state and settings`() {
        val timer = TimerState(
            phase = TimerPhase.SHORT_BREAK,
            isRunning = true,
            remainingMillis = 120_000L,
            totalMillis = 300_000L
        )
        val settings = TimerSettings(focusMinutes = 30, breakMinutes = 10)

        val uiState = buildUiState(timer, settings)

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
        assertEquals(30, uiState.focusMinutes)
        assertEquals(10, uiState.breakMinutes)
    }
}
