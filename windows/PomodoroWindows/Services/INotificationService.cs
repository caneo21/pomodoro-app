using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public interface INotificationService
{
    void NotifySessionEnd(TimerPhase newPhase);
    void NotifyDailyGoalReached(int target);
}
