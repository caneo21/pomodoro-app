package com.pomodoro.timer.wear.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.timer.WearTimerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WearTimerViewModel(
    timerState: StateFlow<TimerState>,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<WearTimerUiState> =
        timerState.map(::buildWearUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearTimerUiState())

    fun start() = onCommand(WearTimerService.ACTION_START)
    fun pause() = onCommand(WearTimerService.ACTION_PAUSE)
    fun reset() = onCommand(WearTimerService.ACTION_RESET)

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, WearTimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return WearTimerViewModel(WearTimerService.state, onCommand) as T
        }
    }
}
