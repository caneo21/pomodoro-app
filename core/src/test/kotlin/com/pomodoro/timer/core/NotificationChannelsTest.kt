package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationChannelsTest {

    @Test
    fun `sessionEndChannelId picks a distinct channel for each sound and vibration combination`() {
        val notifVib = NotificationChannels.sessionEndChannelId(useAlarmSound = false, vibrationEnabled = true)
        val notifNoVib = NotificationChannels.sessionEndChannelId(useAlarmSound = false, vibrationEnabled = false)
        val alarmVib = NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = true)
        val alarmNoVib = NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = false)

        val ids = listOf(notifVib, notifNoVib, alarmVib, alarmNoVib)
        assertEquals(4, ids.toSet().size)
    }

    @Test
    fun `sessionEndChannelId is stable for the same inputs`() {
        assertEquals(
            NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = false),
            NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = false)
        )
    }

    @Test
    fun `allSessionEndChannelIds lists exactly the four combinations`() {
        val expected = setOf(
            NotificationChannels.sessionEndChannelId(useAlarmSound = false, vibrationEnabled = true),
            NotificationChannels.sessionEndChannelId(useAlarmSound = false, vibrationEnabled = false),
            NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = true),
            NotificationChannels.sessionEndChannelId(useAlarmSound = true, vibrationEnabled = false)
        )

        assertEquals(expected, NotificationChannels.allSessionEndChannelIds.toSet())
    }
}
