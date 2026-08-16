package com.pomodoro.timer.wear.timer

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearTimerServiceTest {

    @Test
    fun startAction_setsRunningStateToTrue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START)

        context.startService(intent)

        val deadline = System.currentTimeMillis() + 3_000
        while (!WearTimerService.state.value.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertTrue(WearTimerService.state.value.isRunning)
        assertEquals(TimerPhase.FOCUS, WearTimerService.state.value.phase)
    }

    @Test
    fun pauseAction_stopsRunningWithoutLosingRemainingTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START))
        Thread.sleep(1_200)

        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_PAUSE))
        Thread.sleep(200)

        val remainingAfterPause = WearTimerService.state.value.remainingMillis
        assertTrue(!WearTimerService.state.value.isRunning)

        Thread.sleep(500)
        assertEquals(remainingAfterPause, WearTimerService.state.value.remainingMillis)
    }

    @Test
    fun resetAction_returnsToIdleFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START))
        Thread.sleep(500)

        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_RESET))
        Thread.sleep(200)

        val state = WearTimerService.state.value
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertTrue(!state.isRunning)
    }
}
