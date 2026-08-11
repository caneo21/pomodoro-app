package com.pomodoro.timer.timer

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun startAction_setsRunningStateToTrue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START)

        serviceRule.startService(intent)

        val deadline = System.currentTimeMillis() + 3_000
        while (!TimerService.state.value.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertTrue(TimerService.state.value.isRunning)
        assertEquals(TimerPhase.FOCUS, TimerService.state.value.phase)
    }

    @Test
    fun pauseAction_stopsRunningWithoutLosingRemainingTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(1_200)

        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
        Thread.sleep(200)

        val remainingAfterPause = TimerService.state.value.remainingMillis
        assertTrue(!TimerService.state.value.isRunning)

        Thread.sleep(500)
        assertEquals(remainingAfterPause, TimerService.state.value.remainingMillis)
    }

    @Test
    fun resetAction_returnsToIdleFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(500)

        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_RESET))
        Thread.sleep(200)

        val state = TimerService.state.value
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertTrue(!state.isRunning)
    }
}
