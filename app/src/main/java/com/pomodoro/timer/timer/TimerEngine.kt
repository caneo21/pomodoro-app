package com.pomodoro.timer.timer

class TimerEngine(
    private var focusMillis: Long,
    private var breakMillis: Long
) {
    private var phase: TimerPhase = TimerPhase.FOCUS
    private var isRunning: Boolean = false
    private var endTimestamp: Long = 0L
    private var remainingWhenIdle: Long = focusMillis

    // Duration the in-flight phase was entered with. Kept separate from focusMillis/breakMillis so a
    // mid-session duration change cannot make remainingMillis exceed totalMillis; it applies on the
    // next phase flip instead.
    private var currentPhaseDurationMillis: Long = focusMillis

    private fun durationFor(phase: TimerPhase): Long =
        if (phase == TimerPhase.FOCUS) focusMillis else breakMillis

    fun currentState(now: Long): TimerState {
        val remaining = if (isRunning) (endTimestamp - now).coerceAtLeast(0) else remainingWhenIdle
        return TimerState(
            phase = phase,
            isRunning = isRunning,
            remainingMillis = remaining,
            totalMillis = currentPhaseDurationMillis
        )
    }

    fun start(now: Long): TimerState {
        if (!isRunning) {
            isRunning = true
            endTimestamp = now + remainingWhenIdle
        }
        return currentState(now)
    }

    fun pause(now: Long): TimerState {
        if (isRunning) {
            remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
            isRunning = false
        }
        return currentState(now)
    }

    fun reset(now: Long): TimerState {
        isRunning = false
        phase = TimerPhase.FOCUS
        remainingWhenIdle = focusMillis
        currentPhaseDurationMillis = focusMillis
        return currentState(now)
    }

    fun tick(now: Long): TimerState {
        if (!isRunning) return currentState(now)

        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
        remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        return currentState(now)
    }

    fun updateDurations(focusMillis: Long, breakMillis: Long) {
        this.focusMillis = focusMillis
        this.breakMillis = breakMillis
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }
}
