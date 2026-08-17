package com.pomodoro.timer.wear.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.wear.theme.PomodoroWearTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearTimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startButton_isShownWhenIdle_andInvokesCallback() {
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

        composeRule.onNodeWithTag("startButton").performClick()
        assertTrue(startClicked)
    }

    @Test
    fun pauseButton_isShownWhenRunning_andInvokesCallback() {
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

        composeRule.onNodeWithTag("pauseButton").performClick()
        assertTrue(pauseClicked)
    }

    @Test
    fun resetButton_invokesCallback() {
        var resetClicked = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = { resetClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("resetButton").performClick()
        assertTrue(resetClicked)
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
}
