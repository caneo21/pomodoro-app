package com.pomodoro.timer.core

data class DailyGoalProgress(val date: String, val count: Int)

object DailyGoalTracker {
    /** Returns [stored] as-is if it's for [today], otherwise a fresh zero count for [today]. */
    fun forToday(stored: DailyGoalProgress?, today: String): DailyGoalProgress =
        if (stored != null && stored.date == today) stored else DailyGoalProgress(today, 0)

    /** [current] with one more completion recorded, rolling over to [today] first if needed. */
    fun recordCompletion(current: DailyGoalProgress?, today: String): DailyGoalProgress {
        val base = forToday(current, today)
        return base.copy(count = base.count + 1)
    }

    /** True the instant [count] first reaches [target] (i.e. [previousCount] was still under it). */
    fun justReachedGoal(previousCount: Int, count: Int, target: Int): Boolean =
        previousCount < target && count >= target
}
