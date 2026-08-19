package com.pomodoro.timer.wear.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncKeys
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The watch-side twin of `:app`'s `PlayServicesSyncTransport` — same path, same wire format. */
class WearPlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(SyncKeys.TIMER_STATE_PATH).apply {
            dataMap.putString(SyncKeys.KEY_PHASE, payload.phase.name)
            dataMap.putBoolean(SyncKeys.KEY_IS_RUNNING, payload.isRunning)
            dataMap.putLong(SyncKeys.KEY_REMAINING_MILLIS, payload.remainingMillis)
            dataMap.putLong(SyncKeys.KEY_TOTAL_MILLIS, payload.totalMillis)
            dataMap.putInt(SyncKeys.KEY_FOCUS_MINUTES, payload.focusMinutes)
            dataMap.putInt(SyncKeys.KEY_BREAK_MINUTES, payload.breakMinutes)
            dataMap.putLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS, payload.lastActionAtMillis)
            dataMap.putInt(SyncKeys.KEY_LONG_BREAK_MINUTES, payload.longBreakMinutes)
            dataMap.putInt(SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK, payload.pomodorosUntilLongBreak)
            dataMap.putInt(SyncKeys.KEY_COMPLETED_POMODOROS, payload.completedPomodoros)
            dataMap.putBoolean(SyncKeys.KEY_AUTO_START_BREAK, payload.autoStartBreak)
            dataMap.putBoolean(SyncKeys.KEY_AUTO_START_FOCUS, payload.autoStartFocus)
            dataMap.putBoolean(SyncKeys.KEY_DAILY_GOAL_ENABLED, payload.dailyGoalEnabled)
            dataMap.putInt(SyncKeys.KEY_DAILY_GOAL_TARGET, payload.dailyGoalTarget)
            dataMap.putInt(SyncKeys.KEY_DAILY_GOAL_COUNT, payload.dailyGoalCount)
            dataMap.putString(SyncKeys.KEY_DAILY_GOAL_DATE, payload.dailyGoalDate)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == SyncKeys.TIMER_STATE_PATH
                ) {
                    trySend(decode(DataMapItem.fromDataItem(event.dataItem).dataMap))
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }

    /**
     * Reads whatever state is already published, since [observeRemoteState] only ever delivers
     * *future* changes and would otherwise leave a freshly-started service stuck on defaults.
     */
    override suspend fun getCurrentRemoteState(): SyncPayload? {
        val items = dataClient.dataItems.await()
        try {
            for (item in items) {
                if (item.uri.path == SyncKeys.TIMER_STATE_PATH) {
                    return decode(DataMapItem.fromDataItem(item).dataMap)
                }
            }
        } finally {
            items.release()
        }
        return null
    }

    private fun decode(map: DataMap): SyncPayload = SyncPayload(
        phase = enumValues<TimerPhase>()
            .firstOrNull { it.name == map.getString(SyncKeys.KEY_PHASE, TimerPhase.FOCUS.name) }
            ?: TimerPhase.FOCUS,
        isRunning = map.getBoolean(SyncKeys.KEY_IS_RUNNING),
        remainingMillis = map.getLong(SyncKeys.KEY_REMAINING_MILLIS),
        totalMillis = map.getLong(SyncKeys.KEY_TOTAL_MILLIS),
        focusMinutes = map.getInt(SyncKeys.KEY_FOCUS_MINUTES),
        breakMinutes = map.getInt(SyncKeys.KEY_BREAK_MINUTES),
        lastActionAtMillis = map.getLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS),
        longBreakMinutes = map.getInt(SyncKeys.KEY_LONG_BREAK_MINUTES, TimerDefaults.DEFAULT_LONG_BREAK_MINUTES),
        pomodorosUntilLongBreak = map.getInt(
            SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK,
            TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        ),
        completedPomodoros = map.getInt(SyncKeys.KEY_COMPLETED_POMODOROS),
        autoStartBreak = map.getBoolean(SyncKeys.KEY_AUTO_START_BREAK, false),
        autoStartFocus = map.getBoolean(SyncKeys.KEY_AUTO_START_FOCUS, false),
        dailyGoalEnabled = map.getBoolean(SyncKeys.KEY_DAILY_GOAL_ENABLED, false),
        dailyGoalTarget = map.getInt(SyncKeys.KEY_DAILY_GOAL_TARGET, TimerDefaults.DEFAULT_DAILY_GOAL_TARGET),
        dailyGoalCount = map.getInt(SyncKeys.KEY_DAILY_GOAL_COUNT, 0),
        dailyGoalDate = map.getString(SyncKeys.KEY_DAILY_GOAL_DATE, "")
    )
}
