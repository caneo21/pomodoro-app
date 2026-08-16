package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState

data class WearTimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
)

fun buildWearUiState(timer: TimerState): WearTimerUiState = WearTimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis
)
