package com.pomodoro.timer.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onBack = { backClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("backButton").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun focusSlider_and_breakSlider_areDisplayed() {
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("focusSlider").assertExists()
        composeRule.onNodeWithTag("breakSlider").assertExists()
    }

    @Test
    fun longBreakSlider_and_pomodorosUntilLongBreakSlider_areDisplayed() {
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    longBreakMinutes = 15,
                    pomodorosUntilLongBreak = 4,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onLongBreakMinutesChange = {},
                    onPomodorosUntilLongBreakChange = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("longBreakSlider").assertExists()
        composeRule.onNodeWithTag("pomodorosUntilLongBreakSlider").assertExists()
    }

    @Test
    fun autoStartSwitches_areDisplayed_andInvokeCallbacks() {
        var breakToggled: Boolean? = null
        var focusToggled: Boolean? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    autoStartBreak = false,
                    autoStartFocus = false,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onAutoStartBreakChange = { breakToggled = it },
                    onAutoStartFocusChange = { focusToggled = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("autoStartBreakLabel").assertExists()
        composeRule.onNodeWithTag("autoStartFocusLabel").assertExists()

        composeRule.onNodeWithTag("autoStartBreakSwitch").performClick()
        composeRule.onNodeWithTag("autoStartFocusSwitch").performClick()

        assertTrue(breakToggled == true)
        assertTrue(focusToggled == true)
    }
}
