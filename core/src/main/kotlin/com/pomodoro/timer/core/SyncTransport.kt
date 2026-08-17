package com.pomodoro.timer.core

import kotlinx.coroutines.flow.Flow

/** Abstraction over however payloads actually cross from one device to the other. */
interface SyncTransport {
    suspend fun publish(payload: SyncPayload)
    fun observeRemoteState(): Flow<SyncPayload>

    /**
     * The payload currently stored in the transport, or null if nothing has ever been published.
     * [observeRemoteState] only reports *changes*, so a freshly-attached observer needs this to learn
     * about state the paired device published before it started listening.
     */
    suspend fun getCurrentRemoteState(): SyncPayload?
}
