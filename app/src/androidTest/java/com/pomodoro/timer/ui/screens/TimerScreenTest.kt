package com.pomodoro.timer.ui.screens

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startButton_isShownWhenIdle_andInvokesCallback() {
        var startClicked = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L),
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("startButton").performClick()
        assertTrue(startClicked)
    }

    @Test
    fun pauseButton_isShownWhenRunning_andInvokesCallback() {
        var pauseClicked = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(phase = TimerPhase.FOCUS, isRunning = true, remainingMillis = 1_000_000L, totalMillis = 1_500_000L),
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("pauseButton").performClick()
        assertTrue(pauseClicked)
    }

    @Test
    fun resetButton_invokesCallback() {
        var resetClicked = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(),
                    onStart = {},
                    onPause = {},
                    onReset = { resetClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("resetButton").performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun remainingTime_isFormattedAsMinutesAndSeconds() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(remainingMillis = 754_000L),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("12:34").assertExists()
    }

    @Test
    fun cycleProgress_showsCompletedOverThreshold() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(completedPomodoros = 2, pomodorosUntilLongBreak = 4),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("cycleProgressText").assertExists()
        composeRule.onNodeWithText("2/4").assertExists()
    }

    // Android aggregates a View's keepScreenOn flag up into the window's FLAG_KEEP_SCREEN_ON on
    // the next layout traversal - real, OS-owned behavior confirmed by hand on a physical device
    // (Settings > 화면 꺼짐 방지 on, then Start: dumpsys window's fl= gains bit 0x80). Compose's
    // isolated createAndroidComposeRule<ComponentActivity>() host doesn't reliably reproduce that
    // traversal, so these tests assert on the one thing TimerScreen's DisposableEffect actually
    // controls - LocalView.current.keepScreenOn - rather than the window flag itself.
    @Test
    fun keepScreenOn_isAppliedToView_onlyWhileRunning() {
        lateinit var view: View
        var isRunning by mutableStateOf(false)
        composeRule.setContent {
            view = LocalView.current
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(keepScreenOn = true, isRunning = isRunning),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertFalse("view should not keep the screen on before the timer starts running", view.keepScreenOn)

        isRunning = true
        composeRule.waitForIdle()
        assertTrue("view should keep the screen on once the timer starts running", view.keepScreenOn)
    }

    @Test
    fun keepScreenOn_isNotAppliedToView_whenSettingDisabled() {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(keepScreenOn = false, isRunning = true),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertFalse(view.keepScreenOn)
    }
}
