using Microsoft.UI.Xaml;
using WinRT.Interop;

namespace PomodoroWindows;

public sealed partial class MainWindow : Window
{
    public bool AllowClose { get; set; }

    public MainWindow()
    {
        InitializeComponent();
        Title = "Pomodoro";
        AppWindow.Closing += AppWindow_Closing;
    }

    private void AppWindow_Closing(Microsoft.UI.Windowing.AppWindow sender, Microsoft.UI.Windowing.AppWindowClosingEventArgs args)
    {
        if (AllowClose) return;
        args.Cancel = true;
        AppWindow.Hide();
    }
}
