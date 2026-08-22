namespace PomodoroWindows.Core;

public static class NotificationContent
{
    public static string OngoingTitle(TimerPhase phase) => phase switch
    {
        TimerPhase.Focus => "집중 중",
        TimerPhase.ShortBreak => "휴식 중",
        TimerPhase.LongBreak => "긴 휴식 중",
        _ => throw new ArgumentOutOfRangeException(nameof(phase))
    };

    public static string OngoingText(long remainingMillis)
    {
        var totalSeconds = Math.Max(remainingMillis / 1000, 0);
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return $"{minutes:D2}:{seconds:D2} 남음";
    }

    public static string SessionEndTitle(TimerPhase newPhase) => newPhase switch
    {
        TimerPhase.ShortBreak => "휴식 시간입니다!",
        TimerPhase.LongBreak => "긴 휴식 시간입니다!",
        TimerPhase.Focus => "다시 집중할 시간입니다!",
        _ => throw new ArgumentOutOfRangeException(nameof(newPhase))
    };

    public static string DailyGoalReachedTitle(int target) => $"오늘의 목표 {target}개를 달성했어요!";

    public static string ToggleActionLabel(bool isRunning) => isRunning ? "일시정지" : "재생";
}
