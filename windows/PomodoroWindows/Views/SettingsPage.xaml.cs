using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Views;

public sealed partial class SettingsPage : Page
{
    private SettingsViewModel? _viewModel;
    private bool _isLoading;

    public SettingsPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        var timerViewModel = (TimerViewModel)e.Parameter;
        _viewModel = new SettingsViewModel(new Services.ApplicationDataSettingsStore(), timerViewModel);
        LoadIntoControls();
    }

    private void LoadIntoControls()
    {
        if (_viewModel is null) return;
        _isLoading = true;

        FocusLabel.Text = $"집중 시간: {_viewModel.FocusMinutes}분";
        FocusSlider.Value = _viewModel.FocusMinutes;
        BreakLabel.Text = $"휴식 시간: {_viewModel.BreakMinutes}분";
        BreakSlider.Value = _viewModel.BreakMinutes;
        LongBreakLabel.Text = $"긴 휴식 시간: {_viewModel.LongBreakMinutes}분";
        LongBreakSlider.Value = _viewModel.LongBreakMinutes;
        PomodorosUntilLongBreakLabel.Text = $"긴 휴식까지 뽀모도로 횟수: {_viewModel.PomodorosUntilLongBreak}회";
        PomodorosUntilLongBreakSlider.Value = _viewModel.PomodorosUntilLongBreak;
        AutoStartBreakToggle.IsOn = _viewModel.AutoStartBreak;
        AutoStartFocusToggle.IsOn = _viewModel.AutoStartFocus;
        KeepScreenOnToggle.IsOn = _viewModel.KeepScreenOn;
        DailyGoalEnabledToggle.IsOn = _viewModel.DailyGoalEnabled;
        DailyGoalTargetLabel.Text = $"일일 목표: {_viewModel.DailyGoalTarget}개";
        DailyGoalTargetSlider.Value = _viewModel.DailyGoalTarget;
        NotificationsEnabledToggle.IsOn = _viewModel.NotificationsEnabled;
        UseAlarmSoundToggle.IsOn = _viewModel.UseAlarmSound;

        ThemeSwatchPanel.Children.Clear();
        foreach (ColorTheme theme in Enum.GetValues<ColorTheme>())
        {
            var palette = Palettes.PaletteFor(theme, darkTheme: false);
            var swatch = new Ellipse
            {
                Width = 40,
                Height = 40,
                Fill = new SolidColorBrush(palette.Primary),
                Tag = theme
            };
            swatch.Tapped += ThemeSwatch_Tapped;
            ThemeSwatchPanel.Children.Add(swatch);
        }

        _isLoading = false;
    }

    private void ThemeSwatch_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _viewModel.ColorTheme = (ColorTheme)((Ellipse)sender).Tag;
        _viewModel.Save();
    }

    private void FocusSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.FocusMinutes = (int)e.NewValue;
        FocusLabel.Text = $"집중 시간: {_viewModel.FocusMinutes}분";
        _viewModel.Save();
    }

    private void BreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.BreakMinutes = (int)e.NewValue;
        BreakLabel.Text = $"휴식 시간: {_viewModel.BreakMinutes}분";
        _viewModel.Save();
    }

    private void LongBreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.LongBreakMinutes = (int)e.NewValue;
        LongBreakLabel.Text = $"긴 휴식 시간: {_viewModel.LongBreakMinutes}분";
        _viewModel.Save();
    }

    private void PomodorosUntilLongBreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.PomodorosUntilLongBreak = (int)e.NewValue;
        PomodorosUntilLongBreakLabel.Text = $"긴 휴식까지 뽀모도로 횟수: {_viewModel.PomodorosUntilLongBreak}회";
        _viewModel.Save();
    }

    private void DailyGoalTargetSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.DailyGoalTarget = (int)e.NewValue;
        DailyGoalTargetLabel.Text = $"일일 목표: {_viewModel.DailyGoalTarget}개";
        _viewModel.Save();
    }

    private void AutoStartBreakToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.AutoStartBreak = AutoStartBreakToggle.IsOn;
        _viewModel.Save();
    }

    private void AutoStartFocusToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.AutoStartFocus = AutoStartFocusToggle.IsOn;
        _viewModel.Save();
    }

    private void KeepScreenOnToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.KeepScreenOn = KeepScreenOnToggle.IsOn;
        _viewModel.Save();
    }

    private void DailyGoalEnabledToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.DailyGoalEnabled = DailyGoalEnabledToggle.IsOn;
        _viewModel.Save();
    }

    private void NotificationsEnabledToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.NotificationsEnabled = NotificationsEnabledToggle.IsOn;
        _viewModel.Save();
    }

    private void UseAlarmSoundToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.UseAlarmSound = UseAlarmSoundToggle.IsOn;
        _viewModel.Save();
    }

    private void BackButton_Click(object sender, RoutedEventArgs e) => Frame.GoBack();
}
