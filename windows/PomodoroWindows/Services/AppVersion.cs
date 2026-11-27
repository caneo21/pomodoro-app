using Windows.ApplicationModel;

namespace PomodoroWindows.Services;

internal static class AppVersion
{
    /// <summary>The installed package version as "Major.Minor.Build" (e.g. "1.2.0"), or null when unpackaged.</summary>
    public static string? Current
    {
        get
        {
            try
            {
                var v = Package.Current.Id.Version;
                return $"{v.Major}.{v.Minor}.{v.Build}";
            }
            catch (Exception)
            {
                return null;
            }
        }
    }
}
