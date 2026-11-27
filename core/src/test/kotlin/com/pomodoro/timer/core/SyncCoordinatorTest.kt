package com.pomodoro.timer.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

private class FakeSyncTransport(private val storedRemoteState: SyncPayload? = null) : SyncTransport {
    val published = mutableListOf<SyncPayload>()
    private val remoteFlow = MutableSharedFlow<SyncPayload>(extraBufferCapacity = 8)

    override suspend fun publish(payload: SyncPayload) {
        published.add(payload)
    }

    override fun observeRemoteState(): Flow<SyncPayload> = remoteFlow

    override suspend fun getCurrentRemoteState(): SyncPayload? = storedRemoteState

    suspend fun emitRemote(payload: SyncPayload) {
        remoteFlow.emit(payload)
    }
}

/** Throws on the first [failures] subscriptions to [observeRemoteState], then behaves normally. */
private class FlakySyncTransport(private val failures: Int) : SyncTransport {
    var subscriptions = 0
        private set
    private val remoteFlow = MutableSharedFlow<SyncPayload>(extraBufferCapacity = 8)

    override suspend fun publish(payload: SyncPayload) = Unit

    override fun observeRemoteState(): Flow<SyncPayload> = flow {
        val attempt = subscriptions++
        if (attempt < failures) throw IllegalStateException("transient transport failure")
        emitAll(remoteFlow)
    }

    override suspend fun getCurrentRemoteState(): SyncPayload? = null

    suspend fun emitRemote(payload: SyncPayload) {
        remoteFlow.emit(payload)
    }
}

/** Never resolves [getCurrentRemoteState], to exercise [SyncCoordinator.awaitInitialSync]'s timeout. */
private class HangingSyncTransport : SyncTransport {
    override suspend fun publish(payload: SyncPayload) = Unit
    override fun observeRemoteState(): Flow<SyncPayload> = MutableSharedFlow()
    override suspend fun getCurrentRemoteState(): SyncPayload? {
        awaitCancellation()
    }
}

/** Always throws from [publish], simulating the Wearable Data Layer being unavailable. */
private class FailingPublishSyncTransport : SyncTransport {
    var publishAttempts = 0
        private set

    override suspend fun publish(payload: SyncPayload) {
        publishAttempts++
        throw IllegalStateException("simulated Wearable.API unavailable")
    }

    override fun observeRemoteState(): Flow<SyncPayload> = MutableSharedFlow()
    override suspend fun getCurrentRemoteState(): SyncPayload? = null
}

private fun payload(lastActionAtMillis: Long, isRunning: Boolean = true) = SyncPayload(
    phase = TimerPhase.FOCUS,
    isRunning = isRunning,
    remainingMillis = 100_000L,
    totalMillis = 1_500_000L,
    focusMinutes = 25,
    breakMinutes = 5,
    lastActionAtMillis = lastActionAtMillis
)

class SyncCoordinatorTest {

    @Test
    fun `isRemoteNewer is true when there is no local state yet`() {
        assertEquals(true, isRemoteNewer(local = null, remote = payload(1_000L)))
    }

    @Test
    fun `isRemoteNewer compares lastActionAtMillis`() {
        assertEquals(false, isRemoteNewer(local = payload(2_000L), remote = payload(1_000L)))
        assertEquals(true, isRemoteNewer(local = payload(1_000L), remote = payload(2_000L)))
        assertEquals(false, isRemoteNewer(local = payload(2_000L), remote = payload(2_000L)))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `publishLocalState sends the payload through the transport`() = runTest {
        val transport = FakeSyncTransport()
        val coordinator = SyncCoordinator(transport, this) {}

        coordinator.publishLocalState(payload(5_000L))
        advanceUntilIdle()

        assertEquals(1, transport.published.size)
        assertEquals(5_000L, transport.published[0].lastActionAtMillis)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a publish failure is swallowed rather than crashing the scope`() = runTest {
        val transport = FailingPublishSyncTransport()
        val coordinator = SyncCoordinator(transport, this) {}

        coordinator.publishLocalState(payload(5_000L))
        advanceUntilIdle()

        assertEquals(1, transport.publishAttempts)
        assertTrue(coroutineContext[Job]!!.isActive)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `newer remote state triggers the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        val job = coordinator.start()
        advanceUntilIdle()

        transport.emitRemote(payload(10_000L))
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(10_000L, received[0].lastActionAtMillis)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stale remote state does not trigger the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        val job = coordinator.start()
        advanceUntilIdle()

        coordinator.publishLocalState(payload(20_000L))
        transport.emitRemote(payload(10_000L))
        advanceUntilIdle()

        assertEquals(0, received.size)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a self-echo of the just-published payload does not re-trigger the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        val job = coordinator.start()
        advanceUntilIdle()

        val mine = payload(30_000L)
        coordinator.publishLocalState(mine)
        transport.emitRemote(mine) // the transport echoing our own write back to us
        advanceUntilIdle()

        assertEquals(0, received.size)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a failing remote flow is retried instead of killing sync permanently`() = runTest {
        val transport = FlakySyncTransport(failures = 2)
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }

        val job = coordinator.start()
        advanceUntilIdle()

        assertEquals(3, transport.subscriptions)
        transport.emitRemote(payload(60_000L))
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(60_000L, received[0].lastActionAtMillis)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a remote flow that never recovers is swallowed rather than crashing the scope`() = runTest {
        val transport = FlakySyncTransport(failures = Int.MAX_VALUE)
        val coordinator = SyncCoordinator(transport, this) {}

        coordinator.start()
        advanceUntilIdle()

        // Retries are exhausted and the failure is swallowed by .catch, rather than propagating out
        // of the launched coroutine and crashing the parent scope.
        assertTrue(coroutineContext[Job]!!.isActive)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `start seeds lastKnown from getCurrentRemoteState before any remote event arrives`() = runTest {
        val stored = payload(40_000L)
        val transport = FakeSyncTransport(storedRemoteState = stored)
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }

        val job = coordinator.start()
        advanceUntilIdle()

        assertEquals(listOf(stored), received)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a stored seed older than a payload already published locally does not overwrite it`() = runTest {
        val transport = FakeSyncTransport(storedRemoteState = payload(10_000L))
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }

        coordinator.publishLocalState(payload(50_000L))
        val job = coordinator.start()
        advanceUntilIdle()

        assertEquals(0, received.size)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `awaitInitialSync returns once the seed attempt resolves`() = runTest {
        val transport = FakeSyncTransport(storedRemoteState = payload(1_000L))
        val coordinator = SyncCoordinator(transport, this) {}

        val job = coordinator.start()
        coordinator.awaitInitialSync(timeoutMillis = 10_000)

        // If awaitInitialSync had actually blocked for the full timeout, this virtual-time assertion
        // would fail: runTest's scheduler only advances as far as awaitInitialSync's own suspension
        // required, which is far less than 10s once the seed coroutine has already completed.
        assertTrue(testScheduler.currentTime < 10_000)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `awaitInitialSync gives up after its timeout when the seed never resolves`() = runTest {
        val coordinator = SyncCoordinator(HangingSyncTransport(), this) {}

        val job = coordinator.start()
        coordinator.awaitInitialSync(timeoutMillis = 2_000)

        assertEquals(2_000L, testScheduler.currentTime)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `awaitInitialSync is safe to call more than once`() = runTest {
        val transport = FakeSyncTransport(storedRemoteState = payload(1_000L))
        val coordinator = SyncCoordinator(transport, this) {}

        val job = coordinator.start()
        coordinator.awaitInitialSync()
        coordinator.awaitInitialSync()
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `the seed is skipped when a shared ledger already adopted that payload in the background`() = runTest {
        val stored = payload(1_000L, isRunning = false)
        val ledger = SyncLedger()
        assertEquals(BackgroundSyncAction.ADOPT, ledger.offerFromBackground(stored, serviceAlive = false))
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(FakeSyncTransport(storedRemoteState = stored), this, ledger) { received.add(it) }

        val job = coordinator.start()
        coordinator.awaitInitialSync()
        advanceUntilIdle()

        assertEquals(emptyList<SyncPayload>(), received)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `the seed still adopts a stored payload newer than the shared ledger`() = runTest {
        val ledger = SyncLedger().apply { recordLocal(payload(1_000L)) }
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(FakeSyncTransport(storedRemoteState = payload(2_000L)), this, ledger) {
            received.add(it)
        }

        val job = coordinator.start()
        coordinator.awaitInitialSync()
        advanceUntilIdle()

        assertEquals(listOf(2_000L), received.map { it.lastActionAtMillis })
        assertEquals(2_000L, ledger.current?.lastActionAtMillis)
        job.cancel()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `a local publish through the coordinator makes the background listener ignore its echo`() = runTest {
        val ledger = SyncLedger()
        val coordinator = SyncCoordinator(FakeSyncTransport(), this, ledger) {}

        coordinator.publishLocalState(payload(5_000L))
        advanceUntilIdle()

        assertEquals(BackgroundSyncAction.IGNORE, ledger.offerFromBackground(payload(5_000L), serviceAlive = false))
    }
}
