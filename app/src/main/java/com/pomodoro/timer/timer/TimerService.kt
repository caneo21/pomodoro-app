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
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.sync.PlayServicesSyncTransport
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

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.pomodoro.timer.action.START"
        const val ACTION_PAUSE = "com.pomodoro.timer.action.PAUSE"
        const val ACTION_RESET = "com.pomodoro.timer.action.RESET"
        const val ACTION_UPDATE_DURATIONS = "com.pomodoro.timer.action.UPDATE_DURATIONS"
        const val EXTRA_FOCUS_MINUTES = "extra_focus_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"
        const val EXTRA_LONG_BREAK_MINUTES = "extra_long_break_minutes"
        const val EXTRA_POMODOROS_UNTIL_LONG_BREAK = "extra_pomodoros_until_long_break"

        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val SESSION_END_CHANNEL_ID = "pomodoro_session_end_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
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
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
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
                completedPomodoros = state.completedPomodoros
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every command arrives via startForegroundService, which obliges us to enter the
        // foreground within ~5s regardless of which action it was.
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))

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

    private fun handleAction(intent: Intent?) {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                publishSyncState(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                updateOngoingNotification(_state.value)
                publishSyncState(_state.value, now)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                publishSyncState(_state.value, now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
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
                engine.updateDurations(
                    focusMinutes * 60_000L,
                    breakMinutes * 60_000L,
                    longBreakMinutes * 60_000L,
                    pomodorosUntilLongBreak
                )
                _state.value = engine.currentState(now)
                publishSyncState(_state.value, now)
                if (!_state.value.isRunning) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
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
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
                if (newState.phase != previousPhase) {
                    showSessionEndNotification(newState.phase)
                    publishSyncState(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
            }
        }
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
            // Channel importance overrides setPriority/setDefaults on API 26+, so session-end
            // alerts need their own IMPORTANCE_HIGH channel to make any sound at all.
            val sessionEndChannel = NotificationChannel(
                SESSION_END_CHANNEL_ID,
                "세션 종료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(sessionEndChannel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun pauseActionIntent(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(ACTION_PAUSE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(this, 0, intent, flags)
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
        if (state.isRunning) {
            builder.addAction(android.R.drawable.ic_media_pause, "일시정지", pauseActionIntent())
        }
        return builder.build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun showSessionEndNotification(newPhase: TimerPhase) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newPhase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopTicker()
        serviceScope.cancel()
        super.onDestroy()
    }
}
