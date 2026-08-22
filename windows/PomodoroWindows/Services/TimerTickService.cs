using Microsoft.UI.Dispatching;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Services;

public sealed class TimerTickService
{
    private readonly DispatcherQueueTimer _timer;

    public TimerTickService(DispatcherQueue dispatcherQueue, TimerViewModel viewModel)
    {
        _timer = dispatcherQueue.CreateTimer();
        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += (_, _) => viewModel.Tick(DateTimeOffset.UtcNow);
    }

    public void Start() => _timer.Start();
    public void Stop() => _timer.Stop();
}
