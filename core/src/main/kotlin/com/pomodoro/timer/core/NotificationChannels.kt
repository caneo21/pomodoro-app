package com.pomodoro.timer.core

/**
 * Android notification channels are immutable once created (importance/sound/vibration can't
 * change on API 26+), so the session-end/daily-goal sound+vibration combinations are pre-created
 * as four distinct channels up front; picking a channel at notify-time is just a lookup.
 */
object NotificationChannels {

    private const val PREFIX = "pomodoro_session_end"

    val allSessionEndChannelIds: List<String> = listOf(false, true).flatMap { alarm ->
        listOf(true, false).map { vibration -> channelId(alarm, vibration) }
    }

    fun sessionEndChannelId(useAlarmSound: Boolean, vibrationEnabled: Boolean): String =
        channelId(useAlarmSound, vibrationEnabled)

    private fun channelId(useAlarmSound: Boolean, vibrationEnabled: Boolean): String {
        val sound = if (useAlarmSound) "alarm" else "notif"
        val vibration = if (vibrationEnabled) "vib" else "novib"
        return "${PREFIX}_${sound}_$vibration"
    }
}
