package com.pomodoro.timer.core

/**
 * A full snapshot of one device's timer, exchanged between phone and watch. [lastActionAtMillis] is
 * the wall-clock time (System.currentTimeMillis()) at which this snapshot was captured — the device
 * that most recently produced a [SyncPayload] wins on conflict, per [isRemoteNewer]. [breakMinutes] is
 * the short-break duration; [longBreakMinutes] and [pomodorosUntilLongBreak] configure the long-break
 * cycle, and [completedPomodoros] is how many focus sessions have completed since the last long break.
 */
data class SyncPayload(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val lastActionAtMillis: Long,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val completedPomodoros: Int = 0,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET,
    val dailyGoalCount: Int = 0,
    val dailyGoalDate: String = "",
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val useAlarmSound: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT
)
