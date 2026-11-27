package com.pomodoro.timer.sync

import com.pomodoro.timer.core.SyncKeys
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataLayerChangesTest {

    private fun payload(lastActionAtMillis: Long) = SyncPayload(
        phase = TimerPhase.FOCUS,
        isRunning = false,
        remainingMillis = 1_488_000L,
        totalMillis = 1_500_000L,
        focusMinutes = 25,
        breakMinutes = 5,
        lastActionAtMillis = lastActionAtMillis
    )

    private fun change(at: Long, isChanged: Boolean = true, path: String? = SyncKeys.TIMER_STATE_PATH) =
        DataLayerChange(isChanged, path) { payload(at) }

    @Test
    fun `the newest timer write in a batch wins`() {
        val newest = newestTimerStateChange(listOf(change(1_000L), change(3_000L), change(2_000L)))

        assertEquals(3_000L, newest?.lastActionAtMillis)
    }

    @Test
    fun `deletions and other paths are skipped without being decoded`() {
        val poisoned = { path: String?, isChanged: Boolean ->
            DataLayerChange(isChanged, path) { error("must not be decoded") }
        }

        val newest = newestTimerStateChange(
            listOf(poisoned("/other", true), poisoned(SyncKeys.TIMER_STATE_PATH, false), change(1_000L))
        )

        assertEquals(1_000L, newest?.lastActionAtMillis)
    }

    @Test
    fun `a batch with no timer write yields nothing`() {
        assertNull(newestTimerStateChange(listOf(change(1_000L, path = "/other"))))
        assertNull(newestTimerStateChange(emptyList()))
    }
}
