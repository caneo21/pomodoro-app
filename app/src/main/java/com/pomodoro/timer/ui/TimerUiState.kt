package com.pomodoro.timer.ui

import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.timer.TimerDefaults
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerState

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
)

fun buildUiState(timer: TimerState, settings: TimerSettings): TimerUiState = TimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    focusMinutes = settings.focusMinutes,
    breakMinutes = settings.breakMinutes
)
