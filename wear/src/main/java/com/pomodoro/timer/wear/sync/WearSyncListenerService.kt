package com.pomodoro.timer.wear.sync

import android.os.Looper
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.wear.timer.WearTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Receives the phone's timer state while [WearTimerService] is not running. Google Play services
 * starts this (and the process, if needed) for every change at the timer path, so a pause, reset or
 * start on the phone reaches the watch before the user opens the app. While the timer service runs,
 * its own listener handles the same event and this one leaves it alone.
 */
class WearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // The buffer is only valid during this call, so decode before handing anything off.
        val remote = newestTimerStateChange(
            dataEvents.map { event ->
                DataLayerChange(event.type == DataEvent.TYPE_CHANGED, event.dataItem.uri.path) {
                    WearPlayServicesSyncTransport.decode(DataMapItem.fromDataItem(event.dataItem).dataMap)
                }
            }
        ) ?: return
        onMainThread(remote)
    }

    /**
     * The engine and the timer service live on the main thread. Blocking this (background) callback
     * thread until the work is done also keeps the process from being treated as idle before it is.
     */
    private fun onMainThread(remote: SyncPayload) {
        val handle = { WearTimerService.backgroundSyncHandler(this).handle(remote) }
        if (Looper.myLooper() == Looper.getMainLooper()) handle() else runBlocking(Dispatchers.Main) { handle() }
    }
}
