package com.pomodoro.timer.wear.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.pomodoro.timer.core.BackgroundSyncAction
import com.pomodoro.timer.core.BackgroundSyncHandler
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
import com.pomodoro.timer.core.NotificationChannels
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.ServiceLifecycle
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncLedger
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.core.TimerTransition
import com.pomodoro.timer.core.phaseEndAlarmAtMillis
import com.pomodoro.timer.core.serviceLifecycleAfterAdoption
import com.pomodoro.timer.wear.MainActivity
import com.pomodoro.timer.wear.sync.WearPlayServicesSyncTransport
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WearTimerService : Service() {

    companion object {
        const val ACTION_START = "com.pomodoro.timer.wear.action.START"
        const val ACTION_PAUSE = "com.pomodoro.timer.wear.action.PAUSE"
        const val ACTION_RESET = "com.pomodoro.timer.wear.action.RESET"
        const val ACTION_PHASE_END_ALARM = "com.pomodoro.timer.wear.action.PHASE_END_ALARM"

        /** Sent by [backgroundSyncHandler] after it adopted a running state from the phone. */
        const val ACTION_SYNC_REMOTE = "com.pomodoro.timer.wear.action.SYNC_REMOTE"

        const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val DAILY_GOAL_NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "pomodoro_wear_timer_channel"
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerDefaults.DEFAULT_BREAK_MINUTES * 60_000L
        private val defaultLongBreakMillis = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L

        private val engine = TimerEngine(
            defaultFocusMillis,
            defaultBreakMillis,
            defaultLongBreakMillis,
            TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        )

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()

        // Static alongside `engine`, so a service instance restart can't rewind these to the
        // defaults while the engine still holds the user's real durations.
        private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
        private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
        private var longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES

        // Exposed as a StateFlow (rather than a plain var like the settings above) because the
        // watch has no settings screen of its own: WearTimerUiState's cycle-progress display is the
        // only consumer, and it needs to observe changes as they arrive from the phone.
        private val _pomodorosUntilLongBreak = MutableStateFlow(TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK)
        val pomodorosUntilLongBreak: StateFlow<Int> = _pomodorosUntilLongBreak.asStateFlow()

        // Not exposed publicly: nothing in :wear's UI reads these back out. They exist purely to
        // echo the phone's last-known settings when the watch itself publishes a local state
        // change, since TimerEngine doesn't expose these as getters (TimerState intentionally
        // carries only phase/timing/count, not settings).
        private val _autoStartBreak = MutableStateFlow(false)
        private val _autoStartFocus = MutableStateFlow(false)

        // Same "not exposed" reasoning as _autoStartBreak/_autoStartFocus above: nothing in
        // :wear's UI reads these back out in this iteration.
        private val _dailyGoalEnabled = MutableStateFlow(false)
        private val _dailyGoalTarget = MutableStateFlow(TimerDefaults.DEFAULT_DAILY_GOAL_TARGET)

        // Plain var, not a repository-backed value: the watch has no local settings persistence
        // (existing constraint), so this is hydrated purely from sync and resets to zero on a
        // fresh install or process restart, same cold-start gap every other watch setting has.
        private var dailyGoalProgress: DailyGoalProgress = DailyGoalProgress("", 0)

        // Same "no local persistence" reasoning as dailyGoalProgress above: these are configured
        // only from the phone's Settings screen and reach the watch purely via sync.
        private var notificationsEnabled: Boolean = true
        private var vibrationEnabled: Boolean = true
        private var useAlarmSound: Boolean = false

        // Exposed publicly (unlike _autoStartBreak/_dailyGoalEnabled above): the watch's own theme
        // rendering needs this value, the same reason pomodorosUntilLongBreak is a StateFlow.
        private val _colorTheme = MutableStateFlow(ColorTheme.DEFAULT)
        val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

        // Static alongside `engine` for the same reason: the newest payload this process has adopted
        // or published, shared by the service's coordinator and the background listener.
        private val syncLedger = SyncLedger()

        // Main thread only (onCreate/onDestroy and the background handler all run there).
        private var serviceAlive = false

        /** Writes [payload] into the process-wide engine and settings. No service instance needed. */
        private fun adoptRemoteIntoState(payload: SyncPayload): TimerState {
            focusMinutes = payload.focusMinutes
            breakMinutes = payload.breakMinutes
            longBreakMinutes = payload.longBreakMinutes
            _pomodorosUntilLongBreak.value = payload.pomodorosUntilLongBreak
            _autoStartBreak.value = payload.autoStartBreak
            _autoStartFocus.value = payload.autoStartFocus
            _dailyGoalEnabled.value = payload.dailyGoalEnabled
            _dailyGoalTarget.value = payload.dailyGoalTarget
            dailyGoalProgress = DailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount)
            notificationsEnabled = payload.notificationsEnabled
            vibrationEnabled = payload.vibrationEnabled
            useAlarmSound = payload.useAlarmSound
            _colorTheme.value = payload.colorTheme
            _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
            return _state.value
        }

        /**
         * For payloads that arrive while this service may not be running ([WearSyncListenerService],
         * and the app opening). Call on the main thread.
         */
        internal fun backgroundSyncHandler(context: Context): BackgroundSyncHandler {
            val appContext = context.applicationContext
            return BackgroundSyncHandler(
                ledger = syncLedger,
                isServiceAlive = { serviceAlive },
                adopt = ::adoptRemoteIntoState,
                startService = {
                    runCatching {
                        ContextCompat.startForegroundService(
                            appContext,
                            Intent(appContext, WearTimerService::class.java).setAction(ACTION_SYNC_REMOTE)
                        )
                    }.isSuccess
                },
                armPhaseEndAlarm = { triggerAt -> WearPhaseEndAlarm.schedule(appContext, triggerAt) },
                clock = System::currentTimeMillis
            )
        }

        /**
         * Adopts whatever the Data Layer currently holds, if it is newer than this process's state.
         * The listener only hears about changes while this process exists to receive them, and the
         * engine is lost with the process, so the UI calls this when it comes to the foreground.
         */
        suspend fun refreshFromDataLayer(context: Context): BackgroundSyncAction? {
            val stored = runCatching {
                WearPlayServicesSyncTransport(context.applicationContext).getCurrentRemoteState()
            }.getOrNull() ?: return null
            return backgroundSyncHandler(context).handle(stored)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var inForeground = false
    private lateinit var syncCoordinator: SyncCoordinator
    private var syncJob: Job? = null

    // Start commands received but not yet handled (each waits for the cold-start seed first).
    private var pendingCommands = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceAlive = true
        createNotificationChannels()
        val transport = WearPlayServicesSyncTransport(applicationContext)
        syncCoordinator = SyncCoordinator(
            transport = transport,
            scope = serviceScope,
            ledger = syncLedger,
            onRemoteStateIsNewer = ::applyRemoteState
        )
        syncJob = syncCoordinator.start()
    }

    private fun applyRemoteState(payload: SyncPayload) {
        adoptRemoteIntoState(payload)
        onRemoteStateAdopted()
    }

    /** Brings the ticker, phase-end alarm, notification and the service itself in line with [_state]. */
    private fun onRemoteStateAdopted() {
        val state = _state.value
        when (serviceLifecycleAfterAdoption(state.isRunning, pendingCommands)) {
            ServiceLifecycle.RUN_IN_FOREGROUND -> {
                startTicker()
                syncPhaseEndAlarm(state)
                if (inForeground) {
                    updateOngoingNotification(state)
                } else {
                    inForeground = runCatching {
                        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
                    }.isSuccess
                }
            }
            ServiceLifecycle.DEFER_TO_PENDING_COMMAND -> {
                stopTicker()
                syncPhaseEndAlarm(state)
                updateOngoingNotification(state)
            }
            ServiceLifecycle.STOP -> stopTimerService()
        }
    }

    /**
     * Leaves the foreground, removing the ongoing notification, and stops. From here on the
     * background listener handles incoming payloads, so this instance stops consuming them.
     */
    private fun stopTimerService() {
        stopTicker()
        syncPhaseEndAlarm(_state.value)
        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        serviceAlive = false
        syncJob?.cancel()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // User commands and ACTION_SYNC_REMOTE arrive via startForegroundService and must enter the
        // foreground. The phase-end alarm uses plain startService while we are already in the
        // foreground, so it only needs this if the service instance was recreated in the meantime.
        when (foregroundEntryFor(intent?.action)) {
            ForegroundEntry.ALWAYS -> {
                startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
                inForeground = true
            }
            ForegroundEntry.IF_NOT_ALREADY -> if (!inForeground) {
                inForeground = runCatching {
                    startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
                }.isSuccess
            }
        }

        // On a freshly-created service, the engine still holds only its hardcoded defaults until
        // the coordinator's cold-start seed resolves; acting on this action before that happens
        // would publish those defaults with a fresh timestamp and win a conflict it shouldn't. On
        // a warm service this has already resolved and returns immediately.
        // Until it is handled, a remote stop arriving through the seed leaves the decision to it.
        pendingCommands++
        serviceScope.launch {
            try {
                syncCoordinator.awaitInitialSync()
            } finally {
                pendingCommands--
            }
            handleAction(intent)
        }
        return START_NOT_STICKY
    }

    private fun handleAction(intent: Intent?) {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                syncPhaseEndAlarm(_state.value)
                onLocalStateChanged(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                syncPhaseEndAlarm(_state.value)
                updateOngoingNotification(_state.value)
                onLocalStateChanged(_state.value, now)
            }
            ACTION_PHASE_END_ALARM -> {
                try {
                    processTick()
                    if (_state.value.isRunning) startTicker()
                } finally {
                    WearPhaseEndAlarmReceiver.releaseWakeLock()
                }
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                onLocalStateChanged(_state.value, now)
                stopTimerService()
            }
            // The state was already adopted (by the background handler, or by this instance's
            // seed); only the service's own side of it is left.
            ACTION_SYNC_REMOTE -> onRemoteStateAdopted()
        }
    }

    private fun onLocalStateChanged(state: TimerState, actionAtMillis: Long) {
        syncCoordinator.publishLocalState(
            SyncPayload(
                phase = state.phase,
                isRunning = state.isRunning,
                remainingMillis = state.remainingMillis,
                totalMillis = state.totalMillis,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                lastActionAtMillis = actionAtMillis,
                longBreakMinutes = longBreakMinutes,
                pomodorosUntilLongBreak = _pomodorosUntilLongBreak.value,
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = _autoStartBreak.value,
                autoStartFocus = _autoStartFocus.value,
                dailyGoalEnabled = _dailyGoalEnabled.value,
                dailyGoalTarget = _dailyGoalTarget.value,
                dailyGoalCount = dailyGoalProgress.count,
                dailyGoalDate = dailyGoalProgress.date,
                notificationsEnabled = notificationsEnabled,
                vibrationEnabled = vibrationEnabled,
                useAlarmSound = useAlarmSound,
                colorTheme = _colorTheme.value
            )
        )
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        val toggleAction = if (state.isRunning) ACTION_PAUSE else ACTION_START
        val toggleIcon = if (state.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .addAction(toggleIcon, NotificationContent.toggleActionLabel(state.isRunning), actionIntent(toggleAction))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "리셋", actionIntent(ACTION_RESET))

        val status = Status.Builder()
            .addTemplate("${NotificationContent.ongoingTitle(state.phase)} · ${NotificationContent.ongoingText(state.remainingMillis)}")
            .build()
        OngoingActivity.Builder(applicationContext, ONGOING_NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_popup_reminder)
            .setTouchIntent(contentIntent())
            .setStatus(status)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                processTick()
                if (!_state.value.isRunning) {
                    tickerJob = null
                    return@launch
                }
            }
        }
    }

    /**
     * Advances the engine to now and reacts to any phase boundaries crossed since the last tick. Runs
     * from both the 1-second ticker and the phase-end alarm (both on the main thread), and compares
     * against [_state] so whichever gets there first handles a boundary exactly once.
     */
    private fun processTick() {
        val previous = _state.value
        val newState = engine.tick(System.currentTimeMillis())
        _state.value = newState
        val transition = TimerTransition.between(previous, newState)
        if (transition.crossedBoundary) {
            onSessionEnded(newState)
            syncPhaseEndAlarm(newState)
            if (transition.focusSessionsCompleted > 0) {
                val today = LocalDate.now().toString()
                val before = DailyGoalTracker.forToday(dailyGoalProgress, today)
                val updated = DailyGoalTracker.recordCompletions(
                    dailyGoalProgress, today, transition.focusSessionsCompleted
                )
                dailyGoalProgress = updated
                if (_dailyGoalEnabled.value &&
                    DailyGoalTracker.justReachedGoal(before.count, updated.count, _dailyGoalTarget.value)
                ) {
                    showDailyGoalReachedNotification(_dailyGoalTarget.value)
                }
            }
            onLocalStateChanged(newState, System.currentTimeMillis())
        }
        updateOngoingNotification(newState)
    }

    private fun syncPhaseEndAlarm(state: TimerState) {
        val triggerAt = phaseEndAlarmAtMillis(state, System.currentTimeMillis())
        if (triggerAt != null) WearPhaseEndAlarm.schedule(this, triggerAt) else WearPhaseEndAlarm.cancel(this)
    }

    private fun onSessionEnded(newState: TimerState) {
        if (!notificationsEnabled) return
        val notification = NotificationCompat.Builder(this, sessionEndChannelId())
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newState.phase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    private fun showDailyGoalReachedNotification(target: Int) {
        if (!notificationsEnabled) return
        val notification = NotificationCompat.Builder(this, sessionEndChannelId())
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.dailyGoalReachedTitle(target))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(DAILY_GOAL_NOTIFICATION_ID, notification)
    }

    private fun sessionEndChannelId(): String =
        NotificationChannels.sessionEndChannelId(useAlarmSound, vibrationEnabled)

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pomodoro Timer", NotificationManager.IMPORTANCE_LOW)
            )
            for (useAlarm in listOf(false, true)) {
                for (vibrate in listOf(false, true)) {
                    val channelId = NotificationChannels.sessionEndChannelId(useAlarm, vibrate)
                    val channel = NotificationChannel(channelId, "세션 종료 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                        enableVibration(vibrate)
                        setSound(
                            RingtoneManager.getDefaultUri(
                                if (useAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                            ),
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .build()
                        )
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, WearTimerService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(this, 0, intent, flags)
    }

    override fun onDestroy() {
        serviceAlive = false
        stopTicker()
        WearPhaseEndAlarm.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
