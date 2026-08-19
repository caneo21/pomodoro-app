package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalTrackerTest {

    @Test
    fun `forToday returns stored progress unchanged when dates match`() {
        val stored = DailyGoalProgress(date = "2026-08-19", count = 3)

        val result = DailyGoalTracker.forToday(stored, today = "2026-08-19")

        assertEquals(stored, result)
    }

    @Test
    fun `forToday zeroes out on a date change`() {
        val stored = DailyGoalProgress(date = "2026-08-18", count = 5)

        val result = DailyGoalTracker.forToday(stored, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 0), result)
    }

    @Test
    fun `forToday zeroes out when stored is null`() {
        val result = DailyGoalTracker.forToday(null, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 0), result)
    }

    @Test
    fun `recordCompletion increments within a day`() {
        val current = DailyGoalProgress(date = "2026-08-19", count = 3)

        val result = DailyGoalTracker.recordCompletion(current, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 4), result)
    }

    @Test
    fun `recordCompletion restarts at 1 across a date change`() {
        val current = DailyGoalProgress(date = "2026-08-18", count = 7)

        val result = DailyGoalTracker.recordCompletion(current, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 1), result)
    }

    @Test
    fun `recordCompletion starts at 1 when current is null`() {
        val result = DailyGoalTracker.recordCompletion(null, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 1), result)
    }

    @Test
    fun `justReachedGoal is true only on the exact crossing`() {
        assertTrue(DailyGoalTracker.justReachedGoal(previousCount = 7, count = 8, target = 8))
    }

    @Test
    fun `justReachedGoal is false below the target`() {
        assertFalse(DailyGoalTracker.justReachedGoal(previousCount = 6, count = 7, target = 8))
    }

    @Test
    fun `justReachedGoal is false once already past the target`() {
        assertFalse(DailyGoalTracker.justReachedGoal(previousCount = 8, count = 9, target = 8))
    }
}
