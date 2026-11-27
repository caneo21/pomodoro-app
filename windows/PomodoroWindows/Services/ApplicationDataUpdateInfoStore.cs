using PomodoroWindows.Core;
using Windows.Storage;

namespace PomodoroWindows.Services;

/// <summary>Keeps update-check state in the app's LocalSettings, next to the timer settings.</summary>
public sealed class ApplicationDataUpdateInfoStore : IUpdateInfoStore
{
    private const string LastCheckedAtKey = "UpdateLastCheckedAt";
    private const string LatestTagKey = "UpdateLatestTag";
    private const string LatestUrlKey = "UpdateLatestUrl";
    private const string DismissedTagKey = "UpdateDismissedTag";

    private readonly ApplicationDataContainer _container = ApplicationData.Current.LocalSettings;

    public StoredUpdateInfo Load() => new(
        LastCheckedAtMillis: _container.Values.TryGetValue(LastCheckedAtKey, out var at) && at is long l ? l : 0,
        LatestTag: GetString(LatestTagKey),
        LatestUrl: GetString(LatestUrlKey),
        DismissedTag: GetString(DismissedTagKey));

    public void SaveCheckResult(long nowMillis, LatestRelease release)
    {
        _container.Values[LastCheckedAtKey] = nowMillis;
        _container.Values[LatestTagKey] = release.Tag;
        _container.Values[LatestUrlKey] = release.Url;
    }

    public void SaveDismissed(string tag) => _container.Values[DismissedTagKey] = tag;

    private string? GetString(string key) =>
        _container.Values.TryGetValue(key, out var value) && value is string s ? s : null;
}
