namespace PomodoroWindows.Core;

public sealed record TimerState(
    TimerPhase Phase,
    bool IsRunning,
    long RemainingMillis,
    long TotalMillis,
    int CompletedPomodoros = 0);
