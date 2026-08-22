using PomodoroWindows.Models;

namespace PomodoroWindows.Services;

public sealed class FakeSettingsStore : ISettingsStore
{
    public TimerSettings Current { get; private set; } = new();

    public TimerSettings Load() => Current;

    public void Save(TimerSettings settings) => Current = settings;
}
