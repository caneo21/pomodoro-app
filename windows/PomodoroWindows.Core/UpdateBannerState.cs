namespace PomodoroWindows.Core;

/// <summary>
/// Decides when the "new version available" banner is open: a release is known, no session is
/// running, and the window isn't in mini mode. Each setter returns whether anything visible
/// changed, so the window only re-lays itself out when it has to (not on every Start/Pause).
/// </summary>
public sealed class UpdateBannerState
{
    private bool _timerRunning;
    private bool _miniMode;

    public LatestRelease? Release { get; private set; }

    public bool IsOpen => Release is not null && !_timerRunning && !_miniMode;

    public bool SetRelease(LatestRelease? release) => Update(() => Release = release);

    public bool SetTimerRunning(bool isRunning) => Update(() => _timerRunning = isRunning);

    public bool SetMiniMode(bool isMiniMode) => Update(() => _miniMode = isMiniMode);

    private bool Update(Action change)
    {
        var (wasOpen, wasRelease) = (IsOpen, Release);
        change();
        return IsOpen != wasOpen || Release != wasRelease;
    }
}
