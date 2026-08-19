package com.pomodoro.timer.settings

import com.pomodoro.timer.core.TimerDefaults

data class TimerSettings(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val keepScreenOn: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
)
