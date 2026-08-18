package com.pomodoro.timer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    timerState: StateFlow<TimerState>,
    private val settingsRepository: TimerSettingsRepository,
    private val onCommand: (String) -> Unit,
    private val onUpdateDurations: (TimerSettings) -> Unit
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> =
        combine(timerState, settingsRepository.settings, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    init {
        // DataStore replays the persisted value on subscription, so this also syncs the service's
        // engine on cold start, not only when the user edits Settings.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                onUpdateDurations(settings)
            }
        }
    }

    fun start() = onCommand(TimerService.ACTION_START)
    fun pause() = onCommand(TimerService.ACTION_PAUSE)
    fun reset() = onCommand(TimerService.ACTION_RESET)

    fun updateFocusMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateFocusMinutes(minutes) }
    }

    fun updateBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateBreakMinutes(minutes) }
    }

    fun updateLongBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateLongBreakMinutes(minutes) }
    }

    fun updatePomodorosUntilLongBreak(count: Int) {
        viewModelScope.launch { settingsRepository.updatePomodorosUntilLongBreak(count) }
    }

    fun updateAutoStartBreak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoStartBreak(enabled) }
    }

    fun updateAutoStartFocus(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoStartFocus(enabled) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = TimerSettingsRepository(appContext.dataStore)
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, TimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            val onUpdateDurations: (TimerSettings) -> Unit = { settings ->
                val intent = Intent(appContext, TimerService::class.java)
                    .setAction(TimerService.ACTION_UPDATE_DURATIONS)
                    .putExtra(TimerService.EXTRA_FOCUS_MINUTES, settings.focusMinutes)
                    .putExtra(TimerService.EXTRA_BREAK_MINUTES, settings.breakMinutes)
                    .putExtra(TimerService.EXTRA_LONG_BREAK_MINUTES, settings.longBreakMinutes)
                    .putExtra(TimerService.EXTRA_POMODOROS_UNTIL_LONG_BREAK, settings.pomodorosUntilLongBreak)
                    .putExtra(TimerService.EXTRA_AUTO_START_BREAK, settings.autoStartBreak)
                    .putExtra(TimerService.EXTRA_AUTO_START_FOCUS, settings.autoStartFocus)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return TimerViewModel(TimerService.state, repository, onCommand, onUpdateDurations) as T
        }
    }
}
