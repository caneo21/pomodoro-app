using System.Text.Json;
using System.Text.RegularExpressions;

namespace PomodoroWindows.Core;

/// <summary>A release version <c>MAJOR.MINOR.PATCH</c>, as used in <c>vX.Y.Z</c> release tags.</summary>
public readonly record struct SemVer(int Major, int Minor, int Patch) : IComparable<SemVer>
{
    private static readonly Regex Pattern = new(@"^v?(\d+)\.(\d+)\.(\d+)$", RegexOptions.CultureInvariant);

    /// <summary>Parses <c>v1.2.3</c> or <c>1.2.3</c>; anything else (pre-release suffixes, dev builds) is null.</summary>
    public static SemVer? Parse(string? text)
    {
        if (text is null) return null;
        var match = Pattern.Match(text);
        if (!match.Success) return null;
        return int.TryParse(match.Groups[1].Value, out var major)
            && int.TryParse(match.Groups[2].Value, out var minor)
            && int.TryParse(match.Groups[3].Value, out var patch)
            ? new SemVer(major, minor, patch)
            : null;
    }

    public int CompareTo(SemVer other) =>
        Major != other.Major ? Major.CompareTo(other.Major)
        : Minor != other.Minor ? Minor.CompareTo(other.Minor)
        : Patch.CompareTo(other.Patch);
}

/// <summary>The newest published release: its tag (e.g. <c>v1.2.0</c>) and the page to download it from.</summary>
public sealed record LatestRelease(string Tag, string Url);

public sealed record StoredUpdateInfo(
    long LastCheckedAtMillis = 0,
    string? LatestTag = null,
    string? LatestUrl = null,
    string? DismissedTag = null);

public static class UpdatePolicy
{
    public const long CheckIntervalMillis = 86_400_000;

    public static bool IsNewer(string? latestTag, string installedVersion) =>
        SemVer.Parse(latestTag) is { } latest
        && SemVer.Parse(installedVersion) is { } installed
        && latest.CompareTo(installed) > 0;

    /// <summary>
    /// Due if never checked (0), once a day after that, and also when the stored time is in the
    /// future (the clock moved back), so a bad clock can't block checks forever.
    /// </summary>
    public static bool ShouldCheck(long lastCheckedAtMillis, long nowMillis) =>
        lastCheckedAtMillis == 0
        || nowMillis < lastCheckedAtMillis
        || nowMillis - lastCheckedAtMillis >= CheckIntervalMillis;

    public static bool ShouldShow(string? latestTag, string installedVersion, string? dismissedTag) =>
        latestTag != dismissedTag && IsNewer(latestTag, installedVersion);
}

public static class LatestReleaseParser
{
    /// <summary>Reads <c>tag_name</c> and <c>html_url</c> from GitHub's latest-release JSON; null if either is missing.</summary>
    public static LatestRelease? Parse(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return null;
            if (!root.TryGetProperty("tag_name", out var tag) || tag.ValueKind != JsonValueKind.String) return null;
            if (!root.TryGetProperty("html_url", out var url) || url.ValueKind != JsonValueKind.String) return null;
            var tagText = tag.GetString();
            var urlText = url.GetString();
            if (string.IsNullOrEmpty(tagText) || urlText is null || !urlText.StartsWith("https://", StringComparison.Ordinal))
                return null;
            return new LatestRelease(tagText, urlText);
        }
        catch (JsonException)
        {
            return null;
        }
    }
}

public interface IUpdateInfoStore
{
    StoredUpdateInfo Load();
    void SaveCheckResult(long nowMillis, LatestRelease release);
    void SaveDismissed(string tag);
}

/// <summary>Returns the latest release, or null on any failure (offline, rate-limited, bad response).</summary>
public interface IReleaseFetcher
{
    Task<LatestRelease?> FetchLatestAsync();
}

public sealed class UpdateCoordinator
{
    private readonly IUpdateInfoStore _store;
    private readonly IReleaseFetcher _fetcher;
    private readonly string _installedVersion;
    private readonly Func<long> _nowMillis;

    public UpdateCoordinator(IUpdateInfoStore store, IReleaseFetcher fetcher, string installedVersion, Func<long> nowMillis)
    {
        _store = store;
        _fetcher = fetcher;
        _installedVersion = installedVersion;
        _nowMillis = nowMillis;
    }

    /// <summary>Checks GitHub if a check is due, then returns the release to tell the user about, or null.</summary>
    public async Task<LatestRelease?> RefreshAsync()
    {
        // Dev builds can't be compared, so they never check or show anything.
        if (SemVer.Parse(_installedVersion) is null) return null;

        var info = _store.Load();
        var now = _nowMillis();
        if (UpdatePolicy.ShouldCheck(info.LastCheckedAtMillis, now))
        {
            LatestRelease? fetched;
            try
            {
                fetched = await _fetcher.FetchLatestAsync();
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception)
            {
                fetched = null;
            }

            if (fetched is not null)
            {
                _store.SaveCheckResult(now, fetched);
                info = _store.Load();
            }
        }

        if (info.LatestTag is not { } tag || info.LatestUrl is not { } url) return null;
        return UpdatePolicy.ShouldShow(tag, _installedVersion, info.DismissedTag) ? new LatestRelease(tag, url) : null;
    }

    public void Dismiss(string tag) => _store.SaveDismissed(tag);
}
