package com.pomodoro.timer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    timerState: StateFlow<TimerState>,
    private val settingsRepository: TimerSettingsRepository,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> =
        combine(timerState, settingsRepository.settings, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun start() = onCommand(TimerService.ACTION_START)
    fun pause() = onCommand(TimerService.ACTION_PAUSE)
    fun reset() = onCommand(TimerService.ACTION_RESET)

    fun updateFocusMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateFocusMinutes(minutes) }
    }

    fun updateBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateBreakMinutes(minutes) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = TimerSettingsRepository(appContext.dataStore)
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, TimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return TimerViewModel(TimerService.state, repository, onCommand) as T
        }
    }
}
