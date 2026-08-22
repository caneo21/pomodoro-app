using CommunityToolkit.Mvvm.ComponentModel;
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;

namespace PomodoroWindows.ViewModels;

public sealed partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsStore _settingsStore;
    private readonly TimerViewModel _timerViewModel;

    [ObservableProperty] private int focusMinutes;
    [ObservableProperty] private int breakMinutes;
    [ObservableProperty] private int longBreakMinutes;
    [ObservableProperty] private int pomodorosUntilLongBreak;
    [ObservableProperty] private bool autoStartBreak;
    [ObservableProperty] private bool autoStartFocus;
    [ObservableProperty] private bool keepScreenOn;
    [ObservableProperty] private bool dailyGoalEnabled;
    [ObservableProperty] private int dailyGoalTarget;
    [ObservableProperty] private bool notificationsEnabled;
    [ObservableProperty] private bool useAlarmSound;
    [ObservableProperty] private ColorTheme colorTheme;

    public SettingsViewModel(ISettingsStore settingsStore, TimerViewModel timerViewModel)
    {
        _settingsStore = settingsStore;
        _timerViewModel = timerViewModel;

        var s = _settingsStore.Load();
        focusMinutes = s.FocusMinutes;
        breakMinutes = s.BreakMinutes;
        longBreakMinutes = s.LongBreakMinutes;
        pomodorosUntilLongBreak = s.PomodorosUntilLongBreak;
        autoStartBreak = s.AutoStartBreak;
        autoStartFocus = s.AutoStartFocus;
        keepScreenOn = s.KeepScreenOn;
        dailyGoalEnabled = s.DailyGoalEnabled;
        dailyGoalTarget = s.DailyGoalTarget;
        notificationsEnabled = s.NotificationsEnabled;
        useAlarmSound = s.UseAlarmSound;
        colorTheme = s.ColorTheme;
    }

    public void Save()
    {
        // Daily-goal progress is owned by TimerViewModel, not by this page's controls; re-read it
        // fresh so an unrelated settings change here doesn't clobber same-day progress with defaults.
        var current = _settingsStore.Load();
        var settings = new TimerSettings(
            FocusMinutes, BreakMinutes, LongBreakMinutes, PomodorosUntilLongBreak,
            AutoStartBreak, AutoStartFocus, KeepScreenOn, DailyGoalEnabled, DailyGoalTarget,
            NotificationsEnabled, UseAlarmSound, ColorTheme,
            current.DailyGoalDate, current.DailyGoalCount);
        _settingsStore.Save(settings);
        _timerViewModel.ApplySettings(settings);
    }
}
