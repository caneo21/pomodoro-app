package com.pomodoro.timer.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

private class FakeSyncTransport : SyncTransport {
    val published = mutableListOf<SyncPayload>()
    private val remoteFlow = MutableSharedFlow<SyncPayload>(extraBufferCapacity = 8)

    override suspend fun publish(payload: SyncPayload) {
        published.add(payload)
    }

    override fun observeRemoteState(): Flow<SyncPayload> = remoteFlow

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

    suspend fun emitRemote(payload: SyncPayload) {
        remoteFlow.emit(payload)
    }
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
}
