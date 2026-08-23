package com.pomodoro.timer.wear.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.wear.R
import com.pomodoro.timer.wear.theme.WearLongBreakAccent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val RESET_HOLD_MILLIS = 3000L
private const val HOLD_VISUAL_HEAD_START = 0.06f

@OptIn(ExperimentalTextApi::class)
private val ManropeExtraBold = FontFamily(
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.W800,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

private fun wearTimerDigitFontStyle(base: TextStyle): TextStyle = base.copy(
    fontFamily = ManropeExtraBold,
    fontWeight = FontWeight.W800,
    fontFeatureSettings = "tnum"
)

@Composable
fun WearTimerScreen(
    uiState: WearTimerUiState,
    isAmbient: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingText = formatWearMillis(uiState.remainingMillis)

    if (isAmbient) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = remainingText,
                color = Color.White,
                style = wearTimerDigitFontStyle(LocalTextStyle.current)
            )
        }
        return
    }

    // CircularProgressIndicator observes `progress` via snapshotFlow, which only detects reads of
    // actual Compose Snapshot State — a plain data-class property (uiState.remainingMillis) read
    // inside the lambda is invisible to it and would freeze the indicator at its first value. This
    // MutableFloatState bridges the plain uiState into real snapshot state: writing it on every
    // recomposition, then having the lambda read `.floatValue`, is what makes snapshotFlow see it.
    val progressState = remember { mutableFloatStateOf(0f) }
    progressState.floatValue = if (uiState.totalMillis > 0) {
        uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
    } else {
        0f
    }

    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        WearLongBreakAccent
    )
    val animatedRingColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wearTimerRingColor"
    )

    var holdProgress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val gesture = remember {
        TapOrHoldResetGesture(
            holdThresholdMillis = RESET_HOLD_MILLIS,
            onTap = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (uiState.isRunning) onPause() else onStart()
            },
            onReset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onReset()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
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
                                    val linearRatio = (elapsed / RESET_HOLD_MILLIS.toFloat()).coerceIn(0f, 1f)
                                    // Start the visible fill at HOLD_VISUAL_HEAD_START instead of 0 so the
                                    // ring shows an immediate, unmistakable tick the instant the press is
                                    // recognized — a literal 0-start reads as a dead pause for the first
                                    // couple hundred ms, since the indicator's own spring animation also
                                    // ramps up slowly from rest.
                                    holdProgress = HOLD_VISUAL_HEAD_START + linearRatio * (1f - HOLD_VISUAL_HEAD_START)
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
            .testTag("timerTapArea"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = phaseLabel(uiState.phase), style = MaterialTheme.typography.titleMedium)

        val holdStrokeWidthPx = with(LocalDensity.current) {
            CircularProgressIndicatorDefaults.smallStrokeWidth.toPx()
        }

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progressState.floatValue },
                modifier = Modifier.size(156.dp),
                strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = animatedRingColor)
            )
            if (holdProgress > 0f) {
                // CircularProgressIndicator routes `progress` through an internal
                // snapshotFlow(...).collectLatest { animateTo(...) }: every new value
                // cancels the in-flight spring before it can advance a single frame. Since
                // holdProgress is already written every frame with correct timing here, that
                // extra animation layer only starves itself — it stays visually stuck for
                // most of the hold, then lurches forward when frame timing gives it a rare
                // gap to actually run. Drawing the arc directly makes the pixels track
                // holdProgress exactly, frame for frame.
                Canvas(
                    modifier = Modifier
                        .size(156.dp)
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
            Text(
                text = remainingText,
                style = wearTimerDigitFontStyle(MaterialTheme.typography.displaySmall)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${uiState.completedPomodoros}/${uiState.pomodorosUntilLongBreak}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("cycleProgressText")
            )
            if (!uiState.isRunning && holdProgress == 0f) {
                Text(
                    text = "탭하여 시작 · 길게 눌러 리셋",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("gestureHint")
                )
            }
        }
    }
}

internal fun progressColorFor(phase: TimerPhase, primary: Color, longBreakAccent: Color): Color =
    if (phase == TimerPhase.LONG_BREAK) longBreakAccent else primary

private fun phaseLabel(phase: TimerPhase): String = when (phase) {
    TimerPhase.FOCUS -> "집중"
    TimerPhase.SHORT_BREAK -> "휴식"
    TimerPhase.LONG_BREAK -> "긴 휴식"
}

fun formatWearMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
