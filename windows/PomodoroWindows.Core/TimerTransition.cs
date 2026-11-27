namespace PomodoroWindows.Core;

/// <summary>
/// What happened between two <see cref="TimerState"/>s from the same engine. A phase-only comparison
/// misses a tick that lands back in the phase it started in, and comparing
/// <see cref="TimerState.CompletedPomodoros"/> mistakes a long break ending (cycle count reset to 0)
/// for a completed focus session, so this uses <see cref="TimerState.TotalFocusCompletions"/>.
/// </summary>
public sealed record TimerTransition(bool CrossedBoundary, int FocusSessionsCompleted)
{
    public static TimerTransition Between(TimerState previous, TimerState current)
    {
        var focusEnded = (int)Math.Max(current.TotalFocusCompletions - previous.TotalFocusCompletions, 0);
        return new TimerTransition(focusEnded > 0 || current.Phase != previous.Phase, focusEnded);
    }
}
