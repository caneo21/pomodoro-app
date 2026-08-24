using PomodoroWindows.Core;
using PomodoroWindows.Models;
using Windows.Storage;

namespace PomodoroWindows.Services;

public sealed class ApplicationDataSettingsStore : ISettingsStore
{
    private readonly ApplicationDataContainer _container = ApplicationData.Current.LocalSettings;

    public TimerSettings Load()
    {
        var defaults = new TimerSettings();
        return new TimerSettings(
            FocusMinutes: GetInt(nameof(TimerSettings.FocusMinutes), defaults.FocusMinutes),
            BreakMinutes: GetInt(nameof(TimerSettings.BreakMinutes), defaults.BreakMinutes),
            LongBreakMinutes: GetInt(nameof(TimerSettings.LongBreakMinutes), defaults.LongBreakMinutes),
            PomodorosUntilLongBreak: GetInt(nameof(TimerSettings.PomodorosUntilLongBreak), defaults.PomodorosUntilLongBreak),
            AutoStartBreak: GetBool(nameof(TimerSettings.AutoStartBreak), defaults.AutoStartBreak),
            AutoStartFocus: GetBool(nameof(TimerSettings.AutoStartFocus), defaults.AutoStartFocus),
            KeepScreenOn: GetBool(nameof(TimerSettings.KeepScreenOn), defaults.KeepScreenOn),
            ShowTaskbarBadge: GetBool(nameof(TimerSettings.ShowTaskbarBadge), defaults.ShowTaskbarBadge),
            DailyGoalEnabled: GetBool(nameof(TimerSettings.DailyGoalEnabled), defaults.DailyGoalEnabled),
            DailyGoalTarget: GetInt(nameof(TimerSettings.DailyGoalTarget), defaults.DailyGoalTarget),
            NotificationsEnabled: GetBool(nameof(TimerSettings.NotificationsEnabled), defaults.NotificationsEnabled),
            UseAlarmSound: GetBool(nameof(TimerSettings.UseAlarmSound), defaults.UseAlarmSound),
            ColorTheme: (ColorTheme)GetInt(nameof(TimerSettings.ColorTheme), (int)defaults.ColorTheme),
            DailyGoalDate: GetString(nameof(TimerSettings.DailyGoalDate), defaults.DailyGoalDate),
            DailyGoalCount: GetInt(nameof(TimerSettings.DailyGoalCount), defaults.DailyGoalCount));
    }

    public void Save(TimerSettings settings)
    {
        _container.Values[nameof(TimerSettings.FocusMinutes)] = settings.FocusMinutes;
        _container.Values[nameof(TimerSettings.BreakMinutes)] = settings.BreakMinutes;
        _container.Values[nameof(TimerSettings.LongBreakMinutes)] = settings.LongBreakMinutes;
        _container.Values[nameof(TimerSettings.PomodorosUntilLongBreak)] = settings.PomodorosUntilLongBreak;
        _container.Values[nameof(TimerSettings.AutoStartBreak)] = settings.AutoStartBreak;
        _container.Values[nameof(TimerSettings.AutoStartFocus)] = settings.AutoStartFocus;
        _container.Values[nameof(TimerSettings.KeepScreenOn)] = settings.KeepScreenOn;
        _container.Values[nameof(TimerSettings.ShowTaskbarBadge)] = settings.ShowTaskbarBadge;
        _container.Values[nameof(TimerSettings.DailyGoalEnabled)] = settings.DailyGoalEnabled;
        _container.Values[nameof(TimerSettings.DailyGoalTarget)] = settings.DailyGoalTarget;
        _container.Values[nameof(TimerSettings.NotificationsEnabled)] = settings.NotificationsEnabled;
        _container.Values[nameof(TimerSettings.UseAlarmSound)] = settings.UseAlarmSound;
        _container.Values[nameof(TimerSettings.ColorTheme)] = (int)settings.ColorTheme;
        _container.Values[nameof(TimerSettings.DailyGoalDate)] = settings.DailyGoalDate;
        _container.Values[nameof(TimerSettings.DailyGoalCount)] = settings.DailyGoalCount;
    }

    private int GetInt(string key, int fallback) =>
        _container.Values.TryGetValue(key, out var value) && value is int i ? i : fallback;

    private bool GetBool(string key, bool fallback) =>
        _container.Values.TryGetValue(key, out var value) && value is bool b ? b : fallback;

    private string GetString(string key, string fallback) =>
        _container.Values.TryGetValue(key, out var value) && value is string s ? s : fallback;
}
