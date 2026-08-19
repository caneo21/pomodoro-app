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

    @Test
    fun `buildUiState carries over the long-break cycle fields`() {
        val timer = TimerState(
            phase = TimerPhase.LONG_BREAK,
            isRunning = true,
            remainingMillis = 600_000L,
            totalMillis = 900_000L,
            completedPomodoros = 4
        )
        val settings = TimerSettings(
            focusMinutes = 30,
            breakMinutes = 10,
            longBreakMinutes = 20,
            pomodorosUntilLongBreak = 4
        )

        val uiState = buildUiState(timer, settings)

        assertEquals(TimerPhase.LONG_BREAK, uiState.phase)
        assertEquals(20, uiState.longBreakMinutes)
        assertEquals(4, uiState.completedPomodoros)
        assertEquals(4, uiState.pomodorosUntilLongBreak)
    }

    @Test
    fun `buildUiState carries autostart settings through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, autoStartBreak = true, autoStartFocus = false)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.autoStartBreak)
        assertEquals(false, uiState.autoStartFocus)
    }

    @Test
    fun `buildUiState carries keepScreenOn through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, keepScreenOn = true)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.keepScreenOn)
    }

    @Test
    fun `buildUiState carries dailyGoalEnabled and dailyGoalTarget through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, dailyGoalEnabled = true, dailyGoalTarget = 12)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.dailyGoalEnabled)
        assertEquals(12, uiState.dailyGoalTarget)
    }
}
