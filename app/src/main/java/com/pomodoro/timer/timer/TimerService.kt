package com.pomodoro.timer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.timer.MainActivity
import com.pomodoro.timer.settings.TimerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerSettingsRepository.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerSettingsRepository.DEFAULT_BREAK_MINUTES * 60_000L

        private val engine = TimerEngine(defaultFocusMillis, defaultBreakMillis)

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                val previousPhase = _state.value.phase
                _state.value = engine.start(now)
                startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
                startTicker()
                if (previousPhase != _state.value.phase) {
                    // no-op: start() never changes phase, kept for symmetry with tick()
                }
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                updateOngoingNotification(_state.value)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            ACTION_UPDATE_DURATIONS -> {
                val focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, TimerSettingsRepository.DEFAULT_FOCUS_MINUTES)
                val breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, TimerSettingsRepository.DEFAULT_BREAK_MINUTES)
                engine.updateDurations(focusMinutes * 60_000L, breakMinutes * 60_000L)
                _state.value = engine.currentState(now)
            }
        }
        return START_STICKY
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
                }
                updateOngoingNotification(newState)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun showSessionEndNotification(newPhase: TimerPhase) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
        super.onDestroy()
    }
}
