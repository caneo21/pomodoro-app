using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

/// <summary>Asks GitHub for the latest published release. Any failure (offline, 403, 404, bad body) is null.</summary>
public sealed class GitHubReleaseFetcher : IReleaseFetcher
{
    public const string LatestReleaseUrl = "https://api.github.com/repos/caneo21/pomodoro-app/releases/latest";

    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("PomodoroApp-UpdateCheck");
        client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        return client;
    }

    public async Task<LatestRelease?> FetchLatestAsync()
    {
        try
        {
            using var response = await Client.GetAsync(LatestReleaseUrl);
            if (!response.IsSuccessStatusCode) return null;
            return LatestReleaseParser.Parse(await response.Content.ReadAsStringAsync());
        }
        catch (Exception)
        {
            return null;
        }
    }
}
