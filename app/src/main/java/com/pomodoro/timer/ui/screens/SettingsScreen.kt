package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.timer.TimerDefaults
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    focusMinutes: Int,
    breakMinutes: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local drag state: committing every frame would round-trip through DataStore and fight the finger.
    var localFocusMinutes by remember(focusMinutes) { mutableFloatStateOf(focusMinutes.toFloat()) }
    var localBreakMinutes by remember(breakMinutes) { mutableFloatStateOf(breakMinutes.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("집중 시간: ${localFocusMinutes.roundToInt()}분")
        Slider(
            value = localFocusMinutes,
            onValueChange = { localFocusMinutes = it },
            onValueChangeFinished = { onFocusMinutesChange(localFocusMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_FOCUS_MINUTES.toFloat()..TimerDefaults.MAX_FOCUS_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_FOCUS_MINUTES - TimerDefaults.MIN_FOCUS_MINUTES - 1,
            modifier = Modifier.testTag("focusSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("휴식 시간: ${localBreakMinutes.roundToInt()}분")
        Slider(
            value = localBreakMinutes,
            onValueChange = { localBreakMinutes = it },
            onValueChangeFinished = { onBreakMinutesChange(localBreakMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_BREAK_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_BREAK_MINUTES - TimerDefaults.MIN_BREAK_MINUTES - 1,
            modifier = Modifier.testTag("breakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
