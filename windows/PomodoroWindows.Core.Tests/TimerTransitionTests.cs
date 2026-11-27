namespace PomodoroWindows.Core.Tests;

public class TimerTransitionTests
{
    private static TimerState State(TimerPhase phase, long focusCompletions, int completedPomodoros = 0) =>
        new(phase, IsRunning: true, RemainingMillis: 1_000L, TotalMillis: 1_000L,
            CompletedPomodoros: completedPomodoros, TotalFocusCompletions: focusCompletions);

    [Fact]
    public void No_boundary_when_phase_and_focus_count_are_unchanged()
    {
        var t = TimerTransition.Between(State(TimerPhase.Focus, 3), State(TimerPhase.Focus, 3));

        Assert.False(t.CrossedBoundary);
        Assert.Equal(0, t.FocusSessionsCompleted);
    }

    [Fact]
    public void Leaving_a_long_break_is_a_boundary_with_no_focus_completion_even_though_the_cycle_count_resets()
    {
        var t = TimerTransition.Between(
            State(TimerPhase.LongBreak, 4, completedPomodoros: 4),
            State(TimerPhase.Focus, 4, completedPomodoros: 0));

        Assert.True(t.CrossedBoundary);
        Assert.Equal(0, t.FocusSessionsCompleted);
    }

    [Fact]
    public void Landing_back_in_the_same_phase_still_counts_the_focus_sessions_that_ended()
    {
        var t = TimerTransition.Between(State(TimerPhase.Focus, 3), State(TimerPhase.Focus, 4));

        Assert.True(t.CrossedBoundary);
        Assert.Equal(1, t.FocusSessionsCompleted);
    }

    [Fact]
    public void Several_focus_sessions_in_one_step_are_all_counted()
    {
        var t = TimerTransition.Between(State(TimerPhase.Focus, 3), State(TimerPhase.ShortBreak, 5));

        Assert.Equal(2, t.FocusSessionsCompleted);
    }
}
