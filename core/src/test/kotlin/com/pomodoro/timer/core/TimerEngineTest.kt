package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val focusMillis = 25 * 60_000L
    private val breakMillis = 5 * 60_000L

    @Test
    fun `initial state is idle focus with full duration`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val state = engine.currentState(now = 0L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
        assertEquals(focusMillis, state.totalMillis)
    }

    @Test
    fun `start begins counting down from now`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)

        val state = engine.tick(now = 10_000L)

        assertTrue(state.isRunning)
        assertEquals(focusMillis - 10_000L, state.remainingMillis)
    }

    @Test
    fun `pause freezes remaining time and resume continues from same point`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        val paused = engine.pause(now = 10_000L)
        assertFalse(paused.isRunning)
        assertEquals(focusMillis - 10_000L, paused.remainingMillis)

        // time passes while paused; remaining must not change
        val stillPaused = engine.currentState(now = 60_000L)
        assertEquals(focusMillis - 10_000L, stillPaused.remainingMillis)

        val resumed = engine.start(now = 60_000L)
        assertTrue(resumed.isRunning)
        assertEquals(focusMillis - 10_000L, resumed.remainingMillis)

        val laterTick = engine.tick(now = 65_000L)
        assertEquals(focusMillis - 15_000L, laterTick.remainingMillis)
    }

    @Test
    fun `reset returns to idle focus with full duration`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = 10_000L)

        val state = engine.reset(now = 10_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
    }

    @Test
    fun `tick past end of focus phase transitions to break without drift when autostart is enabled`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true)
        engine.start(now = 0L)

        // tick 5 seconds after focus should have ended
        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(breakMillis - 5_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `tick past end of break phase transitions back to focus when autostart is enabled`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // flips to SHORT_BREAK

        val state = engine.tick(now = focusMillis + breakMillis + 2_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertEquals(focusMillis - 2_000L, state.remainingMillis)
    }

    @Test
    fun `tick while paused does not change state`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val before = engine.currentState(now = 0L)

        val after = engine.tick(now = 999_000L)

        assertEquals(before, after)
    }

    @Test
    fun `updateDurations changes the duration used by the current idle phase`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.updateDurations(focusMillis = 10 * 60_000L, shortBreakMillis = 2 * 60_000L)

        val state = engine.currentState(now = 0L)

        assertEquals(10 * 60_000L, state.remainingMillis)
        assertEquals(10 * 60_000L, state.totalMillis)
    }

    @Test
    fun `updateDurations mid-session does not change the running phase total until it flips`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = 60_000L)

        engine.updateDurations(focusMillis = 10 * 60_000L, shortBreakMillis = 2 * 60_000L)

        val midSession = engine.currentState(now = 60_000L)
        assertEquals(focusMillis, midSession.totalMillis)
        assertEquals(focusMillis - 60_000L, midSession.remainingMillis)
        assertTrue(midSession.remainingMillis <= midSession.totalMillis)

        val afterFlip = engine.tick(now = focusMillis)
        assertEquals(TimerPhase.SHORT_BREAK, afterFlip.phase)
        assertEquals(2 * 60_000L, afterFlip.totalMillis)
    }

    @Test
    fun `adoptRemote applied immediately reproduces the remote running state`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.SHORT_BREAK,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = breakMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `adoptRemote accounts for elapsed time between publish and apply`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_010_000L)

        assertEquals(80_000L, state.remainingMillis)
        val laterTick = engine.tick(now = 1_015_000L)
        assertEquals(75_000L, laterTick.remainingMillis)
    }

    @Test
    fun `adoptRemote crosses a phase boundary when the payload is older than its remaining time and autostart is enabled`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            autoStartBreak = true
        )

        // 60s after publish: focus ended 55s ago, so we are 55s into the 5-minute break.
        val state = engine.adoptRemote(payload, now = 1_060_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(breakMillis - 55_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `adoptRemote across multiple phase boundaries matches a tick-driven engine when autostart is enabled`() {
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            autoStartBreak = true,
            autoStartFocus = true
        )
        // Focus ends at +5s, break ends at +305s, the next focus ends at +1_805s. Land 10s into it.
        val now = 1_000_000L + 5_000L + breakMillis + 10_000L

        val adopted = TimerEngine(focusMillis, breakMillis).adoptRemote(payload, now)

        // Reference: an engine driven forward by plain ticks over the same wall-clock span. Starting
        // at 1_005_000 - focusMillis puts its focus phase's end exactly where the payload's is.
        val startedAt = 1_005_000L - focusMillis
        val reference = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true)
        reference.start(startedAt)
        var t = startedAt
        while (t < now) {
            t = minOf(t + 60_000L, now)
            reference.tick(t)
        }
        val expected = reference.currentState(now)

        assertEquals(TimerPhase.FOCUS, adopted.phase)
        assertEquals(expected.phase, adopted.phase)
        assertEquals(expected.remainingMillis, adopted.remainingMillis)
        assertEquals(expected.totalMillis, adopted.totalMillis)
    }

    @Test
    fun `adoptRemote coerces a zero duration up to the minimum so tick cannot spin forever`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 0L,
            totalMillis = 0L,
            focusMinutes = 0,
            breakMinutes = 0,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        // A 0-minute duration would make tick()'s boundary loop add 0 forever.
        assertEquals(TimerDefaults.MIN_BREAK_MINUTES * 60_000L, state.totalMillis)
        val ticked = engine.tick(now = 1_000_000L + 24 * 60 * 60_000L)
        assertTrue(ticked.totalMillis >= TimerDefaults.MIN_BREAK_MINUTES * 60_000L)
    }

    @Test
    fun `adoptRemote of a paused payload freezes at the adjusted remaining time`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = false,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_005_000L)

        assertFalse(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        val later = engine.currentState(now = 2_000_000L)
        assertEquals(90_000L, later.remainingMillis)
    }

    @Test
    fun `completing the Nth focus session enters LONG_BREAK and resets the count when it ends`() {
        val engine = TimerEngine(
            focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 2,
            autoStartBreak = true, autoStartFocus = true
        )
        engine.start(now = 0L)

        engine.tick(now = focusMillis) // 1st FOCUS done -> SHORT_BREAK, completedPomodoros = 1
        engine.tick(now = focusMillis + breakMillis) // SHORT_BREAK done -> FOCUS, completedPomodoros stays 1

        val afterSecondFocus = engine.tick(now = focusMillis + breakMillis + focusMillis)
        assertEquals(TimerPhase.LONG_BREAK, afterSecondFocus.phase)
        assertEquals(2, afterSecondFocus.completedPomodoros)
        assertEquals(900_000L, afterSecondFocus.totalMillis)

        val afterLongBreak = engine.tick(now = focusMillis + breakMillis + focusMillis + 900_000L)
        assertEquals(TimerPhase.FOCUS, afterLongBreak.phase)
        assertEquals(0, afterLongBreak.completedPomodoros)
    }

    @Test
    fun `the default threshold of 4 enters LONG_BREAK on the 4th focus completion, not the 3rd`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true) // relies on the default pomodorosUntilLongBreak = 4
        engine.start(now = 0L)

        var now = 0L
        repeat(3) { cycle ->
            now += focusMillis
            val afterFocus = engine.tick(now = now)
            assertEquals(TimerPhase.SHORT_BREAK, afterFocus.phase)
            assertEquals(cycle + 1, afterFocus.completedPomodoros)

            now += breakMillis
            val afterBreak = engine.tick(now = now)
            assertEquals(TimerPhase.FOCUS, afterBreak.phase)
        }

        now += focusMillis
        val afterFourthFocus = engine.tick(now = now)
        assertEquals(TimerPhase.LONG_BREAK, afterFourthFocus.phase)
        assertEquals(4, afterFourthFocus.completedPomodoros)
        assertEquals(TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L, afterFourthFocus.totalMillis)
    }

    @Test
    fun `reset preserves the completed-pomodoros count`() {
        val engine = TimerEngine(focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 4)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // completedPomodoros = 1, now in SHORT_BREAK

        val state = engine.reset(now = focusMillis)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `updateDurations floors pomodorosUntilLongBreak at 1`() {
        val engine = TimerEngine(focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 4)
        engine.updateDurations(focusMillis, breakMillis, 900_000L, pomodorosUntilLongBreak = 0)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis)

        assertEquals(TimerPhase.LONG_BREAK, state.phase)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `adoptRemote applied immediately carries over completedPomodoros from the payload`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.SHORT_BREAK,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = breakMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            completedPomodoros = 3
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        assertEquals(3, state.completedPomodoros)
    }

    @Test
    fun `adoptRemote crossing into and out of a long break matches a tick-driven engine`() {
        val threshold = 1
        val longBreakMillis = 900_000L
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            longBreakMinutes = 15,
            pomodorosUntilLongBreak = threshold,
            completedPomodoros = 0,
            autoStartBreak = true,
            autoStartFocus = true
        )
        // Focus ends at +5s (completedPomodoros -> 1, meets threshold -> LONG_BREAK), long break runs
        // 15 minutes, then FOCUS resumes (completedPomodoros resets to 0). Land 10s into that FOCUS.
        val now = 1_000_000L + 5_000L + longBreakMillis + 10_000L

        val adopted = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold).adoptRemote(payload, now)

        // Reference: an engine driven forward by plain ticks over the same wall-clock span. Starting
        // at 1_005_000 - focusMillis puts its focus phase's end exactly where the payload's is.
        val startedAt = 1_005_000L - focusMillis
        val reference = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold, autoStartBreak = true, autoStartFocus = true)
        reference.start(startedAt)
        var t = startedAt
        while (t < now) {
            t = minOf(t + 60_000L, now)
            reference.tick(t)
        }
        val expected = reference.currentState(now)

        assertEquals(TimerPhase.FOCUS, adopted.phase)
        assertEquals(expected.phase, adopted.phase)
        assertEquals(expected.remainingMillis, adopted.remainingMillis)
        assertEquals(expected.totalMillis, adopted.totalMillis)
        assertEquals(0, adopted.completedPomodoros)
        assertEquals(expected.completedPomodoros, adopted.completedPomodoros)
    }

    @Test
    fun `adoptRemote coerces a zero long-break duration up to the shared minimum`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 0L,
            totalMillis = 0L,
            focusMinutes = 0,
            breakMinutes = 0,
            lastActionAtMillis = 1_000_000L,
            longBreakMinutes = 0,
            pomodorosUntilLongBreak = 1,
            completedPomodoros = 0
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        // All three durations share one floor (see TimerEngine.MIN_PHASE_MILLIS) so the boundary loop
        // can never add 0 forever, regardless of which duration was zeroed out.
        assertEquals(TimerPhase.LONG_BREAK, state.phase)
        assertEquals(TimerDefaults.MIN_BREAK_MINUTES * 60_000L, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `tick past end of focus phase pauses at SHORT_BREAK when autoStartBreak is false`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = false)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertFalse(state.isRunning)
        assertEquals(breakMillis, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `tick past end of break phase pauses at FOCUS when autoStartFocus is false`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = false)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // FOCUS -> SHORT_BREAK, keeps running (autoStartBreak = true)

        val state = engine.tick(now = focusMillis + breakMillis + 2_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
        assertEquals(focusMillis, state.totalMillis)
    }

    @Test
    fun `autoStartFocus, not autoStartBreak, governs leaving LONG_BREAK`() {
        val engine = TimerEngine(
            focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 1,
            autoStartBreak = true, autoStartFocus = false
        )
        engine.start(now = 0L)
        val enteredLongBreak = engine.tick(now = focusMillis) // FOCUS -> LONG_BREAK, keeps running
        assertTrue(enteredLongBreak.isRunning)

        val state = engine.tick(now = focusMillis + 900_000L + 1_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
    }

    @Test
    fun `adoptRemote stops exactly at a boundary governed off, without fast-forwarding further`() {
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            autoStartBreak = false,
            autoStartFocus = true
        )
        // Enough elapsed time to have crossed FOCUS -> SHORT_BREAK -> FOCUS if fully auto-continuing;
        // autoStartBreak = false must stop it the instant it enters SHORT_BREAK.
        val now = 1_000_000L + 5_000L + breakMillis + 10_000L

        val state = TimerEngine(focusMillis, breakMillis).adoptRemote(payload, now)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertFalse(state.isRunning)
        assertEquals(breakMillis, state.remainingMillis)
    }

    @Test
    fun `updateDurations changes which future transitions auto-continue`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = false)
        engine.updateDurations(focusMillis, breakMillis, autoStartBreak = true)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
    }

    @Test
    fun `updateDurations mid-session does not retroactively auto-continue the in-flight phase`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = false)
        engine.start(now = 0L)
        engine.tick(now = 60_000L) // mid-FOCUS, no boundary crossed yet

        engine.updateDurations(focusMillis, breakMillis, autoStartBreak = true)

        val stillMidFocus = engine.currentState(now = 120_000L)
        assertEquals(TimerPhase.FOCUS, stillMidFocus.phase)
        assertTrue(stillMidFocus.isRunning)

        val afterBoundary = engine.tick(now = focusMillis + 5_000L)
        assertEquals(TimerPhase.SHORT_BREAK, afterBoundary.phase)
        assertTrue(afterBoundary.isRunning) // now auto-continues, since autoStartBreak was flipped to true
    }
}
