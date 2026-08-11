package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
}
