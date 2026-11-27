package com.pomodoro.timer.core

/**
 * Process-wide record of the newest [SyncPayload] this device has produced or adopted. The timer
 * service's [SyncCoordinator] and the Data Layer listener that runs while that service is stopped
 * share one instance, since they feed the same process-wide engine: whichever of them sees a payload
 * first adopts it, and the other then finds it is not newer and leaves it alone.
 */
class SyncLedger {
    private var lastKnown: SyncPayload? = null

    @get:Synchronized
    val current: SyncPayload?
        get() = lastKnown

    /** A locally-produced payload is by definition the most recent action, so it always wins. */
    @Synchronized
    fun recordLocal(payload: SyncPayload) {
        lastKnown = payload
    }

    /** Records [remote] and returns true if it is newer than everything seen so far ([isRemoteNewer]). */
    @Synchronized
    fun acceptIfNewer(remote: SyncPayload): Boolean {
        if (!isRemoteNewer(lastKnown, remote)) return false
        lastKnown = remote
        return true
    }

    /**
     * Decides what a listener running outside the timer service does with [remote]. While the service
     * is alive its own [SyncCoordinator] receives the same Data Layer event, and it is the one that can
     * update the ongoing notification, ticker and phase-end alarm, so the listener stays out of it.
     */
    @Synchronized
    fun offerFromBackground(remote: SyncPayload, serviceAlive: Boolean): BackgroundSyncAction = when {
        serviceAlive -> BackgroundSyncAction.LEAVE_TO_SERVICE
        acceptIfNewer(remote) -> BackgroundSyncAction.ADOPT
        else -> BackgroundSyncAction.IGNORE
    }
}

enum class BackgroundSyncAction {
    /** Not newer than what this device already has: "most recent action wins" keeps the local state. */
    IGNORE,

    /** The timer service is running and handles the payload through its own [SyncCoordinator]. */
    LEAVE_TO_SERVICE,

    /** Adopt it into the engine now; start the service if [serviceLifecycleAfterAdoption] says so. */
    ADOPT
}

enum class ServiceLifecycle {
    /** Something is counting down: the foreground service runs with its ticker and phase-end alarm. */
    RUN_IN_FOREGROUND,

    /**
     * Nothing is counting down, but a local command is still waiting on the cold-start seed. That
     * command decides whether the service stays up; stopping now would kill it before it runs.
     */
    DEFER_TO_PENDING_COMMAND,

    /** Nothing is counting down: leave the foreground and stop, like a local reset does. */
    STOP
}

/**
 * What the timer service does after adopting a state that arrived from the paired device. A reset or
 * pause made on the other device must not leave this one with a foreground service and an ongoing
 * notification for a session that is no longer running. [pendingLocalCommands] counts start commands
 * whose action has not been handled yet.
 */
fun serviceLifecycleAfterAdoption(adoptedIsRunning: Boolean, pendingLocalCommands: Int): ServiceLifecycle = when {
    adoptedIsRunning -> ServiceLifecycle.RUN_IN_FOREGROUND
    pendingLocalCommands > 0 -> ServiceLifecycle.DEFER_TO_PENDING_COMMAND
    else -> ServiceLifecycle.STOP
}

/**
 * The newest of the payloads stored under the timer path. Each node writes its own data item, so
 * the Data Layer holds one per device, and the cold-start seed must not just take whichever is listed
 * first.
 */
fun newestPayload(payloads: Iterable<SyncPayload>): SyncPayload? = payloads.maxByOrNull { it.lastActionAtMillis }

/**
 * Applies a payload that a Data Layer listener received while the timer service may not be running
 * (the app was closed, or the process was started just to deliver the event). The Android parts are
 * passed in so this stays testable on the JVM:
 *
 * - [adopt] writes the payload into the process-wide engine and settings and returns the result.
 * - [startService] starts the timer service in the foreground so it ticks, shows the ongoing
 *   notification and arms its phase-end alarm. It returns false if the system refused (background
 *   foreground-service start limits).
 * - [armPhaseEndAlarm] is the fallback for that refusal: the exact alarm still wakes the device at the
 *   phase end, and an exact alarm is itself allowed to bring the service up.
 */
class BackgroundSyncHandler(
    private val ledger: SyncLedger,
    private val isServiceAlive: () -> Boolean,
    private val adopt: (SyncPayload) -> TimerState,
    private val startService: () -> Boolean,
    private val armPhaseEndAlarm: (triggerAtMillis: Long) -> Unit,
    private val clock: () -> Long
) {
    fun handle(remote: SyncPayload): BackgroundSyncAction {
        val action = ledger.offerFromBackground(remote, isServiceAlive())
        if (action != BackgroundSyncAction.ADOPT) return action
        val adopted = adopt(remote)
        // No service is running here, so STOP and DEFER both mean "nothing to start".
        if (serviceLifecycleAfterAdoption(adopted.isRunning, pendingLocalCommands = 0) ==
            ServiceLifecycle.RUN_IN_FOREGROUND && !startService()
        ) {
            phaseEndAlarmAtMillis(adopted, clock())?.let(armPhaseEndAlarm)
        }
        return action
    }
}
