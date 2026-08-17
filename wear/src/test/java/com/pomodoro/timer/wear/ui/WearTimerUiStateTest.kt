package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerUiStateTest {

    @Test
    fun `buildWearUiState mirrors the timer state`() {
        val timer = TimerState(
            phase = TimerPhase.SHORT_BREAK,
            isRunning = true,
            remainingMillis = 120_000L,
            totalMillis = 300_000L
        )

        val uiState = buildWearUiState(timer)

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
    }

    @Test
    fun `buildWearUiState carries over the long-break cycle fields`() {
        val timer = TimerState(
            phase = TimerPhase.LONG_BREAK,
            isRunning = true,
            remainingMillis = 600_000L,
            totalMillis = 900_000L,
            completedPomodoros = 4
        )

        val uiState = buildWearUiState(timer, pomodorosUntilLongBreak = 4)

        assertEquals(TimerPhase.LONG_BREAK, uiState.phase)
        assertEquals(4, uiState.completedPomodoros)
        assertEquals(4, uiState.pomodorosUntilLongBreak)
    }
}
