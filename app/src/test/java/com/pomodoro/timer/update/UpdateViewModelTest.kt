package com.pomodoro.timer.update

import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.LatestRelease
import com.pomodoro.timer.core.StoredUpdateInfo
import com.pomodoro.timer.core.UpdateCoordinator
import com.pomodoro.timer.core.UpdateInfoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private class FakeStore(var info: StoredUpdateInfo = StoredUpdateInfo()) : UpdateInfoStore {
        override suspend fun load(): StoredUpdateInfo = info
        override suspend fun saveCheckResult(nowMillis: Long, release: LatestRelease) {
            info = info.copy(lastCheckedAtMillis = nowMillis, latestTag = release.tag, latestUrl = release.url)
        }
        override suspend fun saveDismissed(tag: String) {
            info = info.copy(dismissedTag = tag)
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<UpdateViewModel>()
    private val v110 = LatestRelease("v1.1.0", "https://github.com/caneo21/pomodoro-app/releases/tag/v1.1.0")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun newViewModel(coordinator: UpdateCoordinator?) = UpdateViewModel(coordinator).also { viewModels += it }

    @Test
    fun `banner emits newer release after init`() {
        val coordinator = UpdateCoordinator(FakeStore(), { v110 }, "1.0.0") { 1_000L }

        val viewModel = newViewModel(coordinator)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(v110, viewModel.banner.value)
    }

    @Test
    fun `dismiss clears banner and persists dismissed tag`() {
        val store = FakeStore()
        val viewModel = newViewModel(UpdateCoordinator(store, { v110 }, "1.0.0") { 1_000L })
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.dismiss()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.banner.value)
        assertEquals("v1.1.0", store.info.dismissedTag)
    }

    @Test
    fun `null coordinator never shows a banner`() {
        val viewModel = newViewModel(null)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.banner.value)
    }
}
