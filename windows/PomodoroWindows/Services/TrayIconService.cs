using H.NotifyIcon;
using Microsoft.UI.Xaml;
using PomodoroWindows.Core;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Services;

public sealed class TrayIconService : IDisposable
{
    private readonly TaskbarIcon _taskbarIcon;
    private readonly TimerViewModel _timerViewModel;
    private readonly Window _window;

    public event Action? ExitRequested;

    public TrayIconService(Window window, TimerViewModel timerViewModel)
    {
        _window = window;
        _timerViewModel = timerViewModel;

        _taskbarIcon = new TaskbarIcon
        {
            ToolTipText = "Pomodoro",
            Icon = new System.Drawing.Icon(System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico"))
        };
        _taskbarIcon.LeftClickCommand = new RelayNoArgCommand(RestoreWindow);

        var menu = new Microsoft.UI.Xaml.Controls.MenuFlyout();
        var toggleItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "시작/일시정지" };
        toggleItem.Click += (_, _) => _timerViewModel.StartPauseCommand.Execute(DateTimeOffset.UtcNow);
        var resetItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "리셋" };
        resetItem.Click += (_, _) => _timerViewModel.ResetCommand.Execute(null);
        var openItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "열기" };
        openItem.Click += (_, _) => RestoreWindow();
        var exitItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "종료" };
        exitItem.Click += (_, _) => ExitRequested?.Invoke();

        menu.Items.Add(toggleItem);
        menu.Items.Add(resetItem);
        menu.Items.Add(openItem);
        menu.Items.Add(exitItem);
        _taskbarIcon.ContextFlyout = menu;
    }

    public void Show() => _taskbarIcon.ForceCreate();

    private void RestoreWindow()
    {
        _window.AppWindow.Show();
        _window.Activate();
    }

    public void Dispose() => _taskbarIcon.Dispose();
}

internal sealed class RelayNoArgCommand : System.Windows.Input.ICommand
{
    private readonly Action _action;
    public RelayNoArgCommand(Action action) => _action = action;
    public event EventHandler? CanExecuteChanged;
    public bool CanExecute(object? parameter) => true;
    public void Execute(object? parameter) => _action();
}
