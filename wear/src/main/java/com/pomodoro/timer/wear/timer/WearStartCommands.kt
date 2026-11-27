package com.pomodoro.timer.wear.timer

/** How [WearTimerService.onStartCommand] enters the foreground for a given start action. */
internal enum class ForegroundEntry {
    /** Started with startForegroundService: startForeground is mandatory, within seconds. */
    ALWAYS,

    /** Started with plain startService while normally already in the foreground: best effort. */
    IF_NOT_ALREADY
}

internal fun foregroundEntryFor(action: String?): ForegroundEntry =
    if (action == WearTimerService.ACTION_PHASE_END_ALARM) ForegroundEntry.IF_NOT_ALREADY else ForegroundEntry.ALWAYS
