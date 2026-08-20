package com.pomodoro.timer.wear.ui

import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.timer.WearTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<WearTimerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        timerState: StateFlow<TimerState>,
        pomodorosUntilLongBreak: StateFlow<Int> = MutableStateFlow(4),
        colorTheme: StateFlow<ColorTheme> = MutableStateFlow(ColorTheme.DEFAULT),
        onCommand: (String) -> Unit = {}
    ): WearTimerViewModel =
        WearTimerViewModel(timerState, pomodorosUntilLongBreak, colorTheme, onCommand).also { createdViewModels.add(it) }

    @Test
    fun `uiState reflects the injected timer state`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.SHORT_BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = newViewModel(timerState)

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.SHORT_BREAK }

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
    }

    @Test
    fun `uiState reflects the injected pomodoros-until-long-break count`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState, pomodorosUntilLongBreak = MutableStateFlow(3))

        val uiState = viewModel.uiState.first { it.pomodorosUntilLongBreak == 3 }

        assertEquals(3, uiState.pomodorosUntilLongBreak)
    }

    @Test
    fun `start pause and reset forward the matching WearTimerService actions`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = newViewModel(timerState, onCommand = { sentActions.add(it) })

        viewModel.start()
        viewModel.pause()
        viewModel.reset()

        assertEquals(
            listOf(WearTimerService.ACTION_START, WearTimerService.ACTION_PAUSE, WearTimerService.ACTION_RESET),
            sentActions
        )
    }
}
