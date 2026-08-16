package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerUiStateTest {

    @Test
    fun `buildWearUiState mirrors the timer state`() {
        val timer = TimerState(
            phase = TimerPhase.BREAK,
            isRunning = true,
            remainingMillis = 120_000L,
            totalMillis = 300_000L
        )

        val uiState = buildWearUiState(timer)

        assertEquals(TimerPhase.BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
    }
}
