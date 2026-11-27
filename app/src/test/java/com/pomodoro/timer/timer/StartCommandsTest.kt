package com.pomodoro.timer.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class StartCommandsTest {

    @Test
    fun `user commands must enter the foreground`() {
        for (action in listOf(TimerService.ACTION_START, TimerService.ACTION_PAUSE, TimerService.ACTION_RESET)) {
            assertEquals(action, ForegroundEntry.ALWAYS, foregroundEntryFor(action))
        }
    }

    @Test
    fun `a sync started by the background listener must enter the foreground`() {
        // The listener starts it with startForegroundService after adopting a running watch session.
        assertEquals(ForegroundEntry.ALWAYS, foregroundEntryFor(TimerService.ACTION_SYNC_REMOTE))
    }

    @Test
    fun `the phase-end alarm only enters the foreground if the service is not already there`() {
        assertEquals(ForegroundEntry.IF_NOT_ALREADY, foregroundEntryFor(TimerService.ACTION_PHASE_END_ALARM))
    }
}
