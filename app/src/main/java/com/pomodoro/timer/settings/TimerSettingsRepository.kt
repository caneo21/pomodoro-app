package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
import com.pomodoro.timer.core.TimerDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DailyGoalCompletionResult(val previous: DailyGoalProgress, val updated: DailyGoalProgress)

class TimerSettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES_KEY = intPreferencesKey("break_minutes")
        val LONG_BREAK_MINUTES_KEY = intPreferencesKey("long_break_minutes")
        val POMODOROS_UNTIL_LONG_BREAK_KEY = intPreferencesKey("pomodoros_until_long_break")
        val AUTO_START_BREAK_KEY = booleanPreferencesKey("auto_start_break")
        val AUTO_START_FOCUS_KEY = booleanPreferencesKey("auto_start_focus")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val DAILY_GOAL_ENABLED_KEY = booleanPreferencesKey("daily_goal_enabled")
        val DAILY_GOAL_TARGET_KEY = intPreferencesKey("daily_goal_target")
        val DAILY_GOAL_DATE_KEY = stringPreferencesKey("daily_goal_date")
        val DAILY_GOAL_COUNT_KEY = intPreferencesKey("daily_goal_count")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val USE_ALARM_SOUND_KEY = booleanPreferencesKey("use_alarm_sound")
        val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        const val DEFAULT_FOCUS_MINUTES = TimerDefaults.DEFAULT_FOCUS_MINUTES
        const val DEFAULT_BREAK_MINUTES = TimerDefaults.DEFAULT_BREAK_MINUTES
        const val MIN_FOCUS_MINUTES = TimerDefaults.MIN_FOCUS_MINUTES
        const val MAX_FOCUS_MINUTES = TimerDefaults.MAX_FOCUS_MINUTES
        const val MIN_BREAK_MINUTES = TimerDefaults.MIN_BREAK_MINUTES
        const val MAX_BREAK_MINUTES = TimerDefaults.MAX_BREAK_MINUTES
        const val DEFAULT_LONG_BREAK_MINUTES = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES
        const val MIN_LONG_BREAK_MINUTES = TimerDefaults.MIN_LONG_BREAK_MINUTES
        const val MAX_LONG_BREAK_MINUTES = TimerDefaults.MAX_LONG_BREAK_MINUTES
        const val DEFAULT_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        const val MIN_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK
        const val MAX_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK
        const val DEFAULT_DAILY_GOAL_TARGET = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
        const val MIN_DAILY_GOAL_TARGET = TimerDefaults.MIN_DAILY_GOAL_TARGET
        const val MAX_DAILY_GOAL_TARGET = TimerDefaults.MAX_DAILY_GOAL_TARGET
    }

    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES,
            longBreakMinutes = prefs[LONG_BREAK_MINUTES_KEY] ?: DEFAULT_LONG_BREAK_MINUTES,
            pomodorosUntilLongBreak = prefs[POMODOROS_UNTIL_LONG_BREAK_KEY] ?: DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
            autoStartBreak = prefs[AUTO_START_BREAK_KEY] ?: false,
            autoStartFocus = prefs[AUTO_START_FOCUS_KEY] ?: false,
            keepScreenOn = prefs[KEEP_SCREEN_ON_KEY] ?: false,
            dailyGoalEnabled = prefs[DAILY_GOAL_ENABLED_KEY] ?: false,
            dailyGoalTarget = prefs[DAILY_GOAL_TARGET_KEY] ?: DEFAULT_DAILY_GOAL_TARGET,
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED_KEY] ?: true,
            vibrationEnabled = prefs[VIBRATION_ENABLED_KEY] ?: true,
            useAlarmSound = prefs[USE_ALARM_SOUND_KEY] ?: false,
            colorTheme = ColorTheme.entries.firstOrNull { it.name == prefs[COLOR_THEME_KEY] } ?: ColorTheme.DEFAULT,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED_KEY] ?: false
        )
    }

    suspend fun updateFocusMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_MINUTES_KEY] = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES) }
    }

    suspend fun updateBreakMinutes(minutes: Int) {
        dataStore.edit { it[BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) }
    }

    suspend fun updateLongBreakMinutes(minutes: Int) {
        dataStore.edit { it[LONG_BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_LONG_BREAK_MINUTES, MAX_LONG_BREAK_MINUTES) }
    }

    suspend fun updatePomodorosUntilLongBreak(count: Int) {
        dataStore.edit {
            it[POMODOROS_UNTIL_LONG_BREAK_KEY] = count.coerceIn(MIN_POMODOROS_UNTIL_LONG_BREAK, MAX_POMODOROS_UNTIL_LONG_BREAK)
        }
    }

    suspend fun updateAutoStartBreak(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_BREAK_KEY] = enabled }
    }

    suspend fun updateAutoStartFocus(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_FOCUS_KEY] = enabled }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON_KEY] = enabled }
    }

    suspend fun updateDailyGoalEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_GOAL_ENABLED_KEY] = enabled }
    }

    suspend fun updateDailyGoalTarget(target: Int) {
        dataStore.edit { it[DAILY_GOAL_TARGET_KEY] = target.coerceIn(MIN_DAILY_GOAL_TARGET, MAX_DAILY_GOAL_TARGET) }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun updateVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATION_ENABLED_KEY] = enabled }
    }

    suspend fun updateUseAlarmSound(enabled: Boolean) {
        dataStore.edit { it[USE_ALARM_SOUND_KEY] = enabled }
    }

    suspend fun updateColorTheme(theme: ColorTheme) {
        dataStore.edit { it[COLOR_THEME_KEY] = theme.name }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED_KEY] = completed }
    }

    val dailyGoalProgress: Flow<DailyGoalProgress> = dataStore.data.map { prefs ->
        DailyGoalProgress(
            date = prefs[DAILY_GOAL_DATE_KEY] ?: "",
            count = prefs[DAILY_GOAL_COUNT_KEY] ?: 0
        )
    }

    /** Records [completions] finished focus sessions for [today] in a single DataStore write. */
    suspend fun recordDailyGoalCompletion(today: String, completions: Int = 1): DailyGoalCompletionResult {
        var previous = DailyGoalProgress(today, 0)
        var updated = previous
        dataStore.edit { prefs ->
            val stored = DailyGoalProgress(
                date = prefs[DAILY_GOAL_DATE_KEY] ?: "",
                count = prefs[DAILY_GOAL_COUNT_KEY] ?: 0
            )
            previous = DailyGoalTracker.forToday(stored, today)
            updated = DailyGoalTracker.recordCompletions(stored, today, completions)
            prefs[DAILY_GOAL_DATE_KEY] = updated.date
            prefs[DAILY_GOAL_COUNT_KEY] = updated.count
        }
        return DailyGoalCompletionResult(previous, updated)
    }

    suspend fun setDailyGoalProgress(date: String, count: Int) {
        dataStore.edit {
            it[DAILY_GOAL_DATE_KEY] = date
            it[DAILY_GOAL_COUNT_KEY] = count
        }
    }
}
