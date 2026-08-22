namespace PomodoroWindows.Core;

public sealed record DailyGoalProgress(string Date, int Count);

public static class DailyGoalTracker
{
    public static DailyGoalProgress ForToday(DailyGoalProgress? stored, string today) =>
        stored is not null && stored.Date == today ? stored : new DailyGoalProgress(today, 0);

    public static DailyGoalProgress RecordCompletion(DailyGoalProgress? current, string today)
    {
        var baseProgress = ForToday(current, today);
        return baseProgress with { Count = baseProgress.Count + 1 };
    }

    public static bool JustReachedGoal(int previousCount, int count, int target) =>
        previousCount < target && count >= target;
}
