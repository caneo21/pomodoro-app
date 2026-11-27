package com.pomodoro.timer.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pomodoro.timer.core.LatestRelease
import com.pomodoro.timer.core.StoredUpdateInfo
import com.pomodoro.timer.core.UpdateInfoStore
import kotlinx.coroutines.flow.first

/** Keeps update-check state in the app's existing `timer_settings` DataStore. */
class DataStoreUpdateInfoStore(private val dataStore: DataStore<Preferences>) : UpdateInfoStore {

    companion object {
        val LAST_CHECKED_AT_KEY = longPreferencesKey("update_last_checked_at")
        val LATEST_TAG_KEY = stringPreferencesKey("update_latest_tag")
        val LATEST_URL_KEY = stringPreferencesKey("update_latest_url")
        val DISMISSED_TAG_KEY = stringPreferencesKey("update_dismissed_tag")
    }

    override suspend fun load(): StoredUpdateInfo {
        val prefs = dataStore.data.first()
        return StoredUpdateInfo(
            lastCheckedAtMillis = prefs[LAST_CHECKED_AT_KEY] ?: 0L,
            latestTag = prefs[LATEST_TAG_KEY],
            latestUrl = prefs[LATEST_URL_KEY],
            dismissedTag = prefs[DISMISSED_TAG_KEY],
        )
    }

    override suspend fun saveCheckResult(nowMillis: Long, release: LatestRelease) {
        dataStore.edit {
            it[LAST_CHECKED_AT_KEY] = nowMillis
            it[LATEST_TAG_KEY] = release.tag
            it[LATEST_URL_KEY] = release.url
        }
    }

    override suspend fun saveDismissed(tag: String) {
        dataStore.edit { it[DISMISSED_TAG_KEY] = tag }
    }
}
