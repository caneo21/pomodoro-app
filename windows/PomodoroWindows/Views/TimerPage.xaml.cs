using System.ComponentModel;
using System.Diagnostics;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;
using Windows.Storage;
using Windows.UI;

namespace PomodoroWindows.Views;

public sealed partial class TimerPage : Page
{
    private const string MiniModeKey = "IsMiniMode";
    private const double ProgressSpringStiffness = 90.0;
    private const double ProgressSpringDamping = 12.0;
    private const double ColorMixSpringStiffness = 140.0;
    private const double ColorMixSpringDamping = 24.0;

    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;
    private readonly Spring1D _progressSpring = new(0.0, ProgressSpringStiffness, ProgressSpringDamping);
    private readonly Spring1D _colorMixSpring = new(1.0, ColorMixSpringStiffness, ColorMixSpringDamping);
    private TimerViewModel? _viewModel;
    private Models.TimerSettings _settings = new();
    private Color _previousAccent;
    private Color _targetAccent;
    private long _lastRenderingTimestamp;
    private bool _isRenderingLoopActive;

    public bool IsMiniMode { get; private set; }
    public event EventHandler<bool>? MiniModeChanged;

    public TimerPage()
    {
        InitializeComponent();
        IsMiniMode = _localSettings.Values.TryGetValue(MiniModeKey, out var saved) && saved is true;
        ApplyModeVisibility(isInitialLoad: true);
    }

    public void ToggleMiniMode()
    {
        IsMiniMode = !IsMiniMode;
        _localSettings.Values[MiniModeKey] = IsMiniMode;
        ApplyModeVisibility(isInitialLoad: false);
        Render(resetMotion: false);
        MiniModeChanged?.Invoke(this, IsMiniMode);
    }

    // Both views stay in the live layout tree at all times (Opacity/hit-testing only) so
    // neither is ever fully Collapsed. Their ActualWidth/Height still isn't trustworthy here
    // though: whichever view is currently hidden keeps getting arranged inside whatever the
    // window's CURRENT size happens to be (e.g. squeezed into the small mini window), so
    // reading its ActualSize after switching modes just reads back that squeezed leftover.
    // Measuring against infinite space instead yields the view's true, unconstrained size.
    private void ApplyModeVisibility(bool isInitialLoad)
    {
        TimerCard.Opacity = IsMiniMode ? 0 : 1;
        TimerCard.IsHitTestVisible = !IsMiniMode;
        MiniView.Opacity = IsMiniMode ? 1 : 0;
        MiniView.IsHitTestVisible = IsMiniMode;

        FrameworkElement active = IsMiniMode ? MiniView : TimerCard;

        if (isInitialLoad)
        {
            void OnFirstSizeChanged(object s, Microsoft.UI.Xaml.SizeChangedEventArgs e)
            {
                active.SizeChanged -= OnFirstSizeChanged;
                FitToNaturalSize(active);
            }
            active.SizeChanged += OnFirstSizeChanged;
        }
        else
        {
            FitToNaturalSize(active);
        }
    }

    private static void FitToNaturalSize(FrameworkElement element)
    {
        element.Measure(new Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
        var size = element.DesiredSize;
        if (size.Height > 0)
        {
            MainWindow.Current?.FitToContent(size.Width, size.Height);
        }
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _viewModel = (TimerViewModel)e.Parameter;
        _settings = new Services.ApplicationDataSettingsStore().Load();
        _viewModel.PropertyChanged += ViewModel_PropertyChanged;
        ActualThemeChanged += ActualThemeChanged_Handler;
        StartRenderingLoop();
        Render(resetMotion: true);
    }

    protected override void OnNavigatedFrom(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (_viewModel is not null) _viewModel.PropertyChanged -= ViewModel_PropertyChanged;
        ActualThemeChanged -= ActualThemeChanged_Handler;
        StopRenderingLoop();
    }

    private void ViewModel_PropertyChanged(object? sender, PropertyChangedEventArgs e) =>
        DispatcherQueue.TryEnqueue(() => Render(resetMotion: false));

    private void ActualThemeChanged_Handler(FrameworkElement sender, object args) =>
        DispatcherQueue.TryEnqueue(() => Render(resetMotion: false));

    private void StartRenderingLoop()
    {
        if (_isRenderingLoopActive) return;
        _isRenderingLoopActive = true;
        _lastRenderingTimestamp = Stopwatch.GetTimestamp();
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering += CompositionTarget_Rendering;
    }

    private void StopRenderingLoop()
    {
        if (!_isRenderingLoopActive) return;
        _isRenderingLoopActive = false;
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering -= CompositionTarget_Rendering;
    }

    private void CompositionTarget_Rendering(object? sender, object e)
    {
        var now = Stopwatch.GetTimestamp();
        var deltaSeconds = (now - _lastRenderingTimestamp) / (double)Stopwatch.Frequency;
        _lastRenderingTimestamp = now;
        // A dropped/late frame (e.g. the window was occluded) can report a huge delta; clamp it
        // so the spring doesn't take one enormous, visibly-jarring step to catch up.
        deltaSeconds = Math.Min(deltaSeconds, 1.0 / 15.0);

        _progressSpring.Step(deltaSeconds);
        _colorMixSpring.Step(deltaSeconds);

        ProgressRing.Progress = _progressSpring.Value;
        ProgressRing.RingBrush = new SolidColorBrush(Lerp(_previousAccent, _targetAccent, Clamp01(_colorMixSpring.Value)));
    }

    private static double Clamp01(double value) => Math.Max(0.0, Math.Min(1.0, value));

    private static Color Lerp(Color from, Color to, double t) => Color.FromArgb(
        255,
        (byte)(from.R + (to.R - from.R) * t),
        (byte)(from.G + (to.G - from.G) * t),
        (byte)(from.B + (to.B - from.B) * t));

    private void Render(bool resetMotion)
    {
        if (_viewModel is null) return;

        PhaseText.Text = _viewModel.Phase switch
        {
            TimerPhase.Focus => "집중",
            TimerPhase.ShortBreak => "휴식",
            TimerPhase.LongBreak => "긴 휴식",
            _ => string.Empty
        };

        var totalSeconds = Math.Max(_viewModel.RemainingMillis / 1000, 0);
        var timeText = $"{totalSeconds / 60:D2}:{totalSeconds % 60:D2}";
        RemainingText.Text = timeText;
        MiniRemainingText.Text = timeText;
        CompletedText.Text = $"완료한 뽀모도로: {_viewModel.CompletedPomodoros}";
        StartPauseButton.Content = _viewModel.IsRunning ? "일시정지" : "시작";

        var isDark = this.ActualTheme == ElementTheme.Dark;
        var palette = Palettes.PaletteFor(_settings.ColorTheme, isDark);
        var accent = _viewModel.Phase == TimerPhase.LongBreak ? palette.LongBreakAccent : palette.Accent;

        var progressFraction = _viewModel.TotalMillis > 0
            ? (double)(_viewModel.TotalMillis - _viewModel.RemainingMillis) / _viewModel.TotalMillis
            : 0.0;

        if (resetMotion)
        {
            _progressSpring.Reset(progressFraction);
            _previousAccent = accent;
            _targetAccent = accent;
            _colorMixSpring.Reset(1.0);
            ProgressRing.Progress = progressFraction;
            ProgressRing.RingBrush = new SolidColorBrush(accent);
        }
        else
        {
            _progressSpring.SetTarget(progressFraction);
            if (accent != _targetAccent)
            {
                _previousAccent = Lerp(_previousAccent, _targetAccent, Clamp01(_colorMixSpring.Value));
                _targetAccent = accent;
                _colorMixSpring.Reset(0.0);
                _colorMixSpring.SetTarget(1.0);
            }
        }

        ProgressRing.TrackBrush = new SolidColorBrush(palette.Border);

        if (_settings.DailyGoalEnabled)
        {
            DailyGoalBadge.Visibility = Microsoft.UI.Xaml.Visibility.Visible;
            DailyGoalText.Text = $"오늘 목표: {_viewModel.DailyGoalCount}/{_settings.DailyGoalTarget}";
        }
        else
        {
            DailyGoalBadge.Visibility = Microsoft.UI.Xaml.Visibility.Collapsed;
        }

        // In mini mode the window backdrop is real Acrylic (see MainWindow), so the page itself
        // must stay transparent or it would paint over — and hide — the see-through effect.
        RootGrid.Background = new SolidColorBrush(IsMiniMode ? Colors.Transparent : palette.Background);
        TimerCard.Background = new SolidColorBrush(palette.Surface);
        TimerCard.BorderBrush = new SolidColorBrush(palette.Border);

        RemainingText.Foreground = new SolidColorBrush(palette.TextPrimary);
        MiniRemainingText.Foreground = new SolidColorBrush(palette.TextPrimary);
        CompletedText.Foreground = new SolidColorBrush(palette.TextSecondary);
        DailyGoalText.Foreground = new SolidColorBrush(palette.TextSecondary);

        PhaseBadge.Background = new SolidColorBrush(Color.FromArgb(38, accent.R, accent.G, accent.B));
        PhaseText.Foreground = new SolidColorBrush(accent);
        MiniPhaseDot.Fill = new SolidColorBrush(accent);

        // A frosted-glass tint over the acrylic backdrop — translucent enough to read as a
        // floating widget rather than an opaque card, while staying legible over any wallpaper.
        MiniView.Background = new SolidColorBrush(isDark
            ? Color.FromArgb(140, 20, 20, 20)
            : Color.FromArgb(140, 255, 255, 255));

        ApplyAccentButtonColors(StartPauseButton, accent, palette.OnAccent);

        var minutesRemaining = _settings.ShowTaskbarBadge && _viewModel.IsRunning
            ? (int?)(totalSeconds / 60)
            : null;
        MainWindow.Current?.UpdateTaskbarBadge(minutesRemaining, accent);
    }

    private static void ApplyAccentButtonColors(Button button, Color background, Color foreground)
    {
        button.Resources["ButtonBackground"] = new SolidColorBrush(background);
        button.Resources["ButtonBackgroundPointerOver"] = new SolidColorBrush(Blend(background, Colors.White, 0.12));
        button.Resources["ButtonBackgroundPressed"] = new SolidColorBrush(Blend(background, Colors.Black, 0.12));
        button.Resources["ButtonForeground"] = new SolidColorBrush(foreground);
        button.Resources["ButtonForegroundPointerOver"] = new SolidColorBrush(foreground);
        button.Resources["ButtonForegroundPressed"] = new SolidColorBrush(foreground);
        var transparent = new SolidColorBrush(Colors.Transparent);
        button.Resources["ButtonBorderBrush"] = transparent;
        button.Resources["ButtonBorderBrushPointerOver"] = transparent;
        button.Resources["ButtonBorderBrushPressed"] = transparent;
    }

    private static Color Blend(Color from, Color to, double amount) => Color.FromArgb(
        255,
        (byte)(from.R + (to.R - from.R) * amount),
        (byte)(from.G + (to.G - from.G) * amount),
        (byte)(from.B + (to.B - from.B) * amount));

    private void StartPauseButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.StartPauseCommand.Execute(DateTimeOffset.UtcNow);

    private void ResetButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.ResetCommand.Execute(null);

    private void SettingsButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        Frame.Navigate(typeof(SettingsPage), _viewModel);
}
