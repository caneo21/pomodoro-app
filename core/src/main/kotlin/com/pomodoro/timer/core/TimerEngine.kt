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

    /**
     * Flips [phase] forward until [endTimestamp] is in the future. Durations are floored at
     * [MIN_PHASE_MILLIS] before they ever reach here, so this always terminates.
     */
    private fun advanceAcrossPhaseBoundaries(now: Long) {
        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

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

        advanceAcrossPhaseBoundaries(now)
        remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        return currentState(now)
    }

    fun updateDurations(focusMillis: Long, breakMillis: Long) {
        this.focusMillis = focusMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.breakMillis = breakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. A gap large enough to have crossed one or more phase boundaries is resolved here, so
     * the adopted state matches what a continuously-ticking engine would show.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = (payload.focusMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        breakMillis = (payload.breakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        phase = payload.phase
        currentPhaseDurationMillis = payload.totalMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        isRunning = payload.isRunning

        if (isRunning) {
            val elapsedSincePublish = (now - payload.lastActionAtMillis).coerceAtLeast(0)
            // Deliberately not clamped at 0: an overshoot is what tells the loop below how far past
            // the boundary we already are.
            endTimestamp = now + (payload.remainingMillis - elapsedSincePublish)
            advanceAcrossPhaseBoundaries(now)
            remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        } else {
            remainingWhenIdle = payload.remainingMillis
        }
        return currentState(now)
    }

    private companion object {
        /**
         * Floor for any externally-supplied phase duration. A 0 would make
         * [advanceAcrossPhaseBoundaries] add 0 forever.
         */
        val MIN_PHASE_MILLIS = minOf(
            TimerDefaults.MIN_FOCUS_MINUTES,
            TimerDefaults.MIN_BREAK_MINUTES
        ) * 60_000L
    }
}
