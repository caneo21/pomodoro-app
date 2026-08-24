package com.pomodoro.timer.ui.screens

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
    fun tap_whenIdle_invokesOnStart() {
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

        composeRule.onNodeWithTag("timerTapArea").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(startClicked)
    }

    @Test
    fun tap_whenRunning_invokesOnPause() {
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

        composeRule.onNodeWithTag("timerTapArea").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(pauseClicked)
    }

    @Test
    fun tap_afterRecomposingToRunning_invokesOnPause_notOnStartAgain() {
        var startCount = 0
        var pauseCount = 0
        var isRunning by mutableStateOf(false)
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(isRunning = isRunning),
                    onStart = { startCount++; isRunning = true },
                    onPause = { pauseCount++ },
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("timerTapArea").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        assertTrue("first tap while idle should start the timer", startCount == 1)

        composeRule.onNodeWithTag("timerTapArea").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        assertTrue("second tap while running should pause, not start again", pauseCount == 1)
        assertTrue("second tap while running must not call onStart again", startCount == 1)
    }

    @Test
    fun holdingPastThreshold_invokesOnReset_notStart() {
        var startClicked = false
        var resetClicked = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(),
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = { resetClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("timerTapArea").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(RESET_HOLD_MILLIS + 200L)
        composeRule.onNodeWithTag("timerTapArea").performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(resetClicked)
        assertFalse(startClicked)
    }

    @Test
    fun releasingBeforeThreshold_invokesTap_notReset() {
        var startClicked = false
        var resetClicked = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(),
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = { resetClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("timerTapArea").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1200L)
        composeRule.onNodeWithTag("timerTapArea").performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(startClicked)
        assertFalse(resetClicked)
    }

    @Test
    fun settingsButton_invokesCallback() {
        var settingsClicked = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("settingsButton").performClick()
        assertTrue(settingsClicked)
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

        composeRule.onNodeWithTag("cycleProgressIndicator").assertExists()
        composeRule.onNode(hasContentDescription("2/4")).assertExists()
    }

    @Test
    fun gestureHint_isShownWhenIdle() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(isRunning = false),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("gestureHint").assertExists()
    }

    @Test
    fun gestureHint_isHiddenWhenRunning() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(isRunning = true),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("gestureHint").assertDoesNotExist()
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
