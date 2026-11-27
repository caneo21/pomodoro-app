package com.pomodoro.timer.wear.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class WearStartCommandsTest {

    @Test
    fun `user commands must enter the foreground`() {
        for (action in listOf(WearTimerService.ACTION_START, WearTimerService.ACTION_PAUSE, WearTimerService.ACTION_RESET)) {
            assertEquals(action, ForegroundEntry.ALWAYS, foregroundEntryFor(action))
        }
    }

    @Test
    fun `a sync started by the background listener must enter the foreground`() {
        // The listener starts it with startForegroundService after adopting a running phone session.
        assertEquals(ForegroundEntry.ALWAYS, foregroundEntryFor(WearTimerService.ACTION_SYNC_REMOTE))
    }

    @Test
    fun `the phase-end alarm only enters the foreground if the service is not already there`() {
        assertEquals(ForegroundEntry.IF_NOT_ALREADY, foregroundEntryFor(WearTimerService.ACTION_PHASE_END_ALARM))
    }
}
