package com.pomodoro.timer.ui.screens.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skipButton_onFirstPage_invokesOnFinishImmediately() {
        var finished = false
        composeRule.setContent {
            PomodoroTheme {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        composeRule.onNodeWithTag("onboardingSkipButton").performClick()

        assertTrue(finished)
    }

    @Test
    fun nextButton_advancesThroughAllPages_thenFinishesOnLastPage() {
        var finished = false
        composeRule.setContent {
            PomodoroTheme {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        composeRule.onNodeWithTag("onboardingPage_0").assertExists()

        repeat(onboardingPages.lastIndex) {
            composeRule.onNodeWithTag("onboardingNextButton").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("onboardingPage_${onboardingPages.lastIndex}").assertExists()
        composeRule.onNodeWithTag("onboardingSkipButton").assertDoesNotExist()

        composeRule.onNodeWithTag("onboardingNextButton").performClick()

        assertTrue(finished)
    }
}
