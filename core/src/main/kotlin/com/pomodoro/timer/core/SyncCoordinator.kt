package com.pomodoro.timer.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

/** "가장 최근 조작이 이김": a remote payload wins if we have no local state yet, or it is newer. */
fun isRemoteNewer(local: SyncPayload?, remote: SyncPayload): Boolean =
    local == null || remote.lastActionAtMillis > local.lastActionAtMillis

/**
 * Wires a [SyncTransport] to a local timer: forwards locally-produced [SyncPayload]s out, and invokes
 * [onRemoteStateIsNewer] whenever an incoming payload is newer than the latest one this coordinator
 * knows about (either published locally or previously received).
 */
class SyncCoordinator(
    private val transport: SyncTransport,
    private val scope: CoroutineScope,
    private val onRemoteStateIsNewer: (SyncPayload) -> Unit
) {
    private companion object {
        const val OBSERVE_RETRY_ATTEMPTS = 5L
        const val OBSERVE_RETRY_DELAY_MILLIS = 2_000L
    }

    private var lastKnown: SyncPayload? = null

    fun start(): Job = scope.launch {
        transport.observeRemoteState()
            .retry(OBSERVE_RETRY_ATTEMPTS) { delay(OBSERVE_RETRY_DELAY_MILLIS); true }
            .catch { }
            .collect { remote ->
                if (isRemoteNewer(lastKnown, remote)) {
                    lastKnown = remote
                    onRemoteStateIsNewer(remote)
                }
            }
    }

    fun publishLocalState(payload: SyncPayload) {
        lastKnown = payload
        scope.launch { transport.publish(payload) }
    }
}
