package com.pomodoro.timer.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.*
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
}
