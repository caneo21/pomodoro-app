package com.pomodoro.timer.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerService
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<TimerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        // The ViewModel collects settings for the lifetime of its scope; without an onCleared
        // equivalent the collector outlives the test and reads a deleted TemporaryFolder.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newRepository(): TimerSettingsRepository {
        val file = File(tempFolder.newFolder(), "settings.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        return TimerSettingsRepository(dataStore)
    }

    private fun newViewModel(
        timerState: StateFlow<TimerState>,
        repository: TimerSettingsRepository = newRepository(),
        onCommand: (String) -> Unit = {},
        onUpdateDurations: (TimerSettings) -> Unit = {}
    ): TimerViewModel =
        TimerViewModel(timerState, repository, onCommand, onUpdateDurations)
            .also { createdViewModels.add(it) }

    @Test
    fun `uiState reflects the injected timer state and settings`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.SHORT_BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = newViewModel(timerState)

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.SHORT_BREAK }

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
        assertEquals(25, uiState.focusMinutes)
    }

    @Test
    fun `start pause and reset forward the matching TimerService actions`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = newViewModel(timerState, onCommand = { sentActions.add(it) })

        viewModel.start()
        viewModel.pause()
        viewModel.reset()

        assertEquals(
            listOf(TimerService.ACTION_START, TimerService.ACTION_PAUSE, TimerService.ACTION_RESET),
            sentActions
        )
    }

    @Test
    fun `updateFocusMinutes persists through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateFocusMinutes(40)

        val focusMinutes = viewModel.uiState.first { it.focusMinutes == 40 }.focusMinutes

        assertEquals(40, focusMinutes)
    }

    @Test
    fun `settings emissions are pushed to the timer service as duration updates`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val pushedSettings = MutableStateFlow<TimerSettings?>(null)
        val viewModel = newViewModel(
            timerState,
            onUpdateDurations = { pushedSettings.value = it }
        )

        // Cold start: the persisted (here, default) settings reach the service without user action.
        val initial = pushedSettings.first { it != null }
        assertEquals(TimerSettings(focusMinutes = 25, breakMinutes = 5), initial)

        viewModel.updateFocusMinutes(40)
        viewModel.updateBreakMinutes(10)

        val updated = pushedSettings.first { it?.focusMinutes == 40 && it.breakMinutes == 10 }
        assertEquals(40, updated?.focusMinutes)
        assertEquals(10, updated?.breakMinutes)
    }

    @Test
    fun `updateLongBreakMinutes and updatePomodorosUntilLongBreak persist through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateLongBreakMinutes(20)
        viewModel.updatePomodorosUntilLongBreak(3)

        val uiState = viewModel.uiState.first { it.longBreakMinutes == 20 && it.pomodorosUntilLongBreak == 3 }

        assertEquals(20, uiState.longBreakMinutes)
        assertEquals(3, uiState.pomodorosUntilLongBreak)
    }

    @Test
    fun `updateAutoStartBreak and updateAutoStartFocus persist through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateAutoStartBreak(true)
        viewModel.updateAutoStartFocus(true)

        val uiState = viewModel.uiState.first { it.autoStartBreak && it.autoStartFocus }

        assertEquals(true, uiState.autoStartBreak)
        assertEquals(true, uiState.autoStartFocus)
    }
}
