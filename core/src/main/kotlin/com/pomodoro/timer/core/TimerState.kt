package com.pomodoro.timer.core

data class TimerState(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val completedPomodoros: Int = 0
)
