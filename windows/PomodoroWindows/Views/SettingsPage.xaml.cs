using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using PomodoroWindows.Controls;
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
        ActualThemeChanged += (_, _) => ApplyPaletteToChrome();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        var timerViewModel = (TimerViewModel)e.Parameter;
        _viewModel = new SettingsViewModel(new Services.ApplicationDataSettingsStore(), timerViewModel);
        LoadIntoControls();
        ApplyPaletteToChrome();
        PressScaleBehavior.Attach(BackButton);
    }

    private void ApplyPaletteToChrome()
    {
        if (_viewModel is null) return;

        var isDark = this.ActualTheme == ElementTheme.Dark;
        var palette = Palettes.PaletteFor(_viewModel.ColorTheme, isDark);

        TitleText.Foreground = new SolidColorBrush(palette.TextPrimary);

        foreach (var card in new[] { TimerCard, AutomationCard, GoalCard, NotificationsCard, ThemeCard })
        {
            card.Background = new SolidColorBrush(palette.Surface);
            card.BorderBrush = new SolidColorBrush(palette.Border);
        }

        foreach (var header in new[] { TimerHeaderText, AutomationHeaderText, GoalHeaderText, NotificationsHeaderText, ThemeHeaderText })
        {
            header.Foreground = new SolidColorBrush(palette.TextSecondary);
        }
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
        ShowTaskbarBadgeToggle.IsOn = _viewModel.ShowTaskbarBadge;
        DailyGoalEnabledToggle.IsOn = _viewModel.DailyGoalEnabled;
        DailyGoalTargetLabel.Text = $"일일 목표: {_viewModel.DailyGoalTarget}개";
        DailyGoalTargetSlider.Value = _viewModel.DailyGoalTarget;
        NotificationsEnabledToggle.IsOn = _viewModel.NotificationsEnabled;
        UseAlarmSoundToggle.IsOn = _viewModel.UseAlarmSound;

        RenderThemeSwatches();

        _isLoading = false;
    }

    private void RenderThemeSwatches()
    {
        if (_viewModel is null) return;

        ThemeSwatchPanel.Children.Clear();
        foreach (ColorTheme theme in Enum.GetValues<ColorTheme>())
        {
            var palette = Palettes.PaletteFor(theme, darkTheme: false);
            var isSelected = theme == _viewModel.ColorTheme;

            var swatch = new Ellipse
            {
                Width = 36,
                Height = 36,
                Fill = new SolidColorBrush(palette.Accent),
            };

            var ring = new Border
            {
                Width = 44,
                Height = 44,
                CornerRadius = new CornerRadius(22),
                BorderThickness = new Thickness(isSelected ? 2 : 0),
                BorderBrush = new SolidColorBrush(palette.Accent),
                Padding = new Thickness(4),
                Tag = theme,
                Child = swatch
            };
            ring.Tapped += ThemeSwatch_Tapped;
            ThemeSwatchPanel.Children.Add(ring);
        }
    }

    private void ThemeSwatch_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _viewModel.ColorTheme = (ColorTheme)((Border)sender).Tag;
        _viewModel.Save();
        RenderThemeSwatches();
        ApplyPaletteToChrome();
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
        (Microsoft.UI.Xaml.Application.Current as App)?.RefreshKeepAwake();
    }

    private void ShowTaskbarBadgeToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.ShowTaskbarBadge = ShowTaskbarBadgeToggle.IsOn;
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
