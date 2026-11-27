package com.pomodoro.timer.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckTest {

    private class FakeStore(var info: StoredUpdateInfo = StoredUpdateInfo()) : UpdateInfoStore {
        override suspend fun load(): StoredUpdateInfo = info
        override suspend fun saveCheckResult(nowMillis: Long, release: LatestRelease) {
            info = info.copy(lastCheckedAtMillis = nowMillis, latestTag = release.tag, latestUrl = release.url)
        }
        override suspend fun saveDismissed(tag: String) {
            info = info.copy(dismissedTag = tag)
        }
    }

    private class FakeFetcher(var result: () -> LatestRelease?) : ReleaseFetcher {
        var calls = 0
        override suspend fun fetchLatest(): LatestRelease? {
            calls++
            return result()
        }
    }

    private val now = 10_000_000_000L
    private val v110 = LatestRelease("v1.1.0", "https://x/1.1.0")

    // --- SemVer.parse ---

    @Test
    fun `parse accepts v-prefixed and bare versions`() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(10, 0, 7), SemVer.parse("10.0.7"))
    }

    @Test
    fun `parse rejects malformed tags`() {
        listOf("v1.2", "1.2.3.4", "v1.2.0-beta", "release-5", "", null, "0.0.0-dev").forEach {
            assertNull("expected null for '$it'", SemVer.parse(it))
        }
    }

    // --- UpdatePolicy ---

    @Test
    fun `isNewer compares numerically`() {
        assertTrue(UpdatePolicy.isNewer("v1.10.0", "1.9.0"))
        assertFalse(UpdatePolicy.isNewer("v1.2.0", "1.2.0"))
        assertFalse(UpdatePolicy.isNewer("v1.1.0", "1.2.0"))
    }

    @Test
    fun `isNewer is false when either side is unparseable`() {
        assertFalse(UpdatePolicy.isNewer("v2.0.0", "0.0.0-dev"))
        assertFalse(UpdatePolicy.isNewer("garbage", "1.0.0"))
    }

    @Test
    fun `shouldCheck after 24 hours`() {
        assertTrue(UpdatePolicy.shouldCheck(0, 1))
        assertFalse(UpdatePolicy.shouldCheck(1_000, 1_000 + 86_399_999))
        assertTrue(UpdatePolicy.shouldCheck(1_000, 1_000 + 86_400_000))
    }

    @Test
    fun `shouldCheck when clock moved backwards`() {
        assertTrue(UpdatePolicy.shouldCheck(5_000_000, 1_000))
    }

    @Test
    fun `shouldShow hides dismissed version only`() {
        assertFalse(UpdatePolicy.shouldShow("v1.1.0", "1.0.0", "v1.1.0"))
        assertTrue(UpdatePolicy.shouldShow("v1.2.0", "1.0.0", "v1.1.0"))
    }

    // --- UpdateCoordinator ---

    @Test
    fun `refresh fetches when due and returns newer release`() = runTest {
        val store = FakeStore()
        val coordinator = UpdateCoordinator(store, FakeFetcher { v110 }, "1.0.0") { now }

        assertEquals(v110, coordinator.refresh())
        assertEquals(now, store.info.lastCheckedAtMillis)
    }

    @Test
    fun `refresh skips fetch within interval and uses cached result`() = runTest {
        val store = FakeStore(StoredUpdateInfo(now - 1000, "v1.1.0", "u", null))
        val fetcher = FakeFetcher { error("must not fetch") }
        val coordinator = UpdateCoordinator(store, fetcher, "1.0.0") { now }

        assertEquals(LatestRelease("v1.1.0", "u"), coordinator.refresh())
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `refresh on fetch failure keeps cache and does not advance lastChecked`() = runTest {
        val cached = StoredUpdateInfo(now - 90_000_000, "v1.1.0", "u", null)
        val store = FakeStore(cached)
        val coordinator = UpdateCoordinator(store, FakeFetcher { null }, "1.0.0") { now }

        assertEquals(LatestRelease("v1.1.0", "u"), coordinator.refresh())
        assertEquals(cached, store.info)
    }

    @Test
    fun `refresh survives a throwing fetcher`() = runTest {
        val store = FakeStore()
        val coordinator = UpdateCoordinator(store, FakeFetcher { throw IOException("offline") }, "1.0.0") { now }

        assertNull(coordinator.refresh())
        assertEquals(0L, store.info.lastCheckedAtMillis)
    }

    @Test
    fun `refresh returns null when cached tag equals installed version`() = runTest {
        val store = FakeStore(StoredUpdateInfo(now - 1000, "v1.1.0", "u", null))
        val coordinator = UpdateCoordinator(store, FakeFetcher { null }, "1.1.0") { now }

        assertNull(coordinator.refresh())
    }

    @Test
    fun `refresh never fetches for dev version`() = runTest {
        val fetcher = FakeFetcher { v110 }
        val coordinator = UpdateCoordinator(FakeStore(), fetcher, "0.0.0-dev") { now }

        assertNull(coordinator.refresh())
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `dismiss hides that version and a newer one shows again`() = runTest {
        val store = FakeStore()
        var clock = now
        val fetcher = FakeFetcher { v110 }
        val coordinator = UpdateCoordinator(store, fetcher, "1.0.0") { clock }

        assertEquals(v110, coordinator.refresh())
        coordinator.dismiss("v1.1.0")
        assertNull(coordinator.refresh())

        val v120 = LatestRelease("v1.2.0", "https://x/1.2.0")
        fetcher.result = { v120 }
        clock += UpdatePolicy.CHECK_INTERVAL_MILLIS
        assertEquals(v120, coordinator.refresh())
    }
}
