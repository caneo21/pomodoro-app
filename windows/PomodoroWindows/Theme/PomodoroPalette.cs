using PomodoroWindows.Core;
using Windows.UI;

namespace PomodoroWindows.Theme;

public readonly record struct PomodoroPalette(
    Color Accent,
    Color OnAccent,
    Color Background,
    Color Surface,
    Color TextPrimary,
    Color TextSecondary,
    Color Border,
    Color LongBreakAccent);

public static class Palettes
{
    private static Color Rgb(byte r, byte g, byte b) => Color.FromArgb(255, r, g, b);

    // Minimal-monochrome tokens (docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md,
    // Section A). Unlike the watch, PC keeps both light and dark mode, so both neutral columns
    // apply here. Background/Surface/TextPrimary/TextSecondary/Border/LongBreakAccent are
    // identical across all 4 themes within a mode — only Accent/OnAccent vary, which is the
    // whole point of moving from a 2-tone (Primary + OnPrimary) palette to a single accent.
    private static readonly Color DarkBackground = Rgb(0x0D, 0x0D, 0x0F);
    private static readonly Color DarkSurface = Rgb(0x17, 0x17, 0x1A);
    private static readonly Color DarkTextPrimary = Rgb(0xF2, 0xF2, 0xF4);
    private static readonly Color DarkTextSecondary = Rgb(0x8A, 0x8A, 0x92);
    private static readonly Color DarkBorder = Rgb(0x2A, 0x2A, 0x2E);
    private static readonly Color DarkLongBreakAccent = Rgb(0x6B, 0x7B, 0xFF);

    private static readonly Color LightBackground = Rgb(0xFA, 0xFA, 0xF9);
    private static readonly Color LightSurface = Rgb(0xFF, 0xFF, 0xFF);
    private static readonly Color LightTextPrimary = Rgb(0x17, 0x17, 0x1A);
    private static readonly Color LightTextSecondary = Rgb(0x6B, 0x6B, 0x72);
    private static readonly Color LightBorder = Rgb(0xE4, 0xE4, 0xE7);
    private static readonly Color LightLongBreakAccent = Rgb(0x4A, 0x57, 0xC7);

    private static readonly Dictionary<ColorTheme, PomodoroPalette> Dark = new()
    {
        [ColorTheme.Default] = new PomodoroPalette(Rgb(0xFF, 0x6B, 0x57), Rgb(0xFF, 0xFF, 0xFF), DarkBackground, DarkSurface, DarkTextPrimary, DarkTextSecondary, DarkBorder, DarkLongBreakAccent),
        [ColorTheme.Ocean] = new PomodoroPalette(Rgb(0x5A, 0xA9, 0xFF), Rgb(0x00, 0x26, 0x3D), DarkBackground, DarkSurface, DarkTextPrimary, DarkTextSecondary, DarkBorder, DarkLongBreakAccent),
        [ColorTheme.Forest] = new PomodoroPalette(Rgb(0x6F, 0xCB, 0x7A), Rgb(0x0B, 0x3B, 0x12), DarkBackground, DarkSurface, DarkTextPrimary, DarkTextSecondary, DarkBorder, DarkLongBreakAccent),
        [ColorTheme.Lavender] = new PomodoroPalette(Rgb(0xB0, 0x8C, 0xF0), Rgb(0x2C, 0x0F, 0x5C), DarkBackground, DarkSurface, DarkTextPrimary, DarkTextSecondary, DarkBorder, DarkLongBreakAccent),
    };

    private static readonly Dictionary<ColorTheme, PomodoroPalette> Light = new()
    {
        [ColorTheme.Default] = new PomodoroPalette(Rgb(0xE1, 0x4B, 0x34), Rgb(0xFF, 0xFF, 0xFF), LightBackground, LightSurface, LightTextPrimary, LightTextSecondary, LightBorder, LightLongBreakAccent),
        [ColorTheme.Ocean] = new PomodoroPalette(Rgb(0x1E, 0x76, 0xE0), Rgb(0xFF, 0xFF, 0xFF), LightBackground, LightSurface, LightTextPrimary, LightTextSecondary, LightBorder, LightLongBreakAccent),
        [ColorTheme.Forest] = new PomodoroPalette(Rgb(0x2E, 0x8F, 0x45), Rgb(0xFF, 0xFF, 0xFF), LightBackground, LightSurface, LightTextPrimary, LightTextSecondary, LightBorder, LightLongBreakAccent),
        [ColorTheme.Lavender] = new PomodoroPalette(Rgb(0x7C, 0x4F, 0xD6), Rgb(0xFF, 0xFF, 0xFF), LightBackground, LightSurface, LightTextPrimary, LightTextSecondary, LightBorder, LightLongBreakAccent),
    };

    public static PomodoroPalette PaletteFor(ColorTheme theme, bool darkTheme) =>
        (darkTheme ? Dark : Light)[theme];
}
