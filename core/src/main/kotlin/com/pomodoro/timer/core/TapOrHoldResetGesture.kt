package com.pomodoro.timer.core

/**
 * Distinguishes a quick tap (fires [onTap]) from holding past [holdThresholdMillis]
 * (fires [onReset] exactly once, and suppresses the tap that would otherwise fire on release).
 */
class TapOrHoldResetGesture(
    private val holdThresholdMillis: Long,
    private val onTap: () -> Unit,
    private val onReset: () -> Unit
) {
    private var resetFired = false

    fun onPressStart() {
        resetFired = false
    }

    fun onHoldElapsed(heldMillis: Long) {
        if (!resetFired && heldMillis >= holdThresholdMillis) {
            resetFired = true
            onReset()
        }
    }

    fun onRelease() {
        if (!resetFired) onTap()
    }
}
