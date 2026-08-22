using Microsoft.UI.Xaml;
using PomodoroWindows.Services;
using PomodoroWindows.ViewModels;
using PomodoroWindows.Views;

namespace PomodoroWindows;

public partial class App : Application
{
    private Window? _window;
    private TimerTickService? _tickService;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        var settingsStore = new ApplicationDataSettingsStore();
        var notificationService = new FakeNotificationService(); // replaced with AppNotificationService in Task 12
        var timerViewModel = new TimerViewModel(settingsStore, notificationService);

        _window = new MainWindow();
        var frame = ((MainWindow)_window).RootFrame;
        frame.Navigate(typeof(TimerPage), timerViewModel);

        _tickService = new TimerTickService(Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread(), timerViewModel);
        _tickService.Start();

        _window.Activate();
    }
}
