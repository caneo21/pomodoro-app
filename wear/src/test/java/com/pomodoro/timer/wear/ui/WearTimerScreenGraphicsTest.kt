package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerScreenGraphicsTest {

    @Test
    fun `phaseIcon returns target for FOCUS`() {
        assertEquals("🎯", phaseIcon(TimerPhase.FOCUS))
    }

    @Test
    fun `phaseIcon returns coffee for SHORT_BREAK`() {
        assertEquals("☕", phaseIcon(TimerPhase.SHORT_BREAK))
    }

    @Test
    fun `phaseIcon returns moon for LONG_BREAK`() {
        assertEquals("🌙", phaseIcon(TimerPhase.LONG_BREAK))
    }

    @Test
    fun `dotFillStates fills leading dots up to completed count`() {
        assertEquals(listOf(true, true, false, false), dotFillStates(completed = 2, total = 4))
    }

    @Test
    fun `dotFillStates returns all empty when nothing completed`() {
        assertEquals(listOf(false, false, false), dotFillStates(completed = 0, total = 3))
    }

    @Test
    fun `dotFillStates returns all filled when completed meets total`() {
        assertEquals(listOf(true, true), dotFillStates(completed = 2, total = 2))
    }

    @Test
    fun `dotFillStates clamps dots filled when completed exceeds total`() {
        assertEquals(listOf(true, true), dotFillStates(completed = 5, total = 2))
    }

    @Test
    fun `dotFillStates returns empty list when total is zero`() {
        assertEquals(emptyList<Boolean>(), dotFillStates(completed = 0, total = 0))
    }
}
