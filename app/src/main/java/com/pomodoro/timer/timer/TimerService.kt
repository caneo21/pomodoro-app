package com.pomodoro.timer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.timer.MainActivity
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
import com.pomodoro.timer.core.NotificationChannels
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.core.TimerTransition
import com.pomodoro.timer.core.phaseEndAlarmAtMillis
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.sync.PlayServicesSyncTransport
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.pomodoro.timer.action.START"
        const val ACTION_PAUSE = "com.pomodoro.timer.action.PAUSE"
        const val ACTION_RESET = "com.pomodoro.timer.action.RESET"
        const val ACTION_UPDATE_DURATIONS = "com.pomodoro.timer.action.UPDATE_DURATIONS"
        const val ACTION_PHASE_END_ALARM = "com.pomodoro.timer.action.PHASE_END_ALARM"
        const val EXTRA_FOCUS_MINUTES = "extra_focus_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"
        const val EXTRA_LONG_BREAK_MINUTES = "extra_long_break_minutes"
        const val EXTRA_POMODOROS_UNTIL_LONG_BREAK = "extra_pomodoros_until_long_break"
        const val EXTRA_AUTO_START_BREAK = "extra_auto_start_break"
        const val EXTRA_AUTO_START_FOCUS = "extra_auto_start_focus"
        const val EXTRA_DAILY_GOAL_ENABLED = "extra_daily_goal_enabled"
        const val EXTRA_DAILY_GOAL_TARGET = "extra_daily_goal_target"
        const val EXTRA_NOTIFICATIONS_ENABLED = "extra_notifications_enabled"
        const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
        const val EXTRA_USE_ALARM_SOUND = "extra_use_alarm_sound"
        const val EXTRA_COLOR_THEME = "extra_color_theme"

        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val DAILY_GOAL_NOTIFICATION_ID = 3
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
        private var pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        private var autoStartBreak: Boolean = false
        private var autoStartFocus: Boolean = false
        private var dailyGoalEnabled: Boolean = false
        private var dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
        private var dailyGoalProgress: DailyGoalProgress = DailyGoalProgress("", 0)
        private var notificationsEnabled: Boolean = true
        private var vibrationEnabled: Boolean = true
        private var useAlarmSound: Boolean = false
        private var colorTheme: ColorTheme = ColorTheme.DEFAULT
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var inForeground = false
    private lateinit var syncCoordinator: SyncCoordinator
    private lateinit var settingsRepository: TimerSettingsRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        settingsRepository = TimerSettingsRepository(applicationContext.dataStore)
        serviceScope.launch { dailyGoalProgress = settingsRepository.dailyGoalProgress.first() }
        val transport = PlayServicesSyncTransport(applicationContext)
        syncCoordinator = SyncCoordinator(
            transport = transport,
            scope = serviceScope,
            onRemoteStateIsNewer = ::applyRemoteState
        )
        syncCoordinator.start()
    }

    private fun applyRemoteState(payload: SyncPayload) {
        focusMinutes = payload.focusMinutes
        breakMinutes = payload.breakMinutes
        longBreakMinutes = payload.longBreakMinutes
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak
        autoStartBreak = payload.autoStartBreak
        autoStartFocus = payload.autoStartFocus
        dailyGoalEnabled = payload.dailyGoalEnabled
        dailyGoalTarget = payload.dailyGoalTarget
        notificationsEnabled = payload.notificationsEnabled
        vibrationEnabled = payload.vibrationEnabled
        useAlarmSound = payload.useAlarmSound
        colorTheme = payload.colorTheme
        if (payload.dailyGoalDate.isNotEmpty()) {
            dailyGoalProgress = DailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount)
            serviceScope.launch { settingsRepository.setDailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount) }
        }
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
        syncPhaseEndAlarm(_state.value)
        updateOngoingNotification(_state.value)
    }

    private fun publishSyncState(state: TimerState, actionAtMillis: Long) {
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
                pomodorosUntilLongBreak = pomodorosUntilLongBreak,
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = autoStartBreak,
                autoStartFocus = autoStartFocus,
                dailyGoalEnabled = dailyGoalEnabled,
                dailyGoalTarget = dailyGoalTarget,
                dailyGoalCount = dailyGoalProgress.count,
                dailyGoalDate = dailyGoalProgress.date,
                notificationsEnabled = notificationsEnabled,
                vibrationEnabled = vibrationEnabled,
                useAlarmSound = useAlarmSound,
                colorTheme = colorTheme
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every user command arrives via startForegroundService, which obliges us to enter the
        // foreground within ~5s regardless of which action it was. The phase-end alarm uses plain
        // startService while we are already in the foreground, so it only needs this if the service
        // instance was recreated in the meantime (exact alarms permit a background foreground start;
        // if that is refused the tick still runs, just without a foreground service).
        if (intent?.action != ACTION_PHASE_END_ALARM) {
            startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
            inForeground = true
        } else if (!inForeground) {
            inForeground = runCatching {
                startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
            }.isSuccess
        }

        // On a freshly-created service, the engine still holds only its hardcoded defaults until
        // the coordinator's cold-start seed resolves; acting on this action before that happens
        // would publish those defaults with a fresh timestamp and win a conflict it shouldn't. On
        // a warm service this has already resolved and returns immediately.
        serviceScope.launch {
            syncCoordinator.awaitInitialSync()
            handleAction(intent)
        }
        return START_NOT_STICKY
    }

    private suspend fun handleAction(intent: Intent?) {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                syncPhaseEndAlarm(_state.value)
                publishSyncState(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                syncPhaseEndAlarm(_state.value)
                updateOngoingNotification(_state.value)
                publishSyncState(_state.value, now)
            }
            ACTION_PHASE_END_ALARM -> {
                try {
                    processTick()
                    if (_state.value.isRunning) startTicker()
                } finally {
                    PhaseEndAlarmReceiver.releaseWakeLock()
                }
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                publishSyncState(_state.value, now)
                stopTicker()
                syncPhaseEndAlarm(_state.value)
                stopForeground(STOP_FOREGROUND_REMOVE)
                inForeground = false
                stopSelf()
            }
            ACTION_UPDATE_DURATIONS -> {
                focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, TimerDefaults.DEFAULT_FOCUS_MINUTES)
                breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, TimerDefaults.DEFAULT_BREAK_MINUTES)
                longBreakMinutes = intent.getIntExtra(EXTRA_LONG_BREAK_MINUTES, TimerDefaults.DEFAULT_LONG_BREAK_MINUTES)
                pomodorosUntilLongBreak = intent.getIntExtra(
                    EXTRA_POMODOROS_UNTIL_LONG_BREAK,
                    TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
                )
                autoStartBreak = intent.getBooleanExtra(EXTRA_AUTO_START_BREAK, false)
                autoStartFocus = intent.getBooleanExtra(EXTRA_AUTO_START_FOCUS, false)
                dailyGoalEnabled = intent.getBooleanExtra(EXTRA_DAILY_GOAL_ENABLED, false)
                dailyGoalTarget = intent.getIntExtra(EXTRA_DAILY_GOAL_TARGET, TimerDefaults.DEFAULT_DAILY_GOAL_TARGET)
                notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, true)
                vibrationEnabled = intent.getBooleanExtra(EXTRA_VIBRATION_ENABLED, true)
                useAlarmSound = intent.getBooleanExtra(EXTRA_USE_ALARM_SOUND, false)
                colorTheme = ColorTheme.entries
                    .firstOrNull { it.name == intent.getStringExtra(EXTRA_COLOR_THEME) } ?: ColorTheme.DEFAULT
                engine.updateDurations(
                    focusMinutes * 60_000L,
                    breakMinutes * 60_000L,
                    longBreakMinutes * 60_000L,
                    pomodorosUntilLongBreak,
                    autoStartBreak,
                    autoStartFocus
                )
                _state.value = engine.currentState(now)
                publishSyncState(_state.value, now)
                if (!_state.value.isRunning) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    inForeground = false
                    stopSelf()
                } else {
                    updateOngoingNotification(_state.value)
                }
            }
        }
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
    private suspend fun processTick() {
        val previous = _state.value
        val newState = engine.tick(System.currentTimeMillis())
        _state.value = newState
        val transition = TimerTransition.between(previous, newState)
        if (transition.crossedBoundary) {
            showSessionEndNotification(newState.phase)
            syncPhaseEndAlarm(newState)
            if (transition.focusSessionsCompleted > 0) {
                val today = LocalDate.now().toString()
                val (before, updated) = settingsRepository.recordDailyGoalCompletion(
                    today, transition.focusSessionsCompleted
                )
                dailyGoalProgress = updated
                if (dailyGoalEnabled && DailyGoalTracker.justReachedGoal(before.count, updated.count, dailyGoalTarget)) {
                    showDailyGoalReachedNotification(dailyGoalTarget)
                }
            }
            publishSyncState(newState, System.currentTimeMillis())
        }
        updateOngoingNotification(newState)
    }

    private fun syncPhaseEndAlarm(state: TimerState) {
        val triggerAt = phaseEndAlarmAtMillis(state, System.currentTimeMillis())
        if (triggerAt != null) PhaseEndAlarm.schedule(this, triggerAt) else PhaseEndAlarm.cancel(this)
    }

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
            // Channel importance/sound/vibration are immutable once created on API 26+, so every
            // sound x vibration combination the user can pick in Settings gets its own pre-created
            // channel; showSessionEndNotification/showDailyGoalReachedNotification just select one.
            for (useAlarm in listOf(false, true)) {
                for (vibrate in listOf(false, true)) {
                    val channelId = NotificationChannels.sessionEndChannelId(useAlarm, vibrate)
                    val channelName = NotificationChannels.sessionEndChannelName(useAlarm, vibrate)
                    val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
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

    private fun sessionEndChannelId(): String =
        NotificationChannels.sessionEndChannelId(useAlarmSound, vibrationEnabled)

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(this, 0, intent, flags)
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
        return builder.build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun showSessionEndNotification(newPhase: TimerPhase) {
        if (!notificationsEnabled) return
        val notification = NotificationCompat.Builder(this, sessionEndChannelId())
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newPhase))
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

    override fun onDestroy() {
        stopTicker()
        PhaseEndAlarm.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
