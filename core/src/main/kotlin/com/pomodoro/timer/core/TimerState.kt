package com.pomodoro.timer.core

/**
 * [completedPomodoros] counts focus sessions since the last long break (it drives the long-break
 * cycle and is synced). [totalFocusCompletions] is a local, never-resetting count of focus phases
 * this engine has ended; compare it across two states with [TimerTransition.between] to learn how
 * many focus sessions ended in between, even when one tick crossed several phase boundaries.
 */
data class TimerState(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val completedPomodoros: Int = 0,
    val totalFocusCompletions: Long = 0L
)
