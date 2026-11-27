package com.pomodoro.timer.core

/**
 * Wall-clock time (same clock as the engine's `now`) at which the in-flight phase ends, or null when
 * nothing is counting down. Phone and watch schedule a wake-up alarm here so a session end is noticed
 * even while the CPU is asleep and the 1-second ticker is suspended.
 */
fun phaseEndAlarmAtMillis(state: TimerState, now: Long): Long? =
    if (state.isRunning) now + state.remainingMillis else null
