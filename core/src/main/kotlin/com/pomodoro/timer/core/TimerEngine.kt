package com.pomodoro.timer.core

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

    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. If that gap is large enough to have crossed a phase boundary, [remainingMillis] may
     * briefly read 0 until the next [tick] call, which flips phases correctly from there.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = payload.focusMinutes * 60_000L
        breakMillis = payload.breakMinutes * 60_000L
        phase = payload.phase
        currentPhaseDurationMillis = payload.totalMillis
        isRunning = payload.isRunning

        val elapsedSincePublish = if (isRunning) (now - payload.lastActionAtMillis).coerceAtLeast(0) else 0L
        val remainingNow = (payload.remainingMillis - elapsedSincePublish).coerceAtLeast(0)
        if (isRunning) {
            endTimestamp = now + remainingNow
        } else {
            remainingWhenIdle = remainingNow
        }
        return currentState(now)
    }
}
