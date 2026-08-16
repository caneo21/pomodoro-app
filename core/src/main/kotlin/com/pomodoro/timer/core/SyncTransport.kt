package com.pomodoro.timer.core

import kotlinx.coroutines.flow.Flow

/** Abstraction over however payloads actually cross from one device to the other. */
interface SyncTransport {
    suspend fun publish(payload: SyncPayload)
    fun observeRemoteState(): Flow<SyncPayload>
}
