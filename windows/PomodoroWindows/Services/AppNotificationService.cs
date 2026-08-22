using Microsoft.Windows.AppNotifications;
using Microsoft.Windows.AppNotifications.Builder;
using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public sealed class AppNotificationService : INotificationService
{
    private readonly Func<bool> _useAlarmSound;

    public AppNotificationService(Func<bool> useAlarmSound)
    {
        _useAlarmSound = useAlarmSound;
    }

    public void NotifySessionEnd(TimerPhase newPhase)
    {
        Send(NotificationContent.SessionEndTitle(newPhase));
    }

    public void NotifyDailyGoalReached(int target)
    {
        Send(NotificationContent.DailyGoalReachedTitle(target));
    }

    private void Send(string title)
    {
        try
        {
            var builder = new AppNotificationBuilder().AddText(title);
            if (_useAlarmSound())
            {
                builder.SetAudioUri(new Uri("ms-winsoundevent:Notification.Looping.Alarm"));
            }
            AppNotificationManager.Default.Show(builder.BuildNotification());
        }
        catch (Exception)
        {
            // Toast notification display can fail if registration failed or the notification platform
            // is unavailable. Silently continue — the session/goal-reached event itself still occurred.
        }
    }
}
