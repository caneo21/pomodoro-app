using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;

namespace PomodoroWindows.ViewModels;

public sealed partial class TimerViewModel : ObservableObject
{
    private readonly ISettingsStore _settingsStore;
    private readonly INotificationService _notificationService;
    private readonly TimerEngine _engine;

    private TimerSettings _settings;
    private DailyGoalProgress? _dailyGoalProgress;

    [ObservableProperty] private TimerPhase phase;
    [ObservableProperty] private bool isRunning;
    [ObservableProperty] private long remainingMillis;
    [ObservableProperty] private long totalMillis;
    [ObservableProperty] private int completedPomodoros;
    [ObservableProperty] private int dailyGoalCount;

    public TimerViewModel(ISettingsStore settingsStore, INotificationService notificationService)
    {
        _settingsStore = settingsStore;
        _notificationService = notificationService;
        _settings = _settingsStore.Load();
        _engine = new TimerEngine(
            _settings.FocusMinutes * 60_000L,
            _settings.BreakMinutes * 60_000L,
            _settings.LongBreakMinutes * 60_000L,
            _settings.PomodorosUntilLongBreak,
            _settings.AutoStartBreak,
            _settings.AutoStartFocus);
        if (!string.IsNullOrEmpty(_settings.DailyGoalDate))
        {
            _dailyGoalProgress = new DailyGoalProgress(_settings.DailyGoalDate, _settings.DailyGoalCount);
            DailyGoalCount = _settings.DailyGoalCount;
        }
        ApplyState(_engine.CurrentState(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
    }

    [RelayCommand]
    private void StartPause(DateTimeOffset now)
    {
        var nowMillis = now.ToUnixTimeMilliseconds();
        var next = IsRunning ? _engine.Pause(nowMillis) : _engine.Start(nowMillis);
        ApplyState(next);
    }

    [RelayCommand]
    private void Reset()
    {
        ApplyState(_engine.Reset(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
    }

    public void Tick(DateTimeOffset now)
    {
        var previousPhase = Phase;
        var previousCompleted = CompletedPomodoros;

        var next = _engine.Tick(now.ToUnixTimeMilliseconds());
        ApplyState(next);

        if (next.Phase != previousPhase && _settings.NotificationsEnabled)
        {
            _notificationService.NotifySessionEnd(next.Phase);
        }

        // Compare CompletedPomodoros rather than the phase, so a tick that silently crosses more than
        // one boundary in a single call (e.g. after a sleep/hibernate gap) still credits the completed
        // focus session instead of losing it because the phase happened to land back where it started.
        if (next.CompletedPomodoros != previousCompleted)
        {
            RecordDailyGoalCompletion();
        }
    }

    private void RecordDailyGoalCompletion()
    {
        var today = DateTimeOffset.Now.ToString("yyyy-MM-dd");
        var previous = DailyGoalTracker.ForToday(_dailyGoalProgress, today);
        var updated = DailyGoalTracker.RecordCompletion(_dailyGoalProgress, today);
        _dailyGoalProgress = updated;
        DailyGoalCount = updated.Count;
        _settings = _settings with { DailyGoalDate = updated.Date, DailyGoalCount = updated.Count };
        _settingsStore.Save(_settings);

        if (_settings.DailyGoalEnabled && _settings.NotificationsEnabled &&
            DailyGoalTracker.JustReachedGoal(previous.Count, updated.Count, _settings.DailyGoalTarget))
        {
            _notificationService.NotifyDailyGoalReached(_settings.DailyGoalTarget);
        }
    }

    public void ApplySettings(TimerSettings settings)
    {
        _settings = settings;
        _engine.UpdateDurations(
            settings.FocusMinutes * 60_000L,
            settings.BreakMinutes * 60_000L,
            settings.LongBreakMinutes * 60_000L,
            settings.PomodorosUntilLongBreak,
            settings.AutoStartBreak,
            settings.AutoStartFocus);
        ApplyState(_engine.CurrentState(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
    }

    private void ApplyState(TimerState state)
    {
        Phase = state.Phase;
        IsRunning = state.IsRunning;
        RemainingMillis = state.RemainingMillis;
        TotalMillis = state.TotalMillis;
        CompletedPomodoros = state.CompletedPomodoros;
    }
}
