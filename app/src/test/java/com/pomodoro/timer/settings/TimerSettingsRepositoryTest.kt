package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimerSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_settings.preferences_pb")
        return PreferenceDataStoreFactory.create { file }
    }

    @Test
    fun `settings default to 25 focus and 5 break minutes`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(25, settings.focusMinutes)
        assertEquals(5, settings.breakMinutes)
    }

    @Test
    fun `updateFocusMinutes persists and clamps to 5 to 60 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateFocusMinutes(45)
        assertEquals(45, repository.settings.first().focusMinutes)

        repository.updateFocusMinutes(200)
        assertEquals(60, repository.settings.first().focusMinutes)

        repository.updateFocusMinutes(0)
        assertEquals(5, repository.settings.first().focusMinutes)
    }

    @Test
    fun `updateBreakMinutes persists and clamps to 1 to 30 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateBreakMinutes(10)
        assertEquals(10, repository.settings.first().breakMinutes)

        repository.updateBreakMinutes(99)
        assertEquals(30, repository.settings.first().breakMinutes)

        repository.updateBreakMinutes(0)
        assertEquals(1, repository.settings.first().breakMinutes)
    }

    @Test
    fun `settings default to 15 minute long break and 4 pomodoros until long break`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(15, settings.longBreakMinutes)
        assertEquals(4, settings.pomodorosUntilLongBreak)
    }

    @Test
    fun `updateLongBreakMinutes persists and clamps to 5 to 45 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateLongBreakMinutes(20)
        assertEquals(20, repository.settings.first().longBreakMinutes)

        repository.updateLongBreakMinutes(99)
        assertEquals(45, repository.settings.first().longBreakMinutes)

        repository.updateLongBreakMinutes(0)
        assertEquals(5, repository.settings.first().longBreakMinutes)
    }

    @Test
    fun `updatePomodorosUntilLongBreak persists and clamps to 2 to 8 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updatePomodorosUntilLongBreak(3)
        assertEquals(3, repository.settings.first().pomodorosUntilLongBreak)

        repository.updatePomodorosUntilLongBreak(99)
        assertEquals(8, repository.settings.first().pomodorosUntilLongBreak)

        repository.updatePomodorosUntilLongBreak(0)
        assertEquals(2, repository.settings.first().pomodorosUntilLongBreak)
    }

    @Test
    fun `settings default to autostart disabled`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(false, settings.autoStartBreak)
        assertEquals(false, settings.autoStartFocus)
    }

    @Test
    fun `updateAutoStartBreak and updateAutoStartFocus persist independently`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateAutoStartBreak(true)
        assertEquals(true, repository.settings.first().autoStartBreak)
        assertEquals(false, repository.settings.first().autoStartFocus)

        repository.updateAutoStartFocus(true)
        assertEquals(true, repository.settings.first().autoStartBreak)
        assertEquals(true, repository.settings.first().autoStartFocus)
    }

    @Test
    fun `settings default to keep screen on disabled`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(false, settings.keepScreenOn)
    }

    @Test
    fun `updateKeepScreenOn persists`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateKeepScreenOn(true)
        assertEquals(true, repository.settings.first().keepScreenOn)

        repository.updateKeepScreenOn(false)
        assertEquals(false, repository.settings.first().keepScreenOn)
    }
}
