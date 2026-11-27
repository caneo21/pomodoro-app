using System.ComponentModel;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppNotifications;
using PomodoroWindows.Core;
using PomodoroWindows.Services;
using PomodoroWindows.ViewModels;
using PomodoroWindows.Views;

namespace PomodoroWindows;

public partial class App : Application
{
    private Window? _window;
    private TimerTickService? _tickService;
    private ApplicationDataSettingsStore? _settingsStore;
    private TimerViewModel? _timerViewModel;
    private KeepAwakeService? _keepAwakeService;
#if !DEBUG
    private Microsoft.UI.Dispatching.DispatcherQueueTimer? _updateCheckTimer;
    private static readonly TimeSpan UpdateRecheckInterval = TimeSpan.FromHours(6);
#endif

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        var settingsStore = new ApplicationDataSettingsStore();
        _settingsStore = settingsStore;
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
        _timerViewModel = timerViewModel;

        var keepAwakeService = new KeepAwakeService();
        _keepAwakeService = keepAwakeService;
        timerViewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TimerViewModel.IsRunning))
            {
                UpdateKeepAwake();
                (_window as MainWindow)?.SetTimerRunning(timerViewModel.IsRunning);
            }
        };

        _window = new MainWindow();
        var frame = ((MainWindow)_window).RootFrame;
        frame.Navigate(typeof(TimerPage), timerViewModel);

        _tickService = new TimerTickService(Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread(), timerViewModel);
        _tickService.Start();

        _window.Activate();
        _ = ShowUpdateBannerAsync((MainWindow)_window, timerViewModel);

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

    // Asks GitHub whether a newer release exists and shows the window's banner if so. The app
    // lives in the tray for days, so besides launch it re-asks every few hours; UpdateCoordinator
    // still contacts GitHub at most once a day. Debug builds never check, so local development
    // doesn't keep announcing the latest release.
    private async Task ShowUpdateBannerAsync(MainWindow window, TimerViewModel timerViewModel)
    {
#if !DEBUG
        if (AppVersion.Current is not { } installedVersion) return;
        var coordinator = new UpdateCoordinator(
            new ApplicationDataUpdateInfoStore(),
            new GitHubReleaseFetcher(),
            installedVersion,
            () => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        window.UpdateDismissed += coordinator.Dismiss;
        // Awaited on the UI thread, so the continuation can touch the window directly.
        var release = await coordinator.RefreshAsync();
        window.SetTimerRunning(timerViewModel.IsRunning);
        window.SetUpdateRelease(release);

        _updateCheckTimer = Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread().CreateTimer();
        _updateCheckTimer.Interval = UpdateRecheckInterval;
        _updateCheckTimer.Tick += async (_, _) => window.SetUpdateRelease(await coordinator.RefreshAsync());
        _updateCheckTimer.Start();
#else
        await Task.CompletedTask;
#endif
    }

    // Re-runs the same keep-awake decision the IsRunning-change handler uses, so toggling the
    // KeepScreenOn setting while a session is already running takes effect immediately instead of
    // waiting for the next start/pause.
    public void RefreshKeepAwake() => UpdateKeepAwake();

    private void UpdateKeepAwake()
    {
        if (_timerViewModel is null || _keepAwakeService is null || _settingsStore is null) return;
        var keepScreenOn = _settingsStore.Load().KeepScreenOn;
        _keepAwakeService.SetActive(_timerViewModel.IsRunning && keepScreenOn);
    }
}
