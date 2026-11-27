namespace PomodoroWindows.Core;

/// <summary>
/// <see cref="CompletedPomodoros"/> counts focus sessions since the last long break (it drives the
/// long-break cycle). <see cref="TotalFocusCompletions"/> is a never-resetting count of focus phases
/// the engine has ended; the difference between two states is how many focus sessions ended in
/// between, even when one tick crossed several phase boundaries.
/// </summary>
public sealed record TimerState(
    TimerPhase Phase,
    bool IsRunning,
    long RemainingMillis,
    long TotalMillis,
    int CompletedPomodoros = 0,
    long TotalFocusCompletions = 0);
