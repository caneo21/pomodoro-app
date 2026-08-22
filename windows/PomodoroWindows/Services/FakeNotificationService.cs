using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public sealed class FakeNotificationService : INotificationService
{
    public List<TimerPhase> SessionEndCalls { get; } = new();
    public List<int> DailyGoalReachedCalls { get; } = new();

    public void NotifySessionEnd(TimerPhase newPhase) => SessionEndCalls.Add(newPhase);
    public void NotifyDailyGoalReached(int target) => DailyGoalReachedCalls.Add(target);
}
