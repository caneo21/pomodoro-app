namespace PomodoroWindows.Core.Tests;

public class Spring1DTests
{
    private const double FrameSeconds = 1.0 / 60.0;

    [Fact]
    public void New_spring_starts_at_the_initial_value_with_no_target_gap()
    {
        var spring = new Spring1D(initialValue: 0.25, stiffness: 90, damping: 12);

        Assert.Equal(0.25, spring.Value);
        Assert.Equal(0.25, spring.Target);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void A_single_step_moves_the_value_a_small_amount_toward_the_target()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 12);
        spring.SetTarget(1.0);

        spring.Step(FrameSeconds);

        // One 1/60s frame of a spring this stiff moves only a small fraction of the way — this
        // pins down "gradual, not instant" without depending on exactly when it fully settles.
        Assert.True(spring.Value > 0.0);
        Assert.True(spring.Value < 0.5);
    }

    [Fact]
    public void An_overdamped_spring_never_overshoots_its_target()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 120, damping: 60);
        spring.SetTarget(1.0);

        var maxValue = 0.0;
        for (var i = 0; i < 600; i++)
        {
            spring.Step(FrameSeconds);
            maxValue = Math.Max(maxValue, spring.Value);
        }

        Assert.True(maxValue <= 1.01);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void An_underdamped_spring_overshoots_before_settling()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 4);
        spring.SetTarget(1.0);

        var maxValue = 0.0;
        for (var i = 0; i < 600; i++)
        {
            spring.Step(FrameSeconds);
            maxValue = Math.Max(maxValue, spring.Value);
        }

        Assert.True(maxValue > 1.02);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void Reset_snaps_the_value_and_target_together_and_clears_velocity()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 12);
        spring.SetTarget(1.0);
        for (var i = 0; i < 30; i++) spring.Step(FrameSeconds);

        spring.Reset(0.4);

        Assert.Equal(0.4, spring.Value);
        Assert.Equal(0.4, spring.Target);
        Assert.True(spring.IsSettled());
    }
}
