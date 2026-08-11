package com.pomodoro.timer.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newRepository(): TimerSettingsRepository {
        val file = File(tempFolder.newFolder(), "settings.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        return TimerSettingsRepository(dataStore)
    }

    @Test
    fun `uiState reflects the injected timer state and settings`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = TimerViewModel(timerState, newRepository(), onCommand = {})

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.BREAK }

        assertEquals(TimerPhase.BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
        assertEquals(25, uiState.focusMinutes)
    }

    @Test
    fun `start pause and reset forward the matching TimerService actions`() = runTest {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = TimerViewModel(timerState, newRepository(), onCommand = { sentActions.add(it) })

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
        val repository = newRepository()
        val viewModel = TimerViewModel(timerState, repository, onCommand = {})

        viewModel.updateFocusMinutes(40)

        val focusMinutes = viewModel.uiState.first { it.focusMinutes == 40 }.focusMinutes

        assertEquals(40, focusMinutes)
    }
}
