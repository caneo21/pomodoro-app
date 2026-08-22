using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppNotifications;
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
        try
        {
            AppNotificationManager.Default.Register();
        }
        catch (Exception)
        {
            // Notification registration can fail depending on deployment mode (e.g. a dev-mode
            // sideloaded MSIX without full notification-platform trust). Toasts silently become
            // unavailable in that case; the rest of the app still works.
        }
        var notificationService = new AppNotificationService(() => settingsStore.Load().UseAlarmSound);
        var timerViewModel = new TimerViewModel(settingsStore, notificationService);

        var keepAwakeService = new KeepAwakeService();
        timerViewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TimerViewModel.IsRunning))
            {
                var keepScreenOn = settingsStore.Load().KeepScreenOn;
                keepAwakeService.SetActive(timerViewModel.IsRunning && keepScreenOn);
            }
        };

        _window = new MainWindow();
        var frame = ((MainWindow)_window).RootFrame;
        frame.Navigate(typeof(TimerPage), timerViewModel);

        _tickService = new TimerTickService(Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread(), timerViewModel);
        _tickService.Start();

        _window.Activate();

        var trayIconService = new TrayIconService(_window, timerViewModel);
        trayIconService.Show();
        trayIconService.ExitRequested += () =>
        {
            ((MainWindow)_window).AllowClose = true;
            _tickService?.Stop();
            trayIconService.Dispose();
            _window.Close();
        };
    }
}
