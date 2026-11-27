namespace PomodoroWindows.Core;

public sealed record DailyGoalProgress(string Date, int Count);

public static class DailyGoalTracker
{
    public static DailyGoalProgress ForToday(DailyGoalProgress? stored, string today) =>
        stored is not null && stored.Date == today ? stored : new DailyGoalProgress(today, 0);

    public static DailyGoalProgress RecordCompletion(DailyGoalProgress? current, string today) =>
        RecordCompletions(current, today, 1);

    public static DailyGoalProgress RecordCompletions(DailyGoalProgress? current, string today, int completions)
    {
        var baseProgress = ForToday(current, today);
        return baseProgress with { Count = baseProgress.Count + completions };
    }

    public static bool JustReachedGoal(int previousCount, int count, int target) =>
        previousCount < target && count >= target;
}
