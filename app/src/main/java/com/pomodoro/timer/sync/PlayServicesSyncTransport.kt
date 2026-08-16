package com.pomodoro.timer.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TIMER_STATE_PATH = "/pomodoro/timer_state"

/**
 * [SyncTransport] backed by the Wearable Data Layer API. A device's own writes typically also arrive
 * back through its own [DataClient.OnDataChangedListener] (a "self-echo"); [SyncCoordinator] already
 * ignores payloads that aren't newer than the last one it published, so no self-filtering is needed
 * here.
 */
class PlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(TIMER_STATE_PATH).apply {
            dataMap.putString("phase", payload.phase.name)
            dataMap.putBoolean("isRunning", payload.isRunning)
            dataMap.putLong("remainingMillis", payload.remainingMillis)
            dataMap.putLong("totalMillis", payload.totalMillis)
            dataMap.putInt("focusMinutes", payload.focusMinutes)
            dataMap.putInt("breakMinutes", payload.breakMinutes)
            dataMap.putLong("lastActionAtMillis", payload.lastActionAtMillis)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == TIMER_STATE_PATH) {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    trySend(
                        SyncPayload(
                            phase = TimerPhase.valueOf(map.getString("phase", TimerPhase.FOCUS.name)),
                            isRunning = map.getBoolean("isRunning"),
                            remainingMillis = map.getLong("remainingMillis"),
                            totalMillis = map.getLong("totalMillis"),
                            focusMinutes = map.getInt("focusMinutes"),
                            breakMinutes = map.getInt("breakMinutes"),
                            lastActionAtMillis = map.getLong("lastActionAtMillis")
                        )
                    )
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
}
