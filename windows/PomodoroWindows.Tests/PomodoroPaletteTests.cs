using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using Windows.UI;

namespace PomodoroWindows.Tests;

public class PomodoroPaletteTests
{
    private static Color Rgb(byte r, byte g, byte b) => Color.FromArgb(255, r, g, b);

    [Fact]
    public void Dark_DEFAULT_matches_the_monochrome_token_values()
    {
        var palette = Palettes.PaletteFor(ColorTheme.Default, darkTheme: true);

        Assert.Equal(Rgb(0xFF, 0x6B, 0x57), palette.Accent);
        Assert.Equal(Rgb(0xFF, 0xFF, 0xFF), palette.OnAccent);
        Assert.Equal(Rgb(0x0D, 0x0D, 0x0F), palette.Background);
        Assert.Equal(Rgb(0x17, 0x17, 0x1A), palette.Surface);
        Assert.Equal(Rgb(0xF2, 0xF2, 0xF4), palette.TextPrimary);
        Assert.Equal(Rgb(0x8A, 0x8A, 0x92), palette.TextSecondary);
        Assert.Equal(Rgb(0x2A, 0x2A, 0x2E), palette.Border);
        Assert.Equal(Rgb(0x6B, 0x7B, 0xFF), palette.LongBreakAccent);
    }

    [Fact]
    public void Light_DEFAULT_matches_the_monochrome_token_values()
    {
        var palette = Palettes.PaletteFor(ColorTheme.Default, darkTheme: false);

        Assert.Equal(Rgb(0xE1, 0x4B, 0x34), palette.Accent);
        Assert.Equal(Rgb(0xFF, 0xFF, 0xFF), palette.OnAccent);
        Assert.Equal(Rgb(0xFA, 0xFA, 0xF9), palette.Background);
        Assert.Equal(Rgb(0xFF, 0xFF, 0xFF), palette.Surface);
        Assert.Equal(Rgb(0x17, 0x17, 0x1A), palette.TextPrimary);
        Assert.Equal(Rgb(0x6B, 0x6B, 0x72), palette.TextSecondary);
        Assert.Equal(Rgb(0xE4, 0xE4, 0xE7), palette.Border);
        Assert.Equal(Rgb(0x4A, 0x57, 0xC7), palette.LongBreakAccent);
    }

    [Fact]
    public void Every_theme_has_a_distinct_accent_within_dark_and_within_light()
    {
        var darkAccents = Enum.GetValues<ColorTheme>().Select(t => Palettes.PaletteFor(t, darkTheme: true).Accent);
        var lightAccents = Enum.GetValues<ColorTheme>().Select(t => Palettes.PaletteFor(t, darkTheme: false).Accent);

        Assert.Equal(Enum.GetValues<ColorTheme>().Length, darkAccents.Distinct().Count());
        Assert.Equal(Enum.GetValues<ColorTheme>().Length, lightAccents.Distinct().Count());
    }

    [Fact]
    public void Every_theme_shares_the_same_neutrals_and_long_break_accent_within_a_mode()
    {
        var darkPalettes = Enum.GetValues<ColorTheme>().Select(t => Palettes.PaletteFor(t, darkTheme: true)).ToList();

        Assert.Single(darkPalettes.Select(p => p.Background).Distinct());
        Assert.Single(darkPalettes.Select(p => p.Surface).Distinct());
        Assert.Single(darkPalettes.Select(p => p.TextPrimary).Distinct());
        Assert.Single(darkPalettes.Select(p => p.TextSecondary).Distinct());
        Assert.Single(darkPalettes.Select(p => p.Border).Distinct());
        Assert.Single(darkPalettes.Select(p => p.LongBreakAccent).Distinct());
    }
}
