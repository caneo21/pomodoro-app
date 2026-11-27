package com.pomodoro.timer.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.pomodoro.timer.core.BackgroundSyncAction
import com.pomodoro.timer.timer.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Receives the watch's timer state while [TimerService] is not running. Google Play services starts
 * this (and the process, if needed) for every change at the timer path, so a session started on the
 * watch gets the phone's own phase-end alarm and ongoing notification without opening the app. While
 * the timer service runs, its own listener handles the same event and this one leaves it alone.
 */
class PhoneSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // The buffer is only valid during this call, so decode before handing anything off.
        val remote = newestTimerStateChange(
            dataEvents.map { event ->
                DataLayerChange(event.type == DataEvent.TYPE_CHANGED, event.dataItem.uri.path) {
                    PlayServicesSyncTransport.decode(DataMapItem.fromDataItem(event.dataItem).dataMap)
                }
            }
        ) ?: return
        // Callbacks arrive on a background thread; the engine and timer service live on the main
        // one. Blocking here until the work (including the DataStore write) is done also keeps the
        // process from being treated as idle before it is.
        runBlocking {
            val action = withContext(Dispatchers.Main) {
                TimerService.backgroundSyncHandler(this@PhoneSyncListenerService).handle(remote)
            }
            if (action == BackgroundSyncAction.ADOPT) {
                TimerService.persistDailyGoal(this@PhoneSyncListenerService, remote)
            }
        }
    }
}
