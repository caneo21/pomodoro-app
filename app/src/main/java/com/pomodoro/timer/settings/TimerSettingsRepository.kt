package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pomodoro.timer.core.TimerDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimerSettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES_KEY = intPreferencesKey("break_minutes")
        val LONG_BREAK_MINUTES_KEY = intPreferencesKey("long_break_minutes")
        val POMODOROS_UNTIL_LONG_BREAK_KEY = intPreferencesKey("pomodoros_until_long_break")
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
    }

    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES,
            longBreakMinutes = prefs[LONG_BREAK_MINUTES_KEY] ?: DEFAULT_LONG_BREAK_MINUTES,
            pomodorosUntilLongBreak = prefs[POMODOROS_UNTIL_LONG_BREAK_KEY] ?: DEFAULT_POMODOROS_UNTIL_LONG_BREAK
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
}
