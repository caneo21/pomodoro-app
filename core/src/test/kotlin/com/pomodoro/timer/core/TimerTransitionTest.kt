package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTransitionTest {

    private fun state(phase: TimerPhase, focusCompletions: Long) = TimerState(
        phase = phase,
        isRunning = true,
        remainingMillis = 1_000L,
        totalMillis = 1_000L,
        totalFocusCompletions = focusCompletions
    )

    @Test
    fun `no boundary when phase and focus count are unchanged`() {
        val t = TimerTransition.between(state(TimerPhase.FOCUS, 3), state(TimerPhase.FOCUS, 3))

        assertFalse(t.crossedBoundary)
        assertEquals(0, t.focusSessionsCompleted)
    }

    @Test
    fun `break ending into focus is a boundary with no focus completion`() {
        val t = TimerTransition.between(state(TimerPhase.SHORT_BREAK, 3), state(TimerPhase.FOCUS, 3))

        assertTrue(t.crossedBoundary)
        assertEquals(0, t.focusSessionsCompleted)
    }

    @Test
    fun `landing back in the same phase still counts the focus sessions that ended`() {
        val t = TimerTransition.between(state(TimerPhase.FOCUS, 3), state(TimerPhase.FOCUS, 4))

        assertTrue(t.crossedBoundary)
        assertEquals(1, t.focusSessionsCompleted)
    }

    @Test
    fun `several focus sessions in one step are all counted`() {
        val t = TimerTransition.between(state(TimerPhase.FOCUS, 3), state(TimerPhase.SHORT_BREAK, 5))

        assertEquals(2, t.focusSessionsCompleted)
    }
}
