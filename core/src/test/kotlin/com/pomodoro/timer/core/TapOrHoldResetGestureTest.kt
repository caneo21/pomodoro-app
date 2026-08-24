package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TapOrHoldResetGestureTest {

    private var tapCount = 0
    private var resetCount = 0
    private val gesture = TapOrHoldResetGesture(
        holdThresholdMillis = 3000L,
        onTap = { tapCount++ },
        onReset = { resetCount++ }
    )

    @Before
    fun setUp() {
        tapCount = 0
        resetCount = 0
    }

    @Test
    fun `quick release before threshold fires tap, never reset`() {
        gesture.onPressStart()
        gesture.onHoldElapsed(120L)
        gesture.onRelease()

        assertEquals(1, tapCount)
        assertEquals(0, resetCount)
    }

    @Test
    fun `holding past the threshold fires reset`() {
        gesture.onPressStart()
        gesture.onHoldElapsed(1500L)
        gesture.onHoldElapsed(3000L)

        assertEquals(0, tapCount)
        assertEquals(1, resetCount)
    }

    @Test
    fun `releasing after reset already fired does not also fire tap`() {
        gesture.onPressStart()
        gesture.onHoldElapsed(3000L)
        gesture.onRelease()

        assertEquals(0, tapCount)
        assertEquals(1, resetCount)
    }

    @Test
    fun `reset fires only once even if hold keeps reporting past the threshold`() {
        gesture.onPressStart()
        gesture.onHoldElapsed(3000L)
        gesture.onHoldElapsed(3016L)
        gesture.onHoldElapsed(3032L)

        assertEquals(1, resetCount)
    }

    @Test
    fun `a new press after a completed reset can tap again`() {
        gesture.onPressStart()
        gesture.onHoldElapsed(3000L)
        gesture.onRelease()

        gesture.onPressStart()
        gesture.onHoldElapsed(80L)
        gesture.onRelease()

        assertEquals(1, tapCount)
        assertEquals(1, resetCount)
    }
}
