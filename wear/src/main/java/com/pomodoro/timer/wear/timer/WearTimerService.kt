package com.pomodoro.timer.wear.timer

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
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.MainActivity
import com.pomodoro.timer.wear.sync.WearPlayServicesSyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

        const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "pomodoro_wear_timer_channel"
        private const val SESSION_END_CHANNEL_ID = "pomodoro_wear_session_end_channel"
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerDefaults.DEFAULT_BREAK_MINUTES * 60_000L

        val engine = TimerEngine(defaultFocusMillis, defaultBreakMillis)

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
    private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        syncCoordinator = SyncCoordinator(
            transport = WearPlayServicesSyncTransport(applicationContext),
            scope = serviceScope,
            onRemoteStateIsNewer = { payload ->
                focusMinutes = payload.focusMinutes
                breakMinutes = payload.breakMinutes
                _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
                updateOngoingNotification(_state.value)
            }
        )
        syncCoordinator.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))

        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                onLocalStateChanged(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                updateOngoingNotification(_state.value)
                onLocalStateChanged(_state.value, now)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                onLocalStateChanged(_state.value, now)
            }
        }
        return START_NOT_STICKY
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
                lastActionAtMillis = actionAtMillis
            )
        )
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())

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
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
                if (newState.phase != previousPhase) {
                    onSessionEnded(newState)
                }
                updateOngoingNotification(newState)
            }
        }
    }

    private fun onSessionEnded(newState: TimerState) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newState.phase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SESSION_END_NOTIFICATION_ID, notification)
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

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }
}
