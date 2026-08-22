using PomodoroWindows.Models;

namespace PomodoroWindows.Services;

public interface ISettingsStore
{
    TimerSettings Load();
    void Save(TimerSettings settings);
}
