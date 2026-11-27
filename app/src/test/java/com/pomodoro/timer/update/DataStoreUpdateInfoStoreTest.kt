package com.pomodoro.timer.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pomodoro.timer.core.LatestRelease
import com.pomodoro.timer.core.StoredUpdateInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreUpdateInfoStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_settings.preferences_pb")
        return PreferenceDataStoreFactory.create { file }
    }

    @Test
    fun `empty store loads defaults`() = runTest {
        assertEquals(StoredUpdateInfo(), DataStoreUpdateInfoStore(newDataStore()).load())
    }

    @Test
    fun `saveCheckResult persists time, tag, and url`() = runTest {
        val store = DataStoreUpdateInfoStore(newDataStore())

        store.saveCheckResult(42, LatestRelease("v1.1.0", "u"))

        assertEquals(StoredUpdateInfo(42, "v1.1.0", "u", null), store.load())
    }

    @Test
    fun `saveDismissed keeps the other fields`() = runTest {
        val store = DataStoreUpdateInfoStore(newDataStore())
        store.saveCheckResult(42, LatestRelease("v1.1.0", "u"))

        store.saveDismissed("v1.1.0")

        assertEquals(StoredUpdateInfo(42, "v1.1.0", "u", "v1.1.0"), store.load())
    }
}
