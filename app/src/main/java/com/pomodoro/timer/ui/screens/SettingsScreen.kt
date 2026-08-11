package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("집중 시간: ${focusMinutes}분")
        Slider(
            value = focusMinutes.toFloat(),
            onValueChange = { onFocusMinutesChange(it.roundToInt()) },
            valueRange = 5f..60f,
            steps = 54,
            modifier = Modifier.testTag("focusSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("휴식 시간: ${breakMinutes}분")
        Slider(
            value = breakMinutes.toFloat(),
            onValueChange = { onBreakMinutesChange(it.roundToInt()) },
            valueRange = 1f..30f,
            steps = 28,
            modifier = Modifier.testTag("breakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
