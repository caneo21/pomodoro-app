using PomodoroWindows.Core;

namespace PomodoroWindows.Models;

public sealed record TimerSettings(
    int FocusMinutes = TimerDefaults.DefaultFocusMinutes,
    int BreakMinutes = TimerDefaults.DefaultBreakMinutes,
    int LongBreakMinutes = TimerDefaults.DefaultLongBreakMinutes,
    int PomodorosUntilLongBreak = TimerDefaults.DefaultPomodorosUntilLongBreak,
    bool AutoStartBreak = false,
    bool AutoStartFocus = false,
    bool KeepScreenOn = false,
    bool DailyGoalEnabled = false,
    int DailyGoalTarget = TimerDefaults.DefaultDailyGoalTarget,
    bool NotificationsEnabled = true,
    bool UseAlarmSound = false,
    ColorTheme ColorTheme = ColorTheme.Default);
