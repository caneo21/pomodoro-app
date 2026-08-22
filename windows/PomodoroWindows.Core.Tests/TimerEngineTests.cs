namespace PomodoroWindows.Core.Tests;

public class TimerEngineTests
{
    private const long FocusMillis = 25 * 60_000L;
    private const long BreakMillis = 5 * 60_000L;

    [Fact]
    public void Initial_state_is_idle_focus_with_full_duration()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        var state = engine.CurrentState(now: 0L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
        Assert.Equal(FocusMillis, state.TotalMillis);
    }

    [Fact]
    public void Start_begins_counting_down_from_now()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);

        var state = engine.Tick(now: 10_000L);

        Assert.True(state.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, state.RemainingMillis);
    }

    [Fact]
    public void Pause_freezes_remaining_time_and_resume_continues_from_same_point()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        var paused = engine.Pause(now: 10_000L);
        Assert.False(paused.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, paused.RemainingMillis);

        var stillPaused = engine.CurrentState(now: 60_000L);
        Assert.Equal(FocusMillis - 10_000L, stillPaused.RemainingMillis);

        var resumed = engine.Start(now: 60_000L);
        Assert.True(resumed.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, resumed.RemainingMillis);

        var laterTick = engine.Tick(now: 65_000L);
        Assert.Equal(FocusMillis - 15_000L, laterTick.RemainingMillis);
    }

    [Fact]
    public void Reset_returns_to_idle_focus_with_full_duration()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        engine.Tick(now: 10_000L);

        var state = engine.Reset(now: 10_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
    }

    [Fact]
    public void Tick_past_end_of_focus_phase_transitions_to_break_without_drift_when_autostart_is_enabled()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.True(state.IsRunning);
        Assert.Equal(BreakMillis - 5_000L, state.RemainingMillis);
        Assert.Equal(BreakMillis, state.TotalMillis);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_break_phase_transitions_back_to_focus_when_autostart_is_enabled()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Tick(now: FocusMillis + BreakMillis + 2_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.Equal(FocusMillis - 2_000L, state.RemainingMillis);
    }

    [Fact]
    public void Tick_while_paused_does_not_change_state()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        var before = engine.CurrentState(now: 0L);

        var after = engine.Tick(now: 999_000L);

        Assert.Equal(before, after);
    }

    [Fact]
    public void UpdateDurations_changes_the_duration_used_by_the_current_idle_phase()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.UpdateDurations(focusMillis: 10 * 60_000L, shortBreakMillis: 2 * 60_000L);

        var state = engine.CurrentState(now: 0L);

        Assert.Equal(10 * 60_000L, state.RemainingMillis);
        Assert.Equal(10 * 60_000L, state.TotalMillis);
    }

    [Fact]
    public void UpdateDurations_mid_session_does_not_change_the_running_phase_total_until_it_flips()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        engine.Tick(now: 60_000L);

        engine.UpdateDurations(focusMillis: 10 * 60_000L, shortBreakMillis: 2 * 60_000L);

        var midSession = engine.CurrentState(now: 60_000L);
        Assert.Equal(FocusMillis, midSession.TotalMillis);
        Assert.Equal(FocusMillis - 60_000L, midSession.RemainingMillis);
        Assert.True(midSession.RemainingMillis <= midSession.TotalMillis);

        var afterFlip = engine.Tick(now: FocusMillis);
        Assert.Equal(TimerPhase.ShortBreak, afterFlip.Phase);
        Assert.Equal(2 * 60_000L, afterFlip.TotalMillis);
    }

    [Fact]
    public void Completing_the_Nth_focus_session_enters_LongBreak_and_resets_the_count_when_it_ends()
    {
        var engine = new TimerEngine(
            FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 2,
            autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);

        engine.Tick(now: FocusMillis);
        engine.Tick(now: FocusMillis + BreakMillis);

        var afterSecondFocus = engine.Tick(now: FocusMillis + BreakMillis + FocusMillis);
        Assert.Equal(TimerPhase.LongBreak, afterSecondFocus.Phase);
        Assert.Equal(2, afterSecondFocus.CompletedPomodoros);
        Assert.Equal(900_000L, afterSecondFocus.TotalMillis);

        var afterLongBreak = engine.Tick(now: FocusMillis + BreakMillis + FocusMillis + 900_000L);
        Assert.Equal(TimerPhase.Focus, afterLongBreak.Phase);
        Assert.Equal(0, afterLongBreak.CompletedPomodoros);
    }

    [Fact]
    public void The_default_threshold_of_4_enters_LongBreak_on_the_4th_focus_completion_not_the_3rd()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);

        long now = 0L;
        for (var cycle = 0; cycle < 3; cycle++)
        {
            now += FocusMillis;
            var afterFocus = engine.Tick(now: now);
            Assert.Equal(TimerPhase.ShortBreak, afterFocus.Phase);
            Assert.Equal(cycle + 1, afterFocus.CompletedPomodoros);

            now += BreakMillis;
            var afterBreak = engine.Tick(now: now);
            Assert.Equal(TimerPhase.Focus, afterBreak.Phase);
        }

        now += FocusMillis;
        var afterFourthFocus = engine.Tick(now: now);
        Assert.Equal(TimerPhase.LongBreak, afterFourthFocus.Phase);
        Assert.Equal(4, afterFourthFocus.CompletedPomodoros);
        Assert.Equal(TimerDefaults.DefaultLongBreakMinutes * 60_000L, afterFourthFocus.TotalMillis);
    }

    [Fact]
    public void Reset_preserves_the_completed_pomodoros_count()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 4);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Reset(now: FocusMillis);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void UpdateDurations_floors_pomodorosUntilLongBreak_at_1()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 4);
        engine.UpdateDurations(FocusMillis, BreakMillis, 900_000L, pomodorosUntilLongBreak: 0);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis);

        Assert.Equal(TimerPhase.LongBreak, state.Phase);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_focus_phase_pauses_at_ShortBreak_when_autoStartBreak_is_false()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(BreakMillis, state.RemainingMillis);
        Assert.Equal(BreakMillis, state.TotalMillis);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_break_phase_pauses_at_Focus_when_autoStartFocus_is_false()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: false);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Tick(now: FocusMillis + BreakMillis + 2_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
        Assert.Equal(FocusMillis, state.TotalMillis);
    }

    [Fact]
    public void AutoStartFocus_not_autoStartBreak_governs_leaving_LongBreak()
    {
        var engine = new TimerEngine(
            FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 1,
            autoStartBreak: true, autoStartFocus: false);
        engine.Start(now: 0L);
        var enteredLongBreak = engine.Tick(now: FocusMillis);
        Assert.True(enteredLongBreak.IsRunning);

        var state = engine.Tick(now: FocusMillis + 900_000L + 1_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
    }

    [Fact]
    public void UpdateDurations_changes_which_future_transitions_auto_continue()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.UpdateDurations(FocusMillis, BreakMillis, autoStartBreak: true);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.True(state.IsRunning);
    }

    [Fact]
    public void UpdateDurations_mid_session_does_not_retroactively_auto_continue_the_in_flight_phase()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.Start(now: 0L);
        engine.Tick(now: 60_000L);

        engine.UpdateDurations(FocusMillis, BreakMillis, autoStartBreak: true);

        var stillMidFocus = engine.CurrentState(now: 120_000L);
        Assert.Equal(TimerPhase.Focus, stillMidFocus.Phase);
        Assert.True(stillMidFocus.IsRunning);

        var afterBoundary = engine.Tick(now: FocusMillis + 5_000L);
        Assert.Equal(TimerPhase.ShortBreak, afterBoundary.Phase);
        Assert.True(afterBoundary.IsRunning);
    }
}
