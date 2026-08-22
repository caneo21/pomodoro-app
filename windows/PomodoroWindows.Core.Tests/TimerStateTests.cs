using Xunit;

namespace PomodoroWindows.Core.Tests;

public class TimerStateTests
{
    [Fact]
    public void Two_states_with_the_same_field_values_are_equal()
    {
        var a = new TimerState(TimerPhase.Focus, true, 1000L, 2000L, 3);
        var b = new TimerState(TimerPhase.Focus, true, 1000L, 2000L, 3);

        Assert.Equal(a, b);
    }
}
