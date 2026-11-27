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

    /**
     * User-visible channel name. The four channels show up side by side in the system's app
     * notification settings, so each name spells out its combination. Unlike sound/vibration, a
     * channel's name can be updated by re-creating it with the same id, so existing installs pick
     * this up on the next service start.
     */
    fun sessionEndChannelName(useAlarmSound: Boolean, vibrationEnabled: Boolean): String {
        val sound = if (useAlarmSound) "알람음" else "알림음"
        val vibration = if (vibrationEnabled) "진동" else "진동 없음"
        return "세션 종료 · $sound · $vibration"
    }

    private fun channelId(useAlarmSound: Boolean, vibrationEnabled: Boolean): String {
        val sound = if (useAlarmSound) "alarm" else "notif"
        val vibration = if (vibrationEnabled) "vib" else "novib"
        return "${PREFIX}_${sound}_$vibration"
    }
}
