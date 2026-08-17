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
    fun `tick past end of focus phase transitions to break without drift`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)

        // tick 5 seconds after focus should have ended
        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(breakMillis - 5_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `tick past end of break phase transitions back to focus`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // flips to BREAK

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
        engine.updateDurations(focusMillis = 10 * 60_000L, breakMillis = 2 * 60_000L)

        val state = engine.currentState(now = 0L)

        assertEquals(10 * 60_000L, state.remainingMillis)
        assertEquals(10 * 60_000L, state.totalMillis)
    }

    @Test
    fun `updateDurations mid-session does not change the running phase total until it flips`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = 60_000L)

        engine.updateDurations(focusMillis = 10 * 60_000L, breakMillis = 2 * 60_000L)

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
    fun `adoptRemote crosses a phase boundary when the payload is older than its remaining time`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        // 60s after publish: focus ended 55s ago, so we are 55s into the 5-minute break.
        val state = engine.adoptRemote(payload, now = 1_060_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(breakMillis - 55_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `adoptRemote across multiple phase boundaries matches a tick-driven engine`() {
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )
        // Focus ends at +5s, break ends at +305s, the next focus ends at +1_805s. Land 10s into it.
        val now = 1_000_000L + 5_000L + breakMillis + 10_000L

        val adopted = TimerEngine(focusMillis, breakMillis).adoptRemote(payload, now)

        // Reference: an engine driven forward by plain ticks over the same wall-clock span. Starting
        // at 1_005_000 - focusMillis puts its focus phase's end exactly where the payload's is.
        val startedAt = 1_005_000L - focusMillis
        val reference = TimerEngine(focusMillis, breakMillis)
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
}
