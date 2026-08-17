package com.pomodoro.timer.core

class TimerEngine(
    private var focusMillis: Long,
    private var shortBreakMillis: Long,
    private var longBreakMillis: Long = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L,
    private var pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
) {
    private var phase: TimerPhase = TimerPhase.FOCUS
    private var isRunning: Boolean = false
    private var endTimestamp: Long = 0L
    private var remainingWhenIdle: Long = focusMillis
    private var completedPomodoros: Int = 0

    // Duration the in-flight phase was entered with. Kept separate from focusMillis/shortBreakMillis/
    // longBreakMillis so a mid-session duration change cannot make remainingMillis exceed totalMillis;
    // it applies on the next phase flip instead.
    private var currentPhaseDurationMillis: Long = focusMillis

    private fun durationFor(phase: TimerPhase): Long = when (phase) {
        TimerPhase.FOCUS -> focusMillis
        TimerPhase.SHORT_BREAK -> shortBreakMillis
        TimerPhase.LONG_BREAK -> longBreakMillis
    }

    /**
     * The phase to enter when [phase] just ended. Leaving FOCUS advances the completed-pomodoro
     * count and routes to LONG_BREAK once [pomodorosUntilLongBreak] is reached; leaving LONG_BREAK
     * resets that count. [reset] deliberately does not go through here, so it never resets the count.
     */
    private fun nextPhase(): TimerPhase = when (phase) {
        TimerPhase.FOCUS -> {
            completedPomodoros += 1
            if (completedPomodoros >= pomodorosUntilLongBreak) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK
        }
        TimerPhase.SHORT_BREAK -> TimerPhase.FOCUS
        TimerPhase.LONG_BREAK -> {
            completedPomodoros = 0
            TimerPhase.FOCUS
        }
    }

    /**
     * Flips [phase] forward (via [nextPhase]) until [endTimestamp] is in the future. Durations are
     * floored at [MIN_PHASE_MILLIS] before they ever reach here, so this always terminates.
     */
    private fun advanceAcrossPhaseBoundaries(now: Long) {
        while (now >= endTimestamp) {
            phase = nextPhase()
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
            totalMillis = currentPhaseDurationMillis,
            completedPomodoros = completedPomodoros
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

    fun updateDurations(
        focusMillis: Long,
        shortBreakMillis: Long,
        longBreakMillis: Long = this.longBreakMillis,
        pomodorosUntilLongBreak: Int = this.pomodorosUntilLongBreak
    ) {
        this.focusMillis = focusMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.shortBreakMillis = shortBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.longBreakMillis = longBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.pomodorosUntilLongBreak = pomodorosUntilLongBreak.coerceAtLeast(1)
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. A gap large enough to have crossed one or more phase boundaries (including into or
     * out of a long break) is resolved here, so the adopted state matches what a continuously-ticking
     * engine would show. [SyncPayload.completedPomodoros] is trusted as-is; only boundaries crossed
     * after the payload was captured are evolved locally.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = (payload.focusMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        shortBreakMillis = (payload.breakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        longBreakMillis = (payload.longBreakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak.coerceAtLeast(1)
        phase = payload.phase
        completedPomodoros = payload.completedPomodoros
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
            TimerDefaults.MIN_BREAK_MINUTES,
            TimerDefaults.MIN_LONG_BREAK_MINUTES
        ) * 60_000L
    }
}
