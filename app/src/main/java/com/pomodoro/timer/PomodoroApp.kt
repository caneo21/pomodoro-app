package com.pomodoro.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.screens.SettingsScreen
import com.pomodoro.timer.ui.screens.TimerScreen

private object Routes {
    const val TIMER = "timer"
    const val SETTINGS = "settings"
}

@Composable
fun PomodoroApp(viewModel: TimerViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.TIMER) {
        composable(Routes.TIMER) {
            TimerScreen(
                uiState = uiState,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onReset = viewModel::reset,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                focusMinutes = uiState.focusMinutes,
                breakMinutes = uiState.breakMinutes,
                longBreakMinutes = uiState.longBreakMinutes,
                pomodorosUntilLongBreak = uiState.pomodorosUntilLongBreak,
                autoStartBreak = uiState.autoStartBreak,
                autoStartFocus = uiState.autoStartFocus,
                onFocusMinutesChange = viewModel::updateFocusMinutes,
                onBreakMinutesChange = viewModel::updateBreakMinutes,
                onLongBreakMinutesChange = viewModel::updateLongBreakMinutes,
                onPomodorosUntilLongBreakChange = viewModel::updatePomodorosUntilLongBreak,
                onAutoStartBreakChange = viewModel::updateAutoStartBreak,
                onAutoStartFocusChange = viewModel::updateAutoStartFocus,
                keepScreenOn = uiState.keepScreenOn,
                onKeepScreenOnChange = viewModel::updateKeepScreenOn,
                dailyGoalEnabled = uiState.dailyGoalEnabled,
                dailyGoalTarget = uiState.dailyGoalTarget,
                onDailyGoalEnabledChange = viewModel::updateDailyGoalEnabled,
                onDailyGoalTargetChange = viewModel::updateDailyGoalTarget,
                notificationsEnabled = uiState.notificationsEnabled,
                vibrationEnabled = uiState.vibrationEnabled,
                useAlarmSound = uiState.useAlarmSound,
                onNotificationsEnabledChange = viewModel::updateNotificationsEnabled,
                onVibrationEnabledChange = viewModel::updateVibrationEnabled,
                onUseAlarmSoundChange = viewModel::updateUseAlarmSound,
                colorTheme = uiState.colorTheme,
                onColorThemeChange = viewModel::updateColorTheme,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
