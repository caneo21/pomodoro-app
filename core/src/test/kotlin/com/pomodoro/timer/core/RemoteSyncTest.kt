package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSyncTest {

    private fun payload(lastActionAtMillis: Long, isRunning: Boolean = false) = SyncPayload(
        phase = TimerPhase.FOCUS,
        isRunning = isRunning,
        remainingMillis = 1_488_000L,
        totalMillis = 1_500_000L,
        focusMinutes = 25,
        breakMinutes = 5,
        lastActionAtMillis = lastActionAtMillis
    )

    @Test
    fun `a fresh ledger adopts the first payload it is offered in the background`() {
        val ledger = SyncLedger()

        assertEquals(BackgroundSyncAction.ADOPT, ledger.offerFromBackground(payload(1_000L), serviceAlive = false))
        assertEquals(1_000L, ledger.current?.lastActionAtMillis)
    }

    @Test
    fun `the background listener ignores a payload that is not newer`() {
        val ledger = SyncLedger().apply { recordLocal(payload(2_000L)) }

        assertEquals(BackgroundSyncAction.IGNORE, ledger.offerFromBackground(payload(2_000L), serviceAlive = false))
        assertEquals(BackgroundSyncAction.IGNORE, ledger.offerFromBackground(payload(1_000L), serviceAlive = false))
        assertEquals(2_000L, ledger.current?.lastActionAtMillis)
    }

    @Test
    fun `the background listener leaves everything to a running service and records nothing`() {
        val ledger = SyncLedger().apply { recordLocal(payload(1_000L)) }

        assertEquals(
            BackgroundSyncAction.LEAVE_TO_SERVICE,
            ledger.offerFromBackground(payload(2_000L), serviceAlive = true)
        )
        // The service's own coordinator must still find it newer.
        assertTrue(ledger.acceptIfNewer(payload(2_000L)))
    }

    @Test
    fun `a payload adopted once is not adopted again by a second listener`() {
        val ledger = SyncLedger()

        assertTrue(ledger.acceptIfNewer(payload(3_000L)))
        assertFalse(ledger.acceptIfNewer(payload(3_000L)))
    }

    @Test
    fun `a local action always replaces what the ledger knows, even an apparently newer remote`() {
        val ledger = SyncLedger().apply { acceptIfNewer(payload(5_000L)) }

        ledger.recordLocal(payload(4_000L))

        assertEquals(4_000L, ledger.current?.lastActionAtMillis)
    }

    @Test
    fun `a running adopted state keeps the service in the foreground`() {
        assertEquals(ServiceLifecycle.RUN_IN_FOREGROUND, serviceLifecycleAfterAdoption(true, pendingLocalCommands = 0))
        assertEquals(ServiceLifecycle.RUN_IN_FOREGROUND, serviceLifecycleAfterAdoption(true, pendingLocalCommands = 2))
    }

    @Test
    fun `a remote reset or pause stops the service when no local command is waiting`() {
        assertEquals(ServiceLifecycle.STOP, serviceLifecycleAfterAdoption(false, pendingLocalCommands = 0))
    }

    @Test
    fun `a stopped adopted state defers to a local command still waiting on the seed`() {
        assertEquals(
            ServiceLifecycle.DEFER_TO_PENDING_COMMAND,
            serviceLifecycleAfterAdoption(false, pendingLocalCommands = 1)
        )
    }

    @Test
    fun `newestPayload picks the latest action among both devices' items`() {
        val items = listOf(payload(2_000L), payload(5_000L), payload(3_000L))

        assertEquals(5_000L, newestPayload(items)?.lastActionAtMillis)
        assertNull(newestPayload(emptyList()))
    }

    private class FakeEffects(var serviceAlive: Boolean = false, var startSucceeds: Boolean = true) {
        val adopted = mutableListOf<SyncPayload>()
        var serviceStarts = 0
        val armedAlarms = mutableListOf<Long>()
        val engine = TimerEngine(25 * 60_000L, 5 * 60_000L)

        fun handler(ledger: SyncLedger, now: Long) = BackgroundSyncHandler(
            ledger = ledger,
            isServiceAlive = { serviceAlive },
            adopt = { adopted.add(it); engine.adoptRemote(it, now) },
            startService = { serviceStarts++; startSucceeds },
            armPhaseEndAlarm = { armedAlarms.add(it) },
            clock = { now }
        )
    }

    @Test
    fun `a paused state from the other device is adopted without starting the service`() {
        val effects = FakeEffects()

        val action = effects.handler(SyncLedger(), now = 10_000L).handle(payload(9_000L, isRunning = false))

        assertEquals(BackgroundSyncAction.ADOPT, action)
        assertEquals(listOf(9_000L), effects.adopted.map { it.lastActionAtMillis })
        assertEquals(0, effects.serviceStarts)
        assertEquals(emptyList<Long>(), effects.armedAlarms)
    }

    @Test
    fun `a running state from the other device starts the service so its alarm gets armed`() {
        val effects = FakeEffects()

        effects.handler(SyncLedger(), now = 10_000L).handle(payload(10_000L, isRunning = true))

        assertEquals(1, effects.serviceStarts)
        assertEquals(emptyList<Long>(), effects.armedAlarms)
    }

    @Test
    fun `a refused service start falls back to arming the phase-end alarm directly`() {
        val effects = FakeEffects(startSucceeds = false)

        // Captured 2s ago with 1_488_000 ms left, so it ends 1_486_000 ms from now.
        effects.handler(SyncLedger(), now = 10_000L).handle(payload(8_000L, isRunning = true))

        assertEquals(listOf(10_000L + 1_486_000L), effects.armedAlarms)
    }

    @Test
    fun `a running remote state that already ran out on the way is adopted as stopped`() {
        val effects = FakeEffects()

        // Captured long enough ago that the focus phase ended in transit; auto-start is off.
        effects.handler(SyncLedger(), now = 3_000_000L).handle(payload(1_000L, isRunning = true))

        assertEquals(1, effects.adopted.size)
        assertEquals(0, effects.serviceStarts)
    }

    @Test
    fun `the handler does nothing while the service is alive`() {
        val effects = FakeEffects(serviceAlive = true)

        val action = effects.handler(SyncLedger(), now = 10_000L).handle(payload(9_000L, isRunning = true))

        assertEquals(BackgroundSyncAction.LEAVE_TO_SERVICE, action)
        assertEquals(emptyList<SyncPayload>(), effects.adopted)
        assertEquals(0, effects.serviceStarts)
    }

    @Test
    fun `an older payload is ignored by the handler`() {
        val effects = FakeEffects()
        val ledger = SyncLedger().apply { recordLocal(payload(9_500L)) }

        val action = effects.handler(ledger, now = 10_000L).handle(payload(9_000L, isRunning = true))

        assertEquals(BackgroundSyncAction.IGNORE, action)
        assertEquals(emptyList<SyncPayload>(), effects.adopted)
    }
}
