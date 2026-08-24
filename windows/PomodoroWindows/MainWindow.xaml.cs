using System.Runtime.InteropServices;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Windows.Graphics;
using Windows.Storage;

namespace PomodoroWindows;

public sealed partial class MainWindow : Window
{
    // Placeholder shown only for the first frame or two, until FitToContent measures the
    // actual page and shrinks the window to the real minimum.
    private const int FallbackWidth = 420;
    private const int FallbackHeight = 620;
    private const int TitleBarHeightDips = 40;
    private const int ContentHorizontalPaddingDips = 32;
    private const int ContentVerticalPaddingDips = 32;
    private const int ScreenEdgeMarginDips = 16;
    // Wide enough that the title bar's icon + mode-toggle button never collide with the
    // OS-drawn caption buttons, even when a mode's content (like the mini pill) is narrower.
    private const int MinWindowWidthDips = 260;
    private const int MinWindowHeightDips = 120;
    private const string WindowXKey = "WindowPositionX";
    private const string WindowYKey = "WindowPositionY";

    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;
    private bool _hasCustomPosition;
    private bool _suppressPositionSave;

    private readonly ITaskbarList3? _taskbarList;
    private int? _lastBadgeMinutes;
    private Windows.UI.Color? _lastBadgeColor;

    public static MainWindow? Current { get; private set; }

    public bool AllowClose { get; set; }

    public MainWindow()
    {
        Current = this;
        InitializeComponent();
        _taskbarList = CreateTaskbarList();
        Title = "Pomodoro";
        AppWindow.SetIcon(System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico"));
        AppWindow.Closing += AppWindow_Closing;
        AppWindow.Changed += AppWindow_Changed;

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        SystemBackdrop = new MicaBackdrop();

        AppWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;

        // Reserve space in the custom title bar so our own controls don't sit under the
        // OS-drawn caption buttons (min/max/close), which are overlaid on top of our content.
        CaptionButtonsReserveColumn.Width = new GridLength(AppWindow.TitleBar.RightInset / GetScale());

        RootFrame.Navigated += RootFrame_Navigated;

        // Keeps the timer visible above other work windows without covering them: pinned on
        // top, and docked to a screen corner by default — but if that corner turns out to sit
        // over content the user cares about, dragging it anywhere is remembered for next launch.
        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsAlwaysOnTop = true;
            var scale = GetScale();
            presenter.PreferredMinimumWidth = (int)(MinWindowWidthDips * scale);
            presenter.PreferredMinimumHeight = (int)(MinWindowHeightDips * scale);
        }

        _suppressPositionSave = true;
        var savedPosition = LoadSavedPosition();
        if (savedPosition is { } position)
        {
            _hasCustomPosition = true;
            ResizeOnly(FallbackWidth, FallbackHeight);
            AppWindow.Move(position);
        }
        else
        {
            DockBottomRight(FallbackWidth, FallbackHeight);
        }
        _suppressPositionSave = false;
    }

    // Called once the current page's root content has laid out, so the window can shrink to
    // the actual minimum it needs instead of a guessed placeholder size.
    public void FitToContent(double contentWidthDips, double contentHeightDips)
    {
        var width = Math.Max((int)Math.Ceiling(contentWidthDips) + ContentHorizontalPaddingDips, MinWindowWidthDips);
        var height = Math.Max((int)Math.Ceiling(contentHeightDips) + TitleBarHeightDips + ContentVerticalPaddingDips, MinWindowHeightDips);

        _suppressPositionSave = true;
        if (_hasCustomPosition)
        {
            ResizeOnly(width, height);
        }
        else
        {
            DockBottomRight(width, height);
        }
        _suppressPositionSave = false;
    }

    private Views.TimerPage? _subscribedTimerPage;

    private void RootFrame_Navigated(object sender, Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        if (_subscribedTimerPage is not null)
        {
            _subscribedTimerPage.MiniModeChanged -= TimerPage_MiniModeChanged;
            _subscribedTimerPage = null;
        }

        if (e.Content is Views.TimerPage timerPage)
        {
            ModeToggleButton.Visibility = Visibility.Visible;
            _subscribedTimerPage = timerPage;
            timerPage.MiniModeChanged += TimerPage_MiniModeChanged;
            TimerPage_MiniModeChanged(timerPage, timerPage.IsMiniMode);
        }
        else
        {
            ModeToggleButton.Visibility = Visibility.Collapsed;
        }
    }

    private void TimerPage_MiniModeChanged(object? sender, bool isMiniMode)
    {
        ModeToggleButton.Content = isMiniMode ? "전체" : "미니";
        // Windows only ever blurs the desktop wallpaper through a backdrop, never other apps'
        // actual window content (removed for privacy reasons since the Aero Glass era) — so this
        // just gives the mini widget the standard frosted-glass look, not literal see-through.
        SystemBackdrop = isMiniMode ? new DesktopAcrylicBackdrop() : new MicaBackdrop();
    }

    private void ModeToggleButton_Click(object sender, RoutedEventArgs e) =>
        _subscribedTimerPage?.ToggleMiniMode();

    [DllImport("user32.dll")]
    private static extern int GetDpiForWindow(nint hWnd);

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(nint hIcon);

    private double GetScale()
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        return GetDpiForWindow(hwnd) / 96.0;
    }

    private static ITaskbarList3? CreateTaskbarList()
    {
        try
        {
            var taskbarList = (ITaskbarList3)new TaskbarInstance();
            taskbarList.HrInit();
            return taskbarList;
        }
        catch (Exception)
        {
            // ITaskbarList3 is unavailable in some environments (e.g. certain sandboxed or
            // pre-Win7 hosts). The badge simply stays off; the rest of the app is unaffected.
            return null;
        }
    }

    // Draws the remaining-minutes count into a small circular icon and overlays it on this
    // window's taskbar button. Passing null clears the badge (e.g. when paused or the feature
    // is turned off). Skips regenerating the icon when nothing visible would change, since this
    // is driven by a per-second timer tick.
    public void UpdateTaskbarBadge(int? minutes, Windows.UI.Color accent)
    {
        if (_taskbarList is null) return;
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);

        if (minutes is null)
        {
            if (_lastBadgeMinutes is null) return;
            _taskbarList.SetOverlayIcon(hwnd, 0, string.Empty);
            _lastBadgeMinutes = null;
            _lastBadgeColor = null;
            return;
        }

        if (minutes == _lastBadgeMinutes && accent == _lastBadgeColor) return;
        _lastBadgeMinutes = minutes;
        _lastBadgeColor = accent;

        using var bitmap = CreateBadgeBitmap(minutes.Value, accent);
        var hIcon = bitmap.GetHicon();
        try
        {
            _taskbarList.SetOverlayIcon(hwnd, hIcon, $"{minutes}분 남음");
        }
        finally
        {
            DestroyIcon(hIcon);
        }
    }

    private static System.Drawing.Bitmap CreateBadgeBitmap(int minutes, Windows.UI.Color accent)
    {
        const int size = 32;
        var bitmap = new System.Drawing.Bitmap(size, size);
        using var g = System.Drawing.Graphics.FromImage(bitmap);
        g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;
        g.Clear(System.Drawing.Color.Transparent);

        using var brush = new System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(255, accent.R, accent.G, accent.B));
        g.FillEllipse(brush, 0, 0, size, size);

        var text = minutes.ToString();
        using var font = new System.Drawing.Font("Segoe UI", text.Length > 2 ? 11f : 14f, System.Drawing.FontStyle.Bold);
        var textSize = g.MeasureString(text, font);
        g.DrawString(text, font, System.Drawing.Brushes.White,
            (size - textSize.Width) / 2, (size - textSize.Height) / 2);

        return bitmap;
    }

    private void ResizeOnly(int widthDips, int heightDips)
    {
        var scale = GetScale();
        AppWindow.Resize(new SizeInt32((int)(widthDips * scale), (int)(heightDips * scale)));
    }

    private void DockBottomRight(int widthDips, int heightDips)
    {
        var scale = GetScale();
        var width = (int)(widthDips * scale);
        var height = (int)(heightDips * scale);
        AppWindow.Resize(new SizeInt32(width, height));

        var margin = (int)(ScreenEdgeMarginDips * scale);
        var workArea = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        AppWindow.Move(new PointInt32(
            workArea.X + workArea.Width - width - margin,
            workArea.Y + workArea.Height - height - margin));
    }

    // The user dragging the window to a spot where the default corner covered something they
    // needed is treated as a deliberate override: remember it so future launches respect it
    // instead of snapping back to the corner.
    private void AppWindow_Changed(AppWindow sender, AppWindowChangedEventArgs args)
    {
        if (_suppressPositionSave || !args.DidPositionChange) return;
        _hasCustomPosition = true;
        _localSettings.Values[WindowXKey] = sender.Position.X;
        _localSettings.Values[WindowYKey] = sender.Position.Y;
    }

    private PointInt32? LoadSavedPosition()
    {
        if (!_localSettings.Values.TryGetValue(WindowXKey, out var xValue) || xValue is not int x ||
            !_localSettings.Values.TryGetValue(WindowYKey, out var yValue) || yValue is not int y)
        {
            return null;
        }

        return new PointInt32(x, y);
    }

    private void AppWindow_Closing(Microsoft.UI.Windowing.AppWindow sender, Microsoft.UI.Windowing.AppWindowClosingEventArgs args)
    {
        if (AllowClose) return;
        args.Cancel = true;
        AppWindow.Hide();
    }
}

// Minimal declaration of the Windows Shell taskbar COM API, exposing only the members needed
// for overlay icons. Every member up to and including SetOverlayIcon must stay in this exact
// order — COM interop dispatches by vtable slot, not by name, so any reordering silently calls
// the wrong native method.
[ComImport]
[Guid("EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface ITaskbarList3
{
    // ITaskbarList
    void HrInit();
    void AddTab(nint hwnd);
    void DeleteTab(nint hwnd);
    void ActivateTab(nint hwnd);
    void SetActiveAlt(nint hwnd);

    // ITaskbarList2
    void MarkFullscreenWindow(nint hwnd, [MarshalAs(UnmanagedType.Bool)] bool fFullscreen);

    // ITaskbarList3
    void SetProgressValue(nint hwnd, ulong ullCompleted, ulong ullTotal);
    void SetProgressState(nint hwnd, int tbpFlags);
    void RegisterTab(nint hwndTab, nint hwndMDI);
    void UnregisterTab(nint hwndTab);
    void SetTabOrder(nint hwndTab, nint hwndInsertBefore);
    void SetTabActive(nint hwndTab, nint hwndMDI, int tbatFlags);
    void ThumbBarAddButtons(nint hwnd, uint cButtons, nint pButtons);
    void ThumbBarUpdateButtons(nint hwnd, uint cButtons, nint pButtons);
    void ThumbBarSetImageList(nint hwnd, nint himl);
    void SetOverlayIcon(nint hwnd, nint hIcon, [MarshalAs(UnmanagedType.LPWStr)] string pszDescription);
    void SetThumbnailTooltip(nint hwnd, [MarshalAs(UnmanagedType.LPWStr)] string pszTip);
    void SetThumbnailClip(nint hwnd, nint prcClip);
}

[ComImport]
[Guid("56FDF344-FD6D-11d0-958A-006097C9A090")]
internal class TaskbarInstance
{
}
