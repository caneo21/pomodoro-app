using System.ComponentModel;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;
using Windows.UI;

namespace PomodoroWindows.Views;

public sealed partial class TimerPage : Page
{
    private TimerViewModel? _viewModel;

    public TimerPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _viewModel = (TimerViewModel)e.Parameter;
        _viewModel.PropertyChanged += ViewModel_PropertyChanged;
        Render();
    }

    protected override void OnNavigatedFrom(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (_viewModel is not null) _viewModel.PropertyChanged -= ViewModel_PropertyChanged;
    }

    private void ViewModel_PropertyChanged(object? sender, PropertyChangedEventArgs e) =>
        DispatcherQueue.TryEnqueue(Render);

    private void Render()
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
        RemainingText.Text = $"{totalSeconds / 60:D2}:{totalSeconds % 60:D2}";
        CompletedText.Text = $"완료한 뽀모도로: {_viewModel.CompletedPomodoros}";
        StartPauseButton.Content = _viewModel.IsRunning ? "일시정지" : "시작";

        ProgressRing.Maximum = Math.Max(_viewModel.TotalMillis, 1);
        ProgressRing.Value = _viewModel.TotalMillis - _viewModel.RemainingMillis;

        var palette = Palettes.PaletteFor(ColorTheme.Default, darkTheme: false);
        var accent = _viewModel.Phase == TimerPhase.LongBreak ? palette.LongBreakAccent : palette.Primary;
        ProgressRing.Foreground = new SolidColorBrush(accent);
        RootGrid.Background = new SolidColorBrush(palette.Background);
    }

    private void StartPauseButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.StartPauseCommand.Execute(DateTimeOffset.UtcNow);

    private void ResetButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.ResetCommand.Execute(null);

    private void SettingsButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        Frame.Navigate(typeof(SettingsPage), _viewModel);
}
