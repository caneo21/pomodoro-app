package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimerSettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES_KEY = intPreferencesKey("break_minutes")
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 60
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 30
    }

    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES
        )
    }

    suspend fun updateFocusMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_MINUTES_KEY] = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES) }
    }

    suspend fun updateBreakMinutes(minutes: Int) {
        dataStore.edit { it[BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) }
    }
}
