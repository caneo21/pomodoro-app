package com.pomodoro.timer.core

/**
 * What happened between two [TimerState]s from the same engine. A phase-only comparison misses a
 * tick that crosses several boundaries and lands back in the phase it started in (e.g. a device that
 * slept through focus -> break -> focus with auto-start on), so this also looks at
 * [TimerState.totalFocusCompletions]. Every multi-boundary step passes through the end of a focus
 * phase, so the two checks together catch every crossing.
 */
data class TimerTransition(val crossedBoundary: Boolean, val focusSessionsCompleted: Int) {
    companion object {
        fun between(previous: TimerState, current: TimerState): TimerTransition {
            val focusEnded = (current.totalFocusCompletions - previous.totalFocusCompletions)
                .coerceAtLeast(0L).toInt()
            return TimerTransition(
                crossedBoundary = focusEnded > 0 || current.phase != previous.phase,
                focusSessionsCompleted = focusEnded
            )
        }
    }
}
