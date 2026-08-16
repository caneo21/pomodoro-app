package com.pomodoro.timer.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.pomodoro.timer.core.TimerPhase

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

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (uiState.phase == TimerPhase.FOCUS) "집중" else "휴식",
            style = MaterialTheme.typography.title3
        )
        Spacer(Modifier.height(8.dp))

        val progress = if (uiState.totalMillis > 0) {
            uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
        } else {
            0f
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(120.dp)
            )
            Text(text = remainingText, style = MaterialTheme.typography.title2)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.isRunning) {
                Button(onClick = onPause, modifier = Modifier.testTag("pauseButton")) {
                    Icon(Icons.Default.Pause, contentDescription = "일시정지")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.testTag("startButton")) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "시작")
                }
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("resetButton")) {
                Icon(Icons.Default.Refresh, contentDescription = "리셋")
            }
        }
    }
}

fun formatWearMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
