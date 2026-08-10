package com.pomodoro.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PomodoroPrimary,
    onPrimary = PomodoroOnPrimary,
    background = PomodoroBackground
)

private val DarkColors = darkColorScheme(
    primary = PomodoroPrimary,
    onPrimary = PomodoroOnPrimary
)

@Composable
fun PomodoroTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = PomodoroTypography,
        content = content
    )
}
