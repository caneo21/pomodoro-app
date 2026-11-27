package com.pomodoro.timer.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** "가장 최근 조작이 이김": a remote payload wins if we have no local state yet, or it is newer. */
fun isRemoteNewer(local: SyncPayload?, remote: SyncPayload): Boolean =
    local == null || remote.lastActionAtMillis > local.lastActionAtMillis

/**
 * Wires a [SyncTransport] to a local timer: forwards locally-produced [SyncPayload]s out, and invokes
 * [onRemoteStateIsNewer] whenever an incoming payload is newer than the latest one [ledger]
 * knows about (either published locally or previously received).
 *
 * [start] also seeds [ledger] from [SyncTransport.getCurrentRemoteState] before
 * [SyncTransport.observeRemoteState] takes over, since the latter only reports *future* changes.
 * [awaitInitialSync] lets a caller hold off acting on a local user command until that seed attempt
 * has resolved (or timed out) — without it, a freshly-started device's very first action can publish
 * default/stale settings with a timestamp that beats its peer's real session in [isRemoteNewer],
 * corrupting the peer before this device ever learns what it should have adopted instead.
 *
 * "Latest known" lives in [ledger]. Pass the process-wide one when something else (a background Data
 * Layer listener) also adopts payloads into the same engine, so neither adopts a payload twice nor
 * rolls the engine back to one older than the other already took.
 */
class SyncCoordinator(
    private val transport: SyncTransport,
    private val scope: CoroutineScope,
    private val ledger: SyncLedger = SyncLedger(),
    private val onRemoteStateIsNewer: (SyncPayload) -> Unit
) {
    private companion object {
        const val OBSERVE_RETRY_ATTEMPTS = 5L
        const val OBSERVE_RETRY_DELAY_MILLIS = 2_000L
        const val DEFAULT_INITIAL_SYNC_TIMEOUT_MILLIS = 2_000L
    }

    private val initialSyncDone = CompletableDeferred<Unit>()

    /**
     * Suspends until the cold-start seed launched by [start] has resolved, or [timeoutMillis] elapses
     * — whichever comes first. Safe to call more than once; once the seed has resolved this returns
     * immediately every time after.
     */
    suspend fun awaitInitialSync(timeoutMillis: Long = DEFAULT_INITIAL_SYNC_TIMEOUT_MILLIS) {
        withTimeoutOrNull(timeoutMillis) { initialSyncDone.await() }
    }

    /**
     * Launches the seed fetch as a child of the returned [Job], so cancelling it (e.g. when a
     * service tears down) stops both the seed attempt and the live listener together — a caller
     * only ever holds one handle.
     */
    fun start(): Job = scope.launch {
        launch {
            val seed = runCatching { transport.getCurrentRemoteState() }.getOrNull()
            if (seed != null && ledger.acceptIfNewer(seed)) onRemoteStateIsNewer(seed)
            initialSyncDone.complete(Unit)
        }
        transport.observeRemoteState()
            .retry(OBSERVE_RETRY_ATTEMPTS) { delay(OBSERVE_RETRY_DELAY_MILLIS); true }
            .catch { }
            .collect { remote ->
                if (ledger.acceptIfNewer(remote)) onRemoteStateIsNewer(remote)
            }
    }

    fun publishLocalState(payload: SyncPayload) {
        ledger.recordLocal(payload)
        scope.launch { runCatching { transport.publish(payload) } }
    }
}
