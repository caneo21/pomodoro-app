package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseEndAlarmTimeTest {

    @Test
    fun `a running phase fires at now plus its remaining time`() {
        val state = TimerState(TimerPhase.FOCUS, isRunning = true, remainingMillis = 90_000L, totalMillis = 1_500_000L)

        assertEquals(1_090_000L, phaseEndAlarmAtMillis(state, now = 1_000_000L))
    }

    @Test
    fun `an idle or paused phase has no alarm`() {
        val state = TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 90_000L, totalMillis = 1_500_000L)

        assertNull(phaseEndAlarmAtMillis(state, now = 1_000_000L))
    }
}
