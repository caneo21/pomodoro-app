package com.pomodoro.timer.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.BuildConfig
import com.pomodoro.timer.core.LatestRelease
import com.pomodoro.timer.core.UpdateCoordinator
import com.pomodoro.timer.settings.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds the "new version available" banner. A null [coordinator] (debug builds) never shows one. */
class UpdateViewModel(private val coordinator: UpdateCoordinator?) : ViewModel() {

    private val _banner = MutableStateFlow<LatestRelease?>(null)
    val banner: StateFlow<LatestRelease?> = _banner.asStateFlow()

    init {
        if (coordinator != null) {
            viewModelScope.launch { _banner.value = coordinator.refresh() }
        }
    }

    fun dismiss() {
        val release = _banner.value ?: return
        _banner.value = null
        coordinator?.let { viewModelScope.launch { it.dismiss(release.tag) } }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val coordinator = if (BuildConfig.DEBUG) {
                null
            } else {
                UpdateCoordinator(
                    store = DataStoreUpdateInfoStore(appContext.dataStore),
                    fetcher = GitHubReleaseFetcher(),
                    installedVersion = BuildConfig.VERSION_NAME,
                    nowMillis = System::currentTimeMillis,
                )
            }
            return UpdateViewModel(coordinator) as T
        }
    }
}
