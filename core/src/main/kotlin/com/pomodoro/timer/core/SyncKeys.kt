package com.pomodoro.timer.core

/** Shared wire-format constants for [SyncTransport] implementations, so phone and watch can't drift. */
object SyncKeys {
    const val TIMER_STATE_PATH = "/pomodoro/timer_state"
    const val KEY_PHASE = "phase"
    const val KEY_IS_RUNNING = "isRunning"
    const val KEY_REMAINING_MILLIS = "remainingMillis"
    const val KEY_TOTAL_MILLIS = "totalMillis"
    const val KEY_FOCUS_MINUTES = "focusMinutes"
    const val KEY_BREAK_MINUTES = "breakMinutes"
    const val KEY_LAST_ACTION_AT_MILLIS = "lastActionAtMillis"
    const val KEY_LONG_BREAK_MINUTES = "longBreakMinutes"
    const val KEY_POMODOROS_UNTIL_LONG_BREAK = "pomodorosUntilLongBreak"
    const val KEY_COMPLETED_POMODOROS = "completedPomodoros"
}
