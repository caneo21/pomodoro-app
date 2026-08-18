package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val PomodoroWearLongBreakAccent = Color(0xFF5C6BC0)

private val PomodoroWearColors = Colors(
    primary = Color(0xFFE85D4A),
    primaryVariant = Color(0xFFB6432F),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF)
)

@Composable
fun PomodoroWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = PomodoroWearColors, content = content)
}
