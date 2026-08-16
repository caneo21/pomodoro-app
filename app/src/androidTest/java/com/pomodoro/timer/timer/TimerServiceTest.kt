package com.pomodoro.timer.timer

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// TimerService.onBind() returns null by design (state is exposed via the companion
// StateFlow, not a binder), which means androidx.test.rule.ServiceTestRule can't be used
// here: it blocks on bindService()/onServiceConnected(), which never fires for a
// null-binding service. Start the service directly instead.
@RunWith(AndroidJUnit4::class)
class TimerServiceTest {

    @Test
    fun startAction_setsRunningStateToTrue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START)

        context.startService(intent)

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
        context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(1_200)

        context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
        Thread.sleep(200)

        val remainingAfterPause = TimerService.state.value.remainingMillis
        assertTrue(!TimerService.state.value.isRunning)

        Thread.sleep(500)
        assertEquals(remainingAfterPause, TimerService.state.value.remainingMillis)
    }

    @Test
    fun resetAction_returnsToIdleFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(500)

        context.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_RESET))
        Thread.sleep(200)

        val state = TimerService.state.value
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertTrue(!state.isRunning)
    }
}
