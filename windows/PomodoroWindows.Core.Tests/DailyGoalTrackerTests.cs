namespace PomodoroWindows.Core.Tests;

public class DailyGoalTrackerTests
{
    [Fact]
    public void ForToday_returns_stored_progress_unchanged_when_dates_match()
    {
        var stored = new DailyGoalProgress("2026-08-19", 3);

        var result = DailyGoalTracker.ForToday(stored, today: "2026-08-19");

        Assert.Equal(stored, result);
    }

    [Fact]
    public void ForToday_zeroes_out_on_a_date_change()
    {
        var stored = new DailyGoalProgress("2026-08-18", 5);

        var result = DailyGoalTracker.ForToday(stored, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 0), result);
    }

    [Fact]
    public void ForToday_zeroes_out_when_stored_is_null()
    {
        var result = DailyGoalTracker.ForToday(null, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 0), result);
    }

    [Fact]
    public void RecordCompletion_increments_within_a_day()
    {
        var current = new DailyGoalProgress("2026-08-19", 3);

        var result = DailyGoalTracker.RecordCompletion(current, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 4), result);
    }

    [Fact]
    public void RecordCompletion_restarts_at_1_across_a_date_change()
    {
        var current = new DailyGoalProgress("2026-08-18", 7);

        var result = DailyGoalTracker.RecordCompletion(current, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 1), result);
    }

    [Fact]
    public void RecordCompletion_starts_at_1_when_current_is_null()
    {
        var result = DailyGoalTracker.RecordCompletion(null, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 1), result);
    }

    [Fact]
    public void JustReachedGoal_is_true_only_on_the_exact_crossing()
    {
        Assert.True(DailyGoalTracker.JustReachedGoal(previousCount: 7, count: 8, target: 8));
    }

    [Fact]
    public void JustReachedGoal_is_false_below_the_target()
    {
        Assert.False(DailyGoalTracker.JustReachedGoal(previousCount: 6, count: 7, target: 8));
    }

    [Fact]
    public void JustReachedGoal_is_false_once_already_past_the_target()
    {
        Assert.False(DailyGoalTracker.JustReachedGoal(previousCount: 8, count: 9, target: 8));
    }
}
