package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `ongoing title reflects phase`() {
        assertEquals("집중 중", NotificationContent.ongoingTitle(TimerPhase.FOCUS))
        assertEquals("휴식 중", NotificationContent.ongoingTitle(TimerPhase.SHORT_BREAK))
        assertEquals("긴 휴식 중", NotificationContent.ongoingTitle(TimerPhase.LONG_BREAK))
    }

    @Test
    fun `ongoing text formats remaining time as mm colon ss`() {
        assertEquals("12:34 남음", NotificationContent.ongoingText(754_000L))
        assertEquals("00:05 남음", NotificationContent.ongoingText(5_000L))
        assertEquals("00:00 남음", NotificationContent.ongoingText(0L))
    }

    @Test
    fun `session end title reflects the phase just entered`() {
        assertEquals("휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.SHORT_BREAK))
        assertEquals("긴 휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.LONG_BREAK))
        assertEquals("다시 집중할 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.FOCUS))
    }

    @Test
    fun `daily goal reached title includes the target count`() {
        assertEquals("오늘의 목표 8개를 달성했어요!", NotificationContent.dailyGoalReachedTitle(8))
        assertEquals("오늘의 목표 1개를 달성했어요!", NotificationContent.dailyGoalReachedTitle(1))
    }

    @Test
    fun `toggle action label reflects whether the timer is running`() {
        assertEquals("일시정지", NotificationContent.toggleActionLabel(isRunning = true))
        assertEquals("재생", NotificationContent.toggleActionLabel(isRunning = false))
    }
}
