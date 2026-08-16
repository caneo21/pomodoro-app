package com.pomodoro.timer.core

/**
 * A full snapshot of one device's timer, exchanged between phone and watch. [lastActionAtMillis] is
 * the wall-clock time (System.currentTimeMillis()) at which this snapshot was captured — the device
 * that most recently produced a [SyncPayload] wins on conflict, per [isRemoteNewer].
 */
data class SyncPayload(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val lastActionAtMillis: Long
)
