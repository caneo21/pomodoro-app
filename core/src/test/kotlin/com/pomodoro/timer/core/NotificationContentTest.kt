package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `ongoing title reflects phase`() {
        assertEquals("집중 중", NotificationContent.ongoingTitle(TimerPhase.FOCUS))
        assertEquals("휴식 중", NotificationContent.ongoingTitle(TimerPhase.BREAK))
    }

    @Test
    fun `ongoing text formats remaining time as mm colon ss`() {
        assertEquals("12:34 남음", NotificationContent.ongoingText(754_000L))
        assertEquals("00:05 남음", NotificationContent.ongoingText(5_000L))
        assertEquals("00:00 남음", NotificationContent.ongoingText(0L))
    }

    @Test
    fun `session end title reflects the phase just entered`() {
        assertEquals("휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.BREAK))
        assertEquals("다시 집중할 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.FOCUS))
    }
}
