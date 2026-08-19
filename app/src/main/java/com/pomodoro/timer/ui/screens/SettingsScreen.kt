package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.TimerDefaults
import kotlin.math.roundToInt

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
        Text("긴 휴식 시간: ${localLongBreakMinutes.roundToInt()}분")
        Slider(
            value = localLongBreakMinutes,
            onValueChange = { localLongBreakMinutes = it },
            onValueChangeFinished = { onLongBreakMinutesChange(localLongBreakMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_LONG_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_LONG_BREAK_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_LONG_BREAK_MINUTES - TimerDefaults.MIN_LONG_BREAK_MINUTES - 1,
            modifier = Modifier.testTag("longBreakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("긴 휴식까지 뽀모도로 횟수: ${localPomodorosUntilLongBreak.roundToInt()}회")
        Slider(
            value = localPomodorosUntilLongBreak,
            onValueChange = { localPomodorosUntilLongBreak = it },
            onValueChangeFinished = { onPomodorosUntilLongBreakChange(localPomodorosUntilLongBreak.roundToInt()) },
            valueRange = TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK.toFloat()..TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK.toFloat(),
            steps = TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK - TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK - 1,
            modifier = Modifier.testTag("pomodorosUntilLongBreakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "자동으로 휴식 시작",
                modifier = Modifier.weight(1f).testTag("autoStartBreakLabel")
            )
            Switch(
                checked = autoStartBreak,
                onCheckedChange = onAutoStartBreakChange,
                modifier = Modifier.testTag("autoStartBreakSwitch")
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "자동으로 다음 뽀모도로 시작",
                modifier = Modifier.weight(1f).testTag("autoStartFocusLabel")
            )
            Switch(
                checked = autoStartFocus,
                onCheckedChange = onAutoStartFocusChange,
                modifier = Modifier.testTag("autoStartFocusSwitch")
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "화면 꺼짐 방지",
                modifier = Modifier.weight(1f).testTag("keepScreenOnLabel")
            )
            Switch(
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
                modifier = Modifier.testTag("keepScreenOnSwitch")
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "일일 목표 사용",
                modifier = Modifier.weight(1f).testTag("dailyGoalEnabledLabel")
            )
            Switch(
                checked = dailyGoalEnabled,
                onCheckedChange = onDailyGoalEnabledChange,
                modifier = Modifier.testTag("dailyGoalEnabledSwitch")
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("일일 목표: ${localDailyGoalTarget.roundToInt()}개")
        Slider(
            value = localDailyGoalTarget,
            onValueChange = { localDailyGoalTarget = it },
            onValueChangeFinished = { onDailyGoalTargetChange(localDailyGoalTarget.roundToInt()) },
            valueRange = TimerDefaults.MIN_DAILY_GOAL_TARGET.toFloat()..TimerDefaults.MAX_DAILY_GOAL_TARGET.toFloat(),
            steps = TimerDefaults.MAX_DAILY_GOAL_TARGET - TimerDefaults.MIN_DAILY_GOAL_TARGET - 1,
            modifier = Modifier.testTag("dailyGoalTargetSlider")
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
