package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
import com.pomodoro.timer.ui.theme.PomodoroLongBreakAccent

@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    DisposableEffect(uiState.keepScreenOn, uiState.isRunning) {
        view.keepScreenOn = uiState.keepScreenOn && uiState.isRunning
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = phaseLabel(uiState.phase),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${uiState.completedPomodoros}/${uiState.pomodorosUntilLongBreak}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("cycleProgressText")
        )
        Spacer(Modifier.height(16.dp))

        val progress = if (uiState.totalMillis > 0) {
            uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
        } else {
            0f
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(220.dp),
                color = progressColorFor(uiState.phase, MaterialTheme.colorScheme.primary, PomodoroLongBreakAccent),
                strokeWidth = 8.dp
            )
            Text(
                text = formatMillis(uiState.remainingMillis),
                style = MaterialTheme.typography.displayMedium
            )
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (uiState.isRunning) {
                Button(onClick = onPause, modifier = Modifier.testTag("pauseButton")) {
                    Text("일시정지")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.testTag("startButton")) {
                    Text("시작")
                }
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("resetButton")) {
                Text("리셋")
            }
        }

        Spacer(Modifier.height(16.dp))
        IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("settingsButton")) {
            Icon(Icons.Default.Settings, contentDescription = "설정")
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

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
