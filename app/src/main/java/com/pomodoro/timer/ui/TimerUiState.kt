package com.pomodoro.timer.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val completedPomodoros: Int = 0,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
)

fun buildUiState(timer: TimerState, settings: TimerSettings): TimerUiState = TimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    focusMinutes = settings.focusMinutes,
    breakMinutes = settings.breakMinutes,
    longBreakMinutes = settings.longBreakMinutes,
    completedPomodoros = timer.completedPomodoros,
    pomodorosUntilLongBreak = settings.pomodorosUntilLongBreak
)
