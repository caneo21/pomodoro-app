package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.ui.theme.paletteFor
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun SettingsScreen(
    focusMinutes: Int,
    breakMinutes: Int,
    longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onLongBreakMinutesChange: (Int) -> Unit = {},
    onPomodorosUntilLongBreakChange: (Int) -> Unit = {},
    autoStartBreak: Boolean = false,
    autoStartFocus: Boolean = false,
    onAutoStartBreakChange: (Boolean) -> Unit = {},
    onAutoStartFocusChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    dailyGoalEnabled: Boolean = false,
    dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET,
    onDailyGoalEnabledChange: (Boolean) -> Unit = {},
    onDailyGoalTargetChange: (Int) -> Unit = {},
    notificationsEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    useAlarmSound: Boolean = false,
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
    onVibrationEnabledChange: (Boolean) -> Unit = {},
    onUseAlarmSoundChange: (Boolean) -> Unit = {},
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    onColorThemeChange: (ColorTheme) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local drag state: committing every frame would round-trip through DataStore and fight the finger.
    var localFocusMinutes by remember(focusMinutes) { mutableFloatStateOf(focusMinutes.toFloat()) }
    var localBreakMinutes by remember(breakMinutes) { mutableFloatStateOf(breakMinutes.toFloat()) }
    var localLongBreakMinutes by remember(longBreakMinutes) { mutableFloatStateOf(longBreakMinutes.toFloat()) }
    var localPomodorosUntilLongBreak by remember(pomodorosUntilLongBreak) {
        mutableFloatStateOf(pomodorosUntilLongBreak.toFloat())
    }
    var localDailyGoalTarget by remember(dailyGoalTarget) { mutableFloatStateOf(dailyGoalTarget.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        SettingsCard {
            Text(
                "집중 시간: ${localFocusMinutes.roundToInt()}분",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localFocusMinutes,
                onValueChange = { localFocusMinutes = it },
                onValueChangeFinished = { onFocusMinutesChange(localFocusMinutes.roundToInt()) },
                valueRange = TimerDefaults.MIN_FOCUS_MINUTES.toFloat()..TimerDefaults.MAX_FOCUS_MINUTES.toFloat(),
                steps = TimerDefaults.MAX_FOCUS_MINUTES - TimerDefaults.MIN_FOCUS_MINUTES - 1,
                modifier = Modifier.testTag("focusSlider")
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "휴식 시간: ${localBreakMinutes.roundToInt()}분",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localBreakMinutes,
                onValueChange = { localBreakMinutes = it },
                onValueChangeFinished = { onBreakMinutesChange(localBreakMinutes.roundToInt()) },
                valueRange = TimerDefaults.MIN_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_BREAK_MINUTES.toFloat(),
                steps = TimerDefaults.MAX_BREAK_MINUTES - TimerDefaults.MIN_BREAK_MINUTES - 1,
                modifier = Modifier.testTag("breakSlider")
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "긴 휴식 시간: ${localLongBreakMinutes.roundToInt()}분",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localLongBreakMinutes,
                onValueChange = { localLongBreakMinutes = it },
                onValueChangeFinished = { onLongBreakMinutesChange(localLongBreakMinutes.roundToInt()) },
                valueRange = TimerDefaults.MIN_LONG_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_LONG_BREAK_MINUTES.toFloat(),
                steps = TimerDefaults.MAX_LONG_BREAK_MINUTES - TimerDefaults.MIN_LONG_BREAK_MINUTES - 1,
                modifier = Modifier.testTag("longBreakSlider")
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "긴 휴식까지 뽀모도로 횟수: ${localPomodorosUntilLongBreak.roundToInt()}회",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localPomodorosUntilLongBreak,
                onValueChange = { localPomodorosUntilLongBreak = it },
                onValueChangeFinished = { onPomodorosUntilLongBreakChange(localPomodorosUntilLongBreak.roundToInt()) },
                valueRange = TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK.toFloat()..TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK.toFloat(),
                steps = TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK - TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK - 1,
                modifier = Modifier.testTag("pomodorosUntilLongBreakSlider")
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            SettingsSwitchRow("자동으로 휴식 시작", "autoStartBreakLabel", "autoStartBreakSwitch", autoStartBreak, onAutoStartBreakChange)
            Spacer(Modifier.height(16.dp))
            SettingsSwitchRow("자동으로 다음 뽀모도로 시작", "autoStartFocusLabel", "autoStartFocusSwitch", autoStartFocus, onAutoStartFocusChange)
            Spacer(Modifier.height(16.dp))
            SettingsSwitchRow("화면 꺼짐 방지", "keepScreenOnLabel", "keepScreenOnSwitch", keepScreenOn, onKeepScreenOnChange)
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            SettingsSwitchRow("일일 목표 사용", "dailyGoalEnabledLabel", "dailyGoalEnabledSwitch", dailyGoalEnabled, onDailyGoalEnabledChange)
            Spacer(Modifier.height(16.dp))
            Text(
                "일일 목표: ${localDailyGoalTarget.roundToInt()}개",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localDailyGoalTarget,
                onValueChange = { localDailyGoalTarget = it },
                onValueChangeFinished = { onDailyGoalTargetChange(localDailyGoalTarget.roundToInt()) },
                valueRange = TimerDefaults.MIN_DAILY_GOAL_TARGET.toFloat()..TimerDefaults.MAX_DAILY_GOAL_TARGET.toFloat(),
                steps = TimerDefaults.MAX_DAILY_GOAL_TARGET - TimerDefaults.MIN_DAILY_GOAL_TARGET - 1,
                modifier = Modifier.testTag("dailyGoalTargetSlider")
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            SettingsSwitchRow("알림 사용", "notificationsEnabledLabel", "notificationsEnabledSwitch", notificationsEnabled, onNotificationsEnabledChange)
            Spacer(Modifier.height(16.dp))
            SettingsSwitchRow("진동", "vibrationEnabledLabel", "vibrationEnabledSwitch", vibrationEnabled, onVibrationEnabledChange)
            Spacer(Modifier.height(16.dp))
            SettingsSwitchRow("알람음 사용 (끄면 알림음)", "useAlarmSoundLabel", "useAlarmSoundSwitch", useAlarmSound, onUseAlarmSoundChange)
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            Text("테마", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (theme in ColorTheme.entries) {
                    val swatchColor = paletteFor(theme, darkTheme = false).accent
                    val isSelected = theme == colorTheme
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = CircleShape
                            )
                            .clickable { onColorThemeChange(theme) }
                            .testTag("colorThemeSwatch_${theme.name}")
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onReplayOnboarding,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().testTag("replayOnboardingButton")
        ) {
            Text("앱 사용법 다시보기")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().testTag("backButton")
        ) {
            Text("뒤로")
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    labelTag: String,
    switchTag: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag(labelTag)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTag)
        )
    }
}
