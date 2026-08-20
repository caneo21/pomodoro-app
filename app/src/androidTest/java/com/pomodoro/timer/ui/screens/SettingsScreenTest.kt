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

    @Test
    fun keepScreenOnSwitch_isDisplayed_andInvokesCallback() {
        var keepScreenOnToggled: Boolean? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    keepScreenOn = false,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onKeepScreenOnChange = { keepScreenOnToggled = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("keepScreenOnLabel").assertExists()
        composeRule.onNodeWithTag("keepScreenOnSwitch").performClick()

        assertTrue(keepScreenOnToggled == true)
    }

    @Test
    fun dailyGoalControls_areDisplayed_andInvokeCallbacks() {
        var enabledToggled: Boolean? = null
        var targetChanged: Int? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    dailyGoalEnabled = false,
                    dailyGoalTarget = 8,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onDailyGoalEnabledChange = { enabledToggled = it },
                    onDailyGoalTargetChange = { targetChanged = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("dailyGoalEnabledLabel").assertExists()
        composeRule.onNodeWithTag("dailyGoalTargetSlider").assertExists()

        composeRule.onNodeWithTag("dailyGoalEnabledSwitch").performClick()

        assertTrue(enabledToggled == true)
    }

    @Test
    fun notificationControls_areDisplayed_andInvokeCallbacks() {
        var notificationsToggled: Boolean? = null
        var vibrationToggled: Boolean? = null
        var useAlarmSoundToggled: Boolean? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    notificationsEnabled = true,
                    vibrationEnabled = true,
                    useAlarmSound = false,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onNotificationsEnabledChange = { notificationsToggled = it },
                    onVibrationEnabledChange = { vibrationToggled = it },
                    onUseAlarmSoundChange = { useAlarmSoundToggled = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("notificationsEnabledLabel").assertExists()
        composeRule.onNodeWithTag("vibrationEnabledLabel").assertExists()
        composeRule.onNodeWithTag("useAlarmSoundLabel").assertExists()

        composeRule.onNodeWithTag("notificationsEnabledSwitch").performClick()
        composeRule.onNodeWithTag("vibrationEnabledSwitch").performClick()
        composeRule.onNodeWithTag("useAlarmSoundSwitch").performClick()

        assertTrue(notificationsToggled == false)
        assertTrue(vibrationToggled == false)
        assertTrue(useAlarmSoundToggled == true)
    }
}
