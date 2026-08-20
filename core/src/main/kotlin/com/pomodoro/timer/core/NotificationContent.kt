package com.pomodoro.timer.core

object NotificationContent {

    fun ongoingTitle(phase: TimerPhase): String = when (phase) {
        TimerPhase.FOCUS -> "집중 중"
        TimerPhase.SHORT_BREAK -> "휴식 중"
        TimerPhase.LONG_BREAK -> "긴 휴식 중"
    }

    fun ongoingText(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d 남음".format(minutes, seconds)
    }

    fun sessionEndTitle(newPhase: TimerPhase): String = when (newPhase) {
        TimerPhase.SHORT_BREAK -> "휴식 시간입니다!"
        TimerPhase.LONG_BREAK -> "긴 휴식 시간입니다!"
        TimerPhase.FOCUS -> "다시 집중할 시간입니다!"
    }

    fun dailyGoalReachedTitle(target: Int): String = "오늘의 목표 ${target}개를 달성했어요!"

    fun toggleActionLabel(isRunning: Boolean): String = if (isRunning) "일시정지" else "재생"
}
