package com.pomodoro.timer.wear.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.pomodoro.timer.wear.theme.PomodoroWearTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearTimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tap_whenIdle_invokesOnStart() {
        var startClicked = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L),
                    isAmbient = false,
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = {}
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
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = true, remainingMillis = 1_000_000L, totalMillis = 1_500_000L),
                    isAmbient = false,
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onReset = {}
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
    fun holdingPastThreshold_invokesOnReset_notStart() {
        var startClicked = false
        var resetClicked = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = false),
                    isAmbient = false,
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = { resetClicked = true }
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
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = false),
                    isAmbient = false,
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = { resetClicked = true }
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
    fun remainingTime_isFormattedAsMinutesAndSeconds() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(remainingMillis = 754_000L),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithText("12:34").assertExists()
    }

    @Test
    fun cycleProgress_showsCompletedOverThreshold() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(completedPomodoros = 2, pomodorosUntilLongBreak = 4),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithTag("cycleProgressText").assertExists()
        composeRule.onNodeWithText("2/4").assertExists()
    }

    @Test
    fun gestureHint_isShownWhenIdle() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = false),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }
        composeRule.onNodeWithTag("gestureHint").assertExists()
    }

    @Test
    fun gestureHint_isHiddenWhenRunning() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = true),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }
        composeRule.onNodeWithTag("gestureHint").assertDoesNotExist()
    }
}
