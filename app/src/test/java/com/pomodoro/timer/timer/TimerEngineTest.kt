package com.pomodoro.timer.timer

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

        assertEquals(TimerPhase.BREAK, state.phase)
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
        assertEquals(TimerPhase.BREAK, afterFlip.phase)
        assertEquals(2 * 60_000L, afterFlip.totalMillis)
    }
}
