package com.pomodoro.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.screens.SettingsScreen
import com.pomodoro.timer.ui.screens.TimerScreen
import com.pomodoro.timer.ui.screens.onboarding.OnboardingScreen

private object Routes {
    const val TIMER = "timer"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}

internal sealed interface OnboardingFinishAction {
    data object CompleteAndShowTimer : OnboardingFinishAction
    data object ReturnToSettings : OnboardingFinishAction
}

internal fun onboardingFinishAction(isFirstRun: Boolean): OnboardingFinishAction =
    if (isFirstRun) OnboardingFinishAction.CompleteAndShowTimer else OnboardingFinishAction.ReturnToSettings

@Composable
fun PomodoroApp(viewModel: TimerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // uiState starts as the TimerUiState() default before the first DataStore emission
    // arrives. Rendering nothing here (the caller's Surface already paints the themed
    // background) avoids a one-frame flash of the wrong start destination while the real
    // onboardingCompleted value is still unknown.
    if (!uiState.settingsLoaded) return

    // Captured once, when settingsLoaded first becomes true, so it reflects the real
    // persisted value and then stays fixed for the rest of this composable's lifetime --
    // it must not flip mid-session just because completeOnboarding() changes uiState.
    val startedWithoutOnboarding = remember { !uiState.onboardingCompleted }

    val navController = rememberNavController()
    val startDestination = if (startedWithoutOnboarding) Routes.ONBOARDING else Routes.TIMER

    NavHost(navController = navController, startDestination = startDestination) {
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
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    // First-run onboarding is the start destination and has nothing beneath it on
                    // the back stack; a replay launched from Settings always does. This must be read
                    // here, at click time -- startedWithoutOnboarding above answers a different
                    // question (which destination this session started on) and goes stale the moment
                    // completeOnboarding() flips the flag, so it must never be reused for this decision.
                    when (onboardingFinishAction(isFirstRun = navController.previousBackStackEntry == null)) {
                        OnboardingFinishAction.CompleteAndShowTimer -> {
                            viewModel.completeOnboarding()
                            navController.navigate(Routes.TIMER) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                        OnboardingFinishAction.ReturnToSettings -> navController.popBackStack()
                    }
                }
            )
        }
    }
}
