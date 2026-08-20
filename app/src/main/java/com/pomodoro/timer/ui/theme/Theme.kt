package com.pomodoro.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.pomodoro.timer.core.ColorTheme

@Composable
fun PomodoroTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val palette = paletteFor(colorTheme, darkTheme)
    val colors = if (darkTheme) {
        darkColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, background = palette.background)
    } else {
        lightColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, background = palette.background)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PomodoroTypography,
        content = content
    )
}
