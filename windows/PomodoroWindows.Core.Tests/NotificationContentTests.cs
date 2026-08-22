namespace PomodoroWindows.Core.Tests;

public class NotificationContentTests
{
    [Fact]
    public void Ongoing_title_reflects_phase()
    {
        Assert.Equal("집중 중", NotificationContent.OngoingTitle(TimerPhase.Focus));
        Assert.Equal("휴식 중", NotificationContent.OngoingTitle(TimerPhase.ShortBreak));
        Assert.Equal("긴 휴식 중", NotificationContent.OngoingTitle(TimerPhase.LongBreak));
    }

    [Fact]
    public void Ongoing_text_formats_remaining_time_as_mm_colon_ss()
    {
        Assert.Equal("12:34 남음", NotificationContent.OngoingText(754_000L));
        Assert.Equal("00:05 남음", NotificationContent.OngoingText(5_000L));
        Assert.Equal("00:00 남음", NotificationContent.OngoingText(0L));
    }

    [Fact]
    public void Session_end_title_reflects_the_phase_just_entered()
    {
        Assert.Equal("휴식 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.ShortBreak));
        Assert.Equal("긴 휴식 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.LongBreak));
        Assert.Equal("다시 집중할 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.Focus));
    }

    [Fact]
    public void Daily_goal_reached_title_includes_the_target_count()
    {
        Assert.Equal("오늘의 목표 8개를 달성했어요!", NotificationContent.DailyGoalReachedTitle(8));
        Assert.Equal("오늘의 목표 1개를 달성했어요!", NotificationContent.DailyGoalReachedTitle(1));
    }

    [Fact]
    public void Toggle_action_label_reflects_whether_the_timer_is_running()
    {
        Assert.Equal("일시정지", NotificationContent.ToggleActionLabel(isRunning: true));
        Assert.Equal("재생", NotificationContent.ToggleActionLabel(isRunning: false));
    }
}
