package com.pomodoro.timer.timer

object NotificationContent {

    fun ongoingTitle(phase: TimerPhase): String = when (phase) {
        TimerPhase.FOCUS -> "집중 중"
        TimerPhase.BREAK -> "휴식 중"
    }

    fun ongoingText(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d 남음".format(minutes, seconds)
    }

    fun sessionEndTitle(newPhase: TimerPhase): String = when (newPhase) {
        TimerPhase.BREAK -> "휴식 시간입니다!"
        TimerPhase.FOCUS -> "다시 집중할 시간입니다!"
    }
}
