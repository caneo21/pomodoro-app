package com.pomodoro.timer.wear.sync

import com.pomodoro.timer.core.SyncKeys
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.newestPayload

/** One entry of a Data Layer event batch, reduced to what the sync listener looks at. */
internal class DataLayerChange(val isChanged: Boolean, val path: String?, val decode: () -> SyncPayload)

/**
 * The newest timer payload changed in one batch of Data Layer events. Deletions and other paths are
 * skipped without being decoded. A batch can hold several writes when delivery was delayed (the
 * device was asleep or out of range), and only the latest action matters.
 */
internal fun newestTimerStateChange(changes: Iterable<DataLayerChange>): SyncPayload? =
    newestPayload(changes.filter { it.isChanged && it.path == SyncKeys.TIMER_STATE_PATH }.map { it.decode() })
