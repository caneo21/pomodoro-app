namespace PomodoroWindows.Core.Tests;

public class UpdateBannerStateTests
{
    private static readonly LatestRelease V120 = new("v1.2.0", "https://x/1.2.0");

    [Fact]
    public void Timer_start_and_pause_without_a_release_report_no_change()
    {
        var state = new UpdateBannerState();

        Assert.False(state.SetTimerRunning(true));
        Assert.False(state.SetTimerRunning(false));
        Assert.False(state.IsOpen);
    }

    [Fact]
    public void Release_shows_when_idle_and_hides_while_running()
    {
        var state = new UpdateBannerState();

        Assert.True(state.SetRelease(V120));
        Assert.True(state.IsOpen);

        Assert.True(state.SetTimerRunning(true));
        Assert.False(state.IsOpen);
        Assert.False(state.SetTimerRunning(true));

        Assert.True(state.SetTimerRunning(false));
        Assert.True(state.IsOpen);
    }

    [Fact]
    public void Mini_mode_hides_the_banner()
    {
        var state = new UpdateBannerState();
        state.SetRelease(V120);

        Assert.True(state.SetMiniMode(true));
        Assert.False(state.IsOpen);
        Assert.True(state.SetMiniMode(false));
        Assert.True(state.IsOpen);
    }

    [Fact]
    public void Same_release_again_reports_no_change()
    {
        var state = new UpdateBannerState();
        state.SetRelease(V120);

        Assert.False(state.SetRelease(new LatestRelease("v1.2.0", "https://x/1.2.0")));
    }

    [Fact]
    public void Clearing_the_release_closes_the_banner()
    {
        var state = new UpdateBannerState();
        state.SetRelease(V120);

        Assert.True(state.SetRelease(null));
        Assert.False(state.IsOpen);
        Assert.Null(state.Release);
    }
}
