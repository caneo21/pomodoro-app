package com.pomodoro.timer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pomodoro.timer.core.LatestRelease
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.TapOrHoldResetGesture
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
import com.pomodoro.timer.ui.theme.paletteFor
import com.pomodoro.timer.ui.theme.timerDigitFontStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val RESET_HOLD_MILLIS = 3000L

private val RingStrokeWidth = 5.dp
private val RingDiameter = 260.dp

@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    updateBanner: LatestRelease? = null,
    onUpdateDownload: () -> Unit = {},
    onUpdateDismiss: () -> Unit = {}
) {
    val view = LocalView.current
    DisposableEffect(uiState.keepScreenOn, uiState.isRunning) {
        view.keepScreenOn = uiState.keepScreenOn && uiState.isRunning
        onDispose { view.keepScreenOn = false }
    }

    val palette = paletteFor(uiState.colorTheme, isSystemInDarkTheme())
    val ringColor = progressColorFor(uiState.phase, MaterialTheme.colorScheme.primary, palette.longBreakAccent)
    val animatedRingColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "timerRingColor"
    )

    var holdProgress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val currentIsRunning = rememberUpdatedState(uiState.isRunning)
    val currentOnStart = rememberUpdatedState(onStart)
    val currentOnPause = rememberUpdatedState(onPause)
    val currentOnReset = rememberUpdatedState(onReset)
    val gesture = remember {
        TapOrHoldResetGesture(
            holdThresholdMillis = RESET_HOLD_MILLIS,
            onTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (currentIsRunning.value) currentOnPause.value() else currentOnStart.value()
            },
            onReset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnReset.value()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Spacer(Modifier.fillMaxWidth().weight(1f))
            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("settingsButton")) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Only while idle: a banner mid-session would pull attention away from focusing.
        if (updateBanner != null && !uiState.isRunning) {
            UpdateBanner(
                release = updateBanner,
                onDownload = onUpdateDownload,
                onDismiss = onUpdateDismiss,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = phaseLabel(uiState.phase),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            val progress = if (uiState.totalMillis > 0) {
                uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
            } else {
                0f
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                gesture.onPressStart()
                                coroutineScope {
                                    val tickJob = launch {
                                        var startFrameMillis = -1L
                                        while (isActive) {
                                            val elapsed = withFrameMillis { frameMillis ->
                                                if (startFrameMillis < 0) startFrameMillis = frameMillis
                                                frameMillis - startFrameMillis
                                            }
                                            holdProgress = (elapsed / RESET_HOLD_MILLIS.toFloat()).coerceIn(0f, 1f)
                                            gesture.onHoldElapsed(elapsed)
                                            if (elapsed >= RESET_HOLD_MILLIS) break
                                        }
                                    }
                                    tryAwaitRelease()
                                    tickJob.cancel()
                                }
                                gesture.onRelease()
                                holdProgress = 0f
                            }
                        )
                    }
                    .testTag("timerTapArea")
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(RingDiameter),
                    color = animatedRingColor,
                    strokeWidth = RingStrokeWidth,
                    strokeCap = StrokeCap.Round
                )
                if (holdProgress > 0f) {
                    val holdStrokeWidthPx = with(LocalDensity.current) { RingStrokeWidth.toPx() }
                    Canvas(
                        modifier = Modifier
                            .size(RingDiameter)
                            .testTag("resetHoldProgress")
                    ) {
                        val strokePx = holdStrokeWidthPx
                        val diameter = (size.minDimension - strokePx).coerceAtLeast(0f)
                        val topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * holdProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatMillis(uiState.remainingMillis),
                        style = timerDigitFontStyle(MaterialTheme.typography.displayMedium)
                    )
                    Spacer(Modifier.height(12.dp))
                    CycleProgressDots(
                        completed = uiState.completedPomodoros,
                        total = uiState.pomodorosUntilLongBreak,
                        dotColor = animatedRingColor
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            if (!uiState.isRunning && holdProgress == 0f) {
                Text(
                    text = "탭하여 시작 · 길게 눌러 리셋",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("gestureHint")
                )
            }
        }
    }
}

@Composable
private fun CycleProgressDots(
    completed: Int,
    total: Int,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val emptyDotColor = dotColor.copy(alpha = 0.3f)
    Row(
        modifier = modifier
            .semantics { contentDescription = "$completed/$total" }
            .testTag("cycleProgressIndicator"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dotFillStates(completed, total).forEach { filled ->
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = if (filled) dotColor else emptyDotColor)
            }
        }
    }
}

internal fun progressColorFor(phase: TimerPhase, primary: Color, longBreakAccent: Color): Color =
    if (phase == TimerPhase.LONG_BREAK) longBreakAccent else primary

internal fun dotFillStates(completed: Int, total: Int): List<Boolean> =
    List(total) { it < completed }

private fun phaseLabel(phase: TimerPhase): String = when (phase) {
    TimerPhase.FOCUS -> "집중"
    TimerPhase.SHORT_BREAK -> "휴식"
    TimerPhase.LONG_BREAK -> "긴 휴식"
}

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
