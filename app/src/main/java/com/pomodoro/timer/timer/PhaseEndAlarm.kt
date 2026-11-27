package com.pomodoro.timer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

/**
 * Wakes the device when the running phase ends. The service's 1-second ticker is a coroutine
 * `delay`, which stops advancing while the CPU is asleep (a foreground service does not keep it
 * awake), so without this the session-end alert could arrive only when the user next woke the phone.
 */
internal object PhaseEndAlarm {
    private const val REQUEST_CODE = 100

    fun schedule(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Exact-alarm access revoked between the check and the call: fall back to inexact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PhaseEndAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * AlarmManager only holds a wake lock while [onReceive] runs, so this takes its own short one and
 * hands the work to [TimerService], which releases it via [releaseWakeLock] once the tick is handled.
 * The timeout bounds the lock even if the service never gets to run.
 */
class PhaseEndAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Nothing to do if the process was recreated (engine back at its idle defaults) or the timer
        // was stopped after this alarm was set.
        if (!TimerService.state.value.isRunning) return
        acquireWakeLock(context)
        try {
            // Plain startService: the alarm is only armed while TimerService is running in the
            // foreground, which keeps the app eligible to start services.
            context.startService(
                Intent(context, TimerService::class.java).setAction(TimerService.ACTION_PHASE_END_ALARM)
            )
        } catch (e: IllegalStateException) {
            releaseWakeLock()
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "pomodoro:phaseEnd"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 10_000L
        private var wakeLock: PowerManager.WakeLock? = null

        private fun acquireWakeLock(context: Context) {
            val lock = wakeLock ?: context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }

        fun releaseWakeLock() {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }
}
