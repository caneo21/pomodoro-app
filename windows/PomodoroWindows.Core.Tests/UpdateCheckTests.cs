namespace PomodoroWindows.Core.Tests;

public class UpdateCheckTests
{
    private sealed class FakeStore : IUpdateInfoStore
    {
        public StoredUpdateInfo Info { get; set; } = new();
        public StoredUpdateInfo Load() => Info;
        public void SaveCheckResult(long nowMillis, LatestRelease release) =>
            Info = Info with { LastCheckedAtMillis = nowMillis, LatestTag = release.Tag, LatestUrl = release.Url };
        public void SaveDismissed(string tag) => Info = Info with { DismissedTag = tag };
    }

    private sealed class FakeFetcher : IReleaseFetcher
    {
        public Func<LatestRelease?> Result { get; set; }
        public int Calls { get; private set; }
        public FakeFetcher(Func<LatestRelease?> result) => Result = result;
        public Task<LatestRelease?> FetchLatestAsync()
        {
            Calls++;
            return Task.FromResult(Result());
        }
    }

    private const long Now = 10_000_000_000L;
    private static readonly LatestRelease V110 = new("v1.1.0", "https://x/1.1.0");

    // --- SemVer.Parse ---

    [Fact]
    public void Parse_accepts_v_prefixed_and_bare_versions()
    {
        Assert.Equal(new SemVer(1, 2, 3), SemVer.Parse("v1.2.3"));
        Assert.Equal(new SemVer(10, 0, 7), SemVer.Parse("10.0.7"));
    }

    [Theory]
    [InlineData("v1.2")]
    [InlineData("1.2.3.4")]
    [InlineData("v1.2.0-beta")]
    [InlineData("release-5")]
    [InlineData("")]
    [InlineData(null)]
    [InlineData("0.0.0-dev")]
    public void Parse_rejects_malformed_tags(string? text)
    {
        Assert.Null(SemVer.Parse(text));
    }

    // --- UpdatePolicy ---

    [Fact]
    public void IsNewer_compares_numerically()
    {
        Assert.True(UpdatePolicy.IsNewer("v1.10.0", "1.9.0"));
        Assert.False(UpdatePolicy.IsNewer("v1.2.0", "1.2.0"));
        Assert.False(UpdatePolicy.IsNewer("v1.1.0", "1.2.0"));
    }

    [Fact]
    public void IsNewer_is_false_when_either_side_is_unparseable()
    {
        Assert.False(UpdatePolicy.IsNewer("v2.0.0", "0.0.0-dev"));
        Assert.False(UpdatePolicy.IsNewer("garbage", "1.0.0"));
    }

    [Fact]
    public void ShouldCheck_after_24_hours()
    {
        Assert.True(UpdatePolicy.ShouldCheck(0, 1));
        Assert.False(UpdatePolicy.ShouldCheck(1_000, 1_000 + 86_399_999));
        Assert.True(UpdatePolicy.ShouldCheck(1_000, 1_000 + 86_400_000));
    }

    [Fact]
    public void ShouldCheck_when_clock_moved_backwards()
    {
        Assert.True(UpdatePolicy.ShouldCheck(5_000_000, 1_000));
    }

    [Fact]
    public void ShouldShow_hides_dismissed_version_only()
    {
        Assert.False(UpdatePolicy.ShouldShow("v1.1.0", "1.0.0", "v1.1.0"));
        Assert.True(UpdatePolicy.ShouldShow("v1.2.0", "1.0.0", "v1.1.0"));
    }

    // --- LatestReleaseParser ---

    [Fact]
    public void Parse_reads_tag_name_and_html_url()
    {
        const string json = """{"tag_name":"v1.2.0","html_url":"https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0","name":"x"}""";

        Assert.Equal(
            new LatestRelease("v1.2.0", "https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0"),
            LatestReleaseParser.Parse(json));
    }

    [Theory]
    [InlineData("""{"message":"API rate limit exceeded"}""")]
    [InlineData("<html>login</html>")]
    [InlineData("")]
    [InlineData("""{"tag_name":"v1.2.0"}""")]
    [InlineData("""[1,2]""")]
    [InlineData("""{"tag_name":5,"html_url":"https://x"}""")]
    public void Parse_returns_null_for_missing_fields_non_json_or_rate_limit_body(string json)
    {
        Assert.Null(LatestReleaseParser.Parse(json));
    }

    [Fact]
    public void Parse_rejects_non_https_url()
    {
        Assert.Null(LatestReleaseParser.Parse("""{"tag_name":"v1.2.0","html_url":"http://evil/x"}"""));
    }

    // --- UpdateCoordinator ---

    [Fact]
    public async Task Refresh_fetches_when_due_and_returns_newer_release()
    {
        var store = new FakeStore();
        var coordinator = new UpdateCoordinator(store, new FakeFetcher(() => V110), "1.0.0", () => Now);

        Assert.Equal(V110, await coordinator.RefreshAsync());
        Assert.Equal(Now, store.Info.LastCheckedAtMillis);
    }

    [Fact]
    public async Task Refresh_skips_fetch_within_interval_and_uses_cached_result()
    {
        var store = new FakeStore { Info = new StoredUpdateInfo(Now - 1000, "v1.1.0", "u", null) };
        var fetcher = new FakeFetcher(() => throw new InvalidOperationException("must not fetch"));
        var coordinator = new UpdateCoordinator(store, fetcher, "1.0.0", () => Now);

        Assert.Equal(new LatestRelease("v1.1.0", "u"), await coordinator.RefreshAsync());
        Assert.Equal(0, fetcher.Calls);
    }

    [Fact]
    public async Task Refresh_on_fetch_failure_keeps_cache_and_does_not_advance_last_checked()
    {
        var cached = new StoredUpdateInfo(Now - 90_000_000, "v1.1.0", "u", null);
        var store = new FakeStore { Info = cached };
        var coordinator = new UpdateCoordinator(store, new FakeFetcher(() => null), "1.0.0", () => Now);

        Assert.Equal(new LatestRelease("v1.1.0", "u"), await coordinator.RefreshAsync());
        Assert.Equal(cached, store.Info);
    }

    [Fact]
    public async Task Refresh_survives_a_throwing_fetcher()
    {
        var store = new FakeStore();
        var coordinator = new UpdateCoordinator(store, new FakeFetcher(() => throw new HttpRequestException("offline")), "1.0.0", () => Now);

        Assert.Null(await coordinator.RefreshAsync());
        Assert.Equal(0L, store.Info.LastCheckedAtMillis);
    }

    [Fact]
    public async Task Refresh_returns_null_when_cached_tag_equals_installed_version()
    {
        var store = new FakeStore { Info = new StoredUpdateInfo(Now - 1000, "v1.1.0", "u", null) };
        var coordinator = new UpdateCoordinator(store, new FakeFetcher(() => null), "1.1.0", () => Now);

        Assert.Null(await coordinator.RefreshAsync());
    }

    [Fact]
    public async Task Refresh_never_fetches_for_dev_version()
    {
        var fetcher = new FakeFetcher(() => V110);
        var coordinator = new UpdateCoordinator(new FakeStore(), fetcher, "0.0.0-dev", () => Now);

        Assert.Null(await coordinator.RefreshAsync());
        Assert.Equal(0, fetcher.Calls);
    }

    [Fact]
    public async Task Dismiss_hides_that_version_and_a_newer_one_shows_again()
    {
        var store = new FakeStore();
        var clock = Now;
        var fetcher = new FakeFetcher(() => V110);
        var coordinator = new UpdateCoordinator(store, fetcher, "1.0.0", () => clock);

        Assert.Equal(V110, await coordinator.RefreshAsync());
        coordinator.Dismiss("v1.1.0");
        Assert.Null(await coordinator.RefreshAsync());

        var v120 = new LatestRelease("v1.2.0", "https://x/1.2.0");
        fetcher.Result = () => v120;
        clock += UpdatePolicy.CheckIntervalMillis;
        Assert.Equal(v120, await coordinator.RefreshAsync());
    }
}
