package com.pomodoro.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroAppTest {

    @Test
    fun `onboardingFinishAction completes and shows timer on first run`() {
        assertEquals(OnboardingFinishAction.CompleteAndShowTimer, onboardingFinishAction(isFirstRun = true))
    }

    @Test
    fun `onboardingFinishAction returns to settings on replay`() {
        assertEquals(OnboardingFinishAction.ReturnToSettings, onboardingFinishAction(isFirstRun = false))
    }
}
