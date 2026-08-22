using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Tests;

public class TimerViewModelTests
{
    private static TimerViewModel CreateViewModel(
        FakeSettingsStore? settingsStore = null,
        FakeNotificationService? notificationService = null,
        TimerSettings? initialSettings = null)
    {
        var store = settingsStore ?? new FakeSettingsStore();
        if (initialSettings is not null) store.Save(initialSettings);
        var notifications = notificationService ?? new FakeNotificationService();
        return new TimerViewModel(store, notifications);
    }

    [Fact]
    public void Initial_state_is_idle_focus_with_full_duration()
    {
        var vm = CreateViewModel(initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5));

        Assert.Equal(TimerPhase.Focus, vm.Phase);
        Assert.False(vm.IsRunning);
        Assert.Equal(25 * 60_000L, vm.RemainingMillis);
    }

    [Fact]
    public void StartPauseCommand_toggles_running_state()
    {
        var vm = CreateViewModel();
        var start = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.Tick(start);

        vm.StartPauseCommand.Execute(start);
        Assert.True(vm.IsRunning);

        vm.StartPauseCommand.Execute(start);
        Assert.False(vm.IsRunning);
    }

    [Fact]
    public void ResetCommand_returns_to_idle_focus()
    {
        var vm = CreateViewModel(initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);
        vm.Tick(DateTimeOffset.FromUnixTimeMilliseconds(10_000));

        vm.ResetCommand.Execute(null);

        Assert.Equal(TimerPhase.Focus, vm.Phase);
        Assert.False(vm.IsRunning);
        Assert.Equal(25 * 60_000L, vm.RemainingMillis);
    }

    [Fact]
    public void Tick_past_a_phase_boundary_with_notifications_enabled_fires_session_end_notification()
    {
        var notifications = new FakeNotificationService();
        var vm = CreateViewModel(
            notificationService: notifications,
            initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, NotificationsEnabled: true));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Equal(TimerPhase.ShortBreak, vm.Phase);
        Assert.Equal(new[] { TimerPhase.ShortBreak }, notifications.SessionEndCalls);
    }

    [Fact]
    public void Tick_past_a_phase_boundary_with_notifications_disabled_does_not_notify()
    {
        var notifications = new FakeNotificationService();
        var vm = CreateViewModel(
            notificationService: notifications,
            initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, NotificationsEnabled: false));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Empty(notifications.SessionEndCalls);
    }

    [Fact]
    public void Completing_a_focus_session_records_a_daily_goal_completion_and_notifies_on_the_exact_crossing()
    {
        var notifications = new FakeNotificationService();
        var settingsStore = new FakeSettingsStore();
        var vm = CreateViewModel(
            settingsStore, notifications,
            new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, DailyGoalEnabled: true, DailyGoalTarget: 1));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Equal(1, vm.DailyGoalCount);
        Assert.Equal(new[] { 1 }, notifications.DailyGoalReachedCalls);
    }
}
