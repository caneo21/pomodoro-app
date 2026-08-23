package com.pomodoro.timer.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.wear.theme.wearPaletteFor

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
            Text(text = remainingText, color = Color.White)
        }
        return
    }

    val progress = if (uiState.totalMillis > 0) {
        uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
    } else {
        0f
    }
    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        wearPaletteFor(uiState.colorTheme).longBreakAccent
    )

    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = phaseLabel(uiState.phase), style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${uiState.completedPomodoros}/${uiState.pomodorosUntilLongBreak}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("cycleProgressText")
            )
        }

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(130.dp),
                colors = ProgressIndicatorDefaults.colors(indicatorColor = ringColor)
            )
            Text(text = remainingText, style = MaterialTheme.typography.displaySmall)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onReset, modifier = Modifier.testTag("resetButton")) {
                Icon(Icons.Default.Refresh, contentDescription = "리셋")
            }

            if (uiState.isRunning) {
                FilledIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(IconButtonDefaults.LargeButtonSize).testTag("pauseButton")
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "일시정지",
                        modifier = Modifier.size(IconButtonDefaults.LargeIconSize)
                    )
                }
            } else {
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.size(IconButtonDefaults.LargeButtonSize).testTag("startButton")
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "시작",
                        modifier = Modifier.size(IconButtonDefaults.LargeIconSize)
                    )
                }
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
