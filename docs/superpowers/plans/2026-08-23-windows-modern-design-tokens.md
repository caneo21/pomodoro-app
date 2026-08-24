# Windows Modern Design Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the shared minimal-monochrome design tokens (color, typography, shape, motion) to the WinUI 3 Windows app's Timer and Settings screens — the third and last of three platforms to receive the redesign (phone and watch already restyled this session).

**Architecture:** This is a token/styling pass over existing files — `Theme/PomodoroPalette.cs`, `App.xaml`, `MainWindow.xaml`, `Views/TimerPage.xaml(.cs)`, `Views/SettingsPage.xaml(.cs)` — no new screens, no changes to `TimerEngine`, `TimerViewModel`, `SettingsViewModel`, or any settings/sync/notification code. `PomodoroPalette` collapses from a 2-tone (`Primary`+`OnPrimary`) per-theme shape to a 1-tone (`Accent`+`OnAccent`) shape plus shared neutrals (`Background`/`Surface`/`TextPrimary`/`TextSecondary`/`Border`), the long-break accent becomes one shared value instead of 4 per-theme values, a bundled variable font (Manrope) renders the countdown digits only, the progress ring is replaced with a small hand-drawn custom control (`TimerProgressRing`) since WinUI 3's stock `ProgressRing` has no stroke-thickness property, and progress/color changes animate via a small reusable spring-physics helper (`Spring1D`) added to the portable `PomodoroWindows.Core` library, driven every frame by `CompositionTarget.Rendering`.

**Tech Stack:** C#, WinUI 3 (Windows App SDK 1.8), .NET 8, xunit (`PomodoroWindows.Core.Tests` for pure-logic tests, `PomodoroWindows.Tests` for palette tests that need `Windows.UI.Color`).

**Spec:** `docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md`

## Global Constraints

- Timer countdown digits (`MM:SS`) use **Manrope**, weight 800, tabular figures — everywhere else keeps Segoe UI Variable (the WinUI default; no change needed).
- Neutrals — PC keeps both light and dark mode (system-following, unchanged): `background` dark `#0D0D0F` / light `#FAFAF9`; `surface` dark `#17171A` / light `#FFFFFF`; `textPrimary` dark `#F2F2F4` / light `#17171A`; `textSecondary` dark `#8A8A92` / light `#6B6B72`; `border` dark `#2A2A2E` / light `#E4E4E7`.
- Per-theme accent (dark-bg / light-bg): Default `#FF6B57` / `#E14B34`; Ocean `#5AA9FF` / `#1E76E0`; Forest `#6FCB7A` / `#2E8F45`; Lavender `#B08CF0` / `#7C4FD6`.
- `OnAccent` (text/icon color placed on top of the accent) reuses this codebase's existing values, since the new accents are near-identical in hue/lightness to the old ones they replace: dark-bg `OnAccent` is `Palettes.Dark[theme].OnPrimary` as it exists today (Default `#FFFFFF`, Ocean `#00263D`, Forest `#0B3B12`, Lavender `#2C0F5C`); light-bg `OnAccent` is white for all 4 themes, exactly as `Palettes.Light[theme].OnPrimary` already is today.
- Long-break accent is **one shared value** across all 4 themes: dark-bg `#6B7BFF`, light-bg `#4A57C7` (was 4 different per-theme values in each of `Light`/`Dark`).
- Shape: card/section corner radius grows from 16 to 24; pill buttons and chips move to a corner radius (999) that's always at least half the control's height regardless of exact padding, guaranteeing a true pill/circle with "no partially-rounded rectangles" rather than the previous fixed `20`, which was a hair short of a true pill at the button's actual rendered height; the progress ring becomes a custom-drawn control, 280px diameter (up from 240px) with a 10px stroke (thinner and more open than the stock `ProgressRing`'s look at that size).
- Motion is spring-based, not linear — applies to the ring's progress value, its color when the phase changes, and a small scale-down (~0.94, springing back on release) on button press. This plan's `Spring1D` stiffness/damping constants are this platform's implementation detail (the spec fixes character, not numbers): progress uses a bouncier spring (`stiffness=90, damping=12`), color-mix uses a stiffer, non-bouncy spring (`stiffness=140, damping=24`); button press uses a native Composition spring animation (`DampingRatio=0.5, Period=150ms`) targeting the button's `Scale`, since that's a Visual-layer transform rather than a value this plan recomputes into geometry each frame.
- No changes to `TimerEngine`, `TimerViewModel`, `SettingsViewModel`, `ISettingsStore`/`ApplicationDataSettingsStore`, or any notification/tray/keep-awake code — this plan is presentation-layer only.

---

## Task 1: Bundle the Manrope variable font resource

**Files:**
- Create: `windows/PomodoroWindows/Assets/Fonts/Manrope-Variable.ttf` (binary, downloaded — not written as text)
- Test: none (a font resource has no unit-testable behavior); verified by a successful build

**Interfaces:**
- Produces: the app resource reachable at XAML URI `ms-appx:///Assets/Fonts/Manrope-Variable.ttf#Manrope`, consumed by Task 6.

Google Fonts ships Manrope only as a single variable font (no static per-weight files) — confirmed by listing `github.com/google/fonts/tree/main/ofl/manrope` this session, which contains only `Manrope[wght].ttf` alongside license/metadata files. WinUI 3's text stack (DirectWrite/DWriteCore) resolves a requested `FontWeight` against a variable font's `wght` axis automatically — the same mechanism that already makes "Segoe UI Variable" (itself a variable font) respond correctly to different weights in this app today — so no XAML-side variation-axis API is needed, unlike Compose's `FontVariation.Settings`. The file is renamed from `Manrope[wght].ttf` to `Manrope-Variable.ttf` on download because `[`/`]` are reserved URI characters and would need percent-encoding (`%5B`/`%5D`) if left in the filename referenced from a XAML `ms-appx:///` URI — renaming avoids that fragility entirely, matching the same reasoning the watch rollout used to rename this same upstream file for Android resource-name rules.

- [ ] **Step 1: Create the font asset directory**

```bash
mkdir -p windows/PomodoroWindows/Assets/Fonts
```

- [ ] **Step 2: Download the Manrope variable font**

```bash
curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf" -o windows/PomodoroWindows/Assets/Fonts/Manrope-Variable.ttf
```

- [ ] **Step 3: Verify the download**

```bash
ls -la windows/PomodoroWindows/Assets/Fonts/Manrope-Variable.ttf
```

Expected: a file of roughly 165KB (the wear rollout fetched the identical upstream file at 165,420 bytes this session). If the file is 0 bytes or a few KB of HTML, the download failed — check network access and retry.

- [ ] **Step 4: Verify the project still builds with the new asset present**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: `Build succeeded`. This project has no explicit `<Content Include>`/`<Page Include>` items in its `.csproj` (confirmed by reading it this session) — it's an SDK-style project, so files placed under `Assets/` are picked up the same implicit way `Assets/AppIcon.ico` and `Assets/Square44x44Logo.png` already are; no `.csproj` edit is needed for the font file to ship in the MSIX package.

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows/Assets/Fonts/Manrope-Variable.ttf
git commit -m "feat(windows): bundle Manrope variable font for timer digits"
```

---

## Task 2: Add a reusable `Spring1D` physics helper to `PomodoroWindows.Core`

**Files:**
- Create: `windows/PomodoroWindows.Core/Spring1D.cs`
- Test: `windows/PomodoroWindows.Core.Tests/Spring1DTests.cs`

**Interfaces:**
- Produces: `public sealed class Spring1D { Spring1D(double initialValue, double stiffness, double damping); double Value { get; } double Target { get; } void SetTarget(double target); void Reset(double value); bool IsSettled(double tolerance = 0.001); double Step(double deltaSeconds); }`. Consumed by Task 6 (one instance drives the ring's progress fraction, a second drives its color-mix ratio).

This is plain math with no WinUI/WinRT dependency, so it lives in the portable `PomodoroWindows.Core` library (same place as `TimerEngine`) and is unit-testable without a UI thread. It integrates a damped harmonic oscillator (`acceleration = stiffness * (target - value) - damping * velocity`) using semi-implicit Euler, the same physical model WinUI's own `SpringVector3NaturalMotionAnimation`/Compose's `spring()` are built on — this plan implements it directly instead of relying on either platform API, because both animate a UI-thread `Visual`/`State` property, not an arbitrary geometry value recomputed into hand-drawn `Path` data every frame the way `TimerProgressRing` (Task 5) needs.

- [ ] **Step 1: Write the failing tests**

Create `windows/PomodoroWindows.Core.Tests/Spring1DTests.cs`:

```csharp
namespace PomodoroWindows.Core.Tests;

public class Spring1DTests
{
    private const double FrameSeconds = 1.0 / 60.0;

    [Fact]
    public void New_spring_starts_at_the_initial_value_with_no_target_gap()
    {
        var spring = new Spring1D(initialValue: 0.25, stiffness: 90, damping: 12);

        Assert.Equal(0.25, spring.Value);
        Assert.Equal(0.25, spring.Target);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void A_single_step_moves_the_value_a_small_amount_toward_the_target()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 12);
        spring.SetTarget(1.0);

        spring.Step(FrameSeconds);

        // One 1/60s frame of a spring this stiff moves only a small fraction of the way — this
        // pins down "gradual, not instant" without depending on exactly when it fully settles.
        Assert.True(spring.Value > 0.0);
        Assert.True(spring.Value < 0.5);
    }

    [Fact]
    public void An_overdamped_spring_never_overshoots_its_target()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 120, damping: 60);
        spring.SetTarget(1.0);

        var maxValue = 0.0;
        for (var i = 0; i < 600; i++)
        {
            spring.Step(FrameSeconds);
            maxValue = Math.Max(maxValue, spring.Value);
        }

        Assert.True(maxValue <= 1.01);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void An_underdamped_spring_overshoots_before_settling()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 4);
        spring.SetTarget(1.0);

        var maxValue = 0.0;
        for (var i = 0; i < 600; i++)
        {
            spring.Step(FrameSeconds);
            maxValue = Math.Max(maxValue, spring.Value);
        }

        Assert.True(maxValue > 1.02);
        Assert.True(spring.IsSettled());
    }

    [Fact]
    public void Reset_snaps_the_value_and_target_together_and_clears_velocity()
    {
        var spring = new Spring1D(initialValue: 0.0, stiffness: 90, damping: 12);
        spring.SetTarget(1.0);
        for (var i = 0; i < 30; i++) spring.Step(FrameSeconds);

        spring.Reset(0.4);

        Assert.Equal(0.4, spring.Value);
        Assert.Equal(0.4, spring.Target);
        Assert.True(spring.IsSettled());
    }
}
```

These assert qualitative spring behavior (motion is gradual not instant, settles, overdamped never overshoots, underdamped does) rather than hardcoded intermediate float values, since the exact trajectory depends on the integration scheme's rounding — the properties being tested are the ones that actually matter for how this feels on screen.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
dotnet test windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj --filter Spring1DTests
```

Expected: **compile failure** — `error CS0246: The type or namespace name 'Spring1D' could not be found`. This is the correct RED state: the type doesn't exist yet.

- [ ] **Step 3: Implement `Spring1D`**

Create `windows/PomodoroWindows.Core/Spring1D.cs`:

```csharp
namespace PomodoroWindows.Core;

/// <summary>
/// A 1-dimensional damped-harmonic-oscillator spring, stepped manually once per rendered
/// frame. Used to give ring progress and ring color transitions a warm, physical settle
/// instead of snapping instantly to a new target.
/// </summary>
public sealed class Spring1D
{
    private readonly double _stiffness;
    private readonly double _damping;
    private double _velocity;

    public double Value { get; private set; }
    public double Target { get; private set; }

    public Spring1D(double initialValue, double stiffness, double damping)
    {
        Value = initialValue;
        Target = initialValue;
        _stiffness = stiffness;
        _damping = damping;
    }

    public void SetTarget(double target) => Target = target;

    public void Reset(double value)
    {
        Value = value;
        Target = value;
        _velocity = 0.0;
    }

    public bool IsSettled(double tolerance = 0.001) =>
        Math.Abs(Target - Value) < tolerance && Math.Abs(_velocity) < tolerance;

    public double Step(double deltaSeconds)
    {
        var displacement = Target - Value;
        var acceleration = _stiffness * displacement - _damping * _velocity;
        _velocity += acceleration * deltaSeconds;
        Value += _velocity * deltaSeconds;
        return Value;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
dotnet test windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj --filter Spring1DTests
```

Expected: all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows.Core/Spring1D.cs windows/PomodoroWindows.Core.Tests/Spring1DTests.cs
git commit -m "feat(windows): add Spring1D damped-oscillator helper for ring motion"
```

---

## Task 3: Redefine `PomodoroPalette` to the monochrome token set

**Files:**
- Modify: `windows/PomodoroWindows/Theme/PomodoroPalette.cs`
- Test: `windows/PomodoroWindows.Tests/PomodoroPaletteTests.cs` (new)

**Interfaces:**
- Produces: `readonly record struct PomodoroPalette(Color Accent, Color OnAccent, Color Background, Color Surface, Color TextPrimary, Color TextSecondary, Color Border, Color LongBreakAccent)` and `static PomodoroPalette Palettes.PaletteFor(ColorTheme theme, bool darkTheme)` — signature of `PaletteFor` is unchanged, only the record's fields change. Consumed by Task 6 (`TimerPage`) and Task 7 (`SettingsPage`).
- Consumes: `ColorTheme` from `PomodoroWindows.Core` (unchanged).

- [ ] **Step 1: Write the failing tests**

Create `windows/PomodoroWindows.Tests/PomodoroPaletteTests.cs`:

```csharp
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
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
dotnet test windows/PomodoroWindows.Tests/PomodoroWindows.Tests.csproj --filter PomodoroPaletteTests
```

Expected: **compile failure** — `error CS1061` / `error CS7036` on `palette.Surface`, `palette.TextPrimary`, etc. (the current `PomodoroPalette` only has `Primary`, `OnPrimary`, `Background`, `LongBreakAccent`). Correct RED state.

- [ ] **Step 3: Replace `PomodoroPalette.cs`**

Replace the full contents of `windows/PomodoroWindows/Theme/PomodoroPalette.cs`:

```csharp
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
```

The `OnAccent` value in each row is copied verbatim from this file's own previous `Dark`/`Light` `OnPrimary` columns (see Global Constraints) — not a new color decision, just carried forward since the new accents are close enough in hue/lightness that the old contrast pairing still holds.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
dotnet test windows/PomodoroWindows.Tests/PomodoroWindows.Tests.csproj --filter PomodoroPaletteTests
```

Expected: all 4 tests pass.

- [ ] **Step 5: Confirm the rest of the solution's expected breakage**

`TimerPage.xaml.cs` and `Views/SettingsPage.xaml.cs` still reference the old `palette.Primary`/`palette.OnPrimary` fields, which no longer exist — this is expected and gets fixed in Tasks 6–7, not here.

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: **build FAILS** with `error CS1061` on `palette.Primary` in `TimerPage.xaml.cs` and `error CS1061` on `palette.Primary` in `SettingsPage.xaml.cs`. This is the correct, expected state between Task 3 and Tasks 6–7 — do not attempt to fix it here.

- [ ] **Step 6: Commit**

```bash
git add windows/PomodoroWindows/Theme/PomodoroPalette.cs windows/PomodoroWindows.Tests/PomodoroPaletteTests.cs
git commit -m "feat(windows): collapse PomodoroPalette to a single monochrome accent plus shared neutrals"
```

---

## Task 4: Shape tokens — rounder cards and a fully-pill mode toggle

**Files:**
- Modify: `windows/PomodoroWindows/App.xaml`
- Modify: `windows/PomodoroWindows/MainWindow.xaml`

**Interfaces:**
- Consumes: nothing new.
- Produces: no new symbols — same `CardCornerRadius`/`PillCornerRadius`/`ChipCornerRadius` resource keys and style keys (`SurfaceCardStyle`, `ChipBorderStyle`, `SectionHeaderTextStyle`, `PillButtonBaseStyle`, `AccentPillButtonStyle`, `SubtlePillButtonStyle`) used unchanged by Tasks 6–7.

- [ ] **Step 1: Grow the card corner radius, and make pill buttons a guaranteed true pill, in `App.xaml`**

In `windows/PomodoroWindows/App.xaml`, change:

```xml
            <CornerRadius x:Key="CardCornerRadius">16</CornerRadius>
            <CornerRadius x:Key="PillCornerRadius">20</CornerRadius>
            <CornerRadius x:Key="ChipCornerRadius">999</CornerRadius>
```

to:

```xml
            <CornerRadius x:Key="CardCornerRadius">24</CornerRadius>
            <CornerRadius x:Key="PillCornerRadius">999</CornerRadius>
            <CornerRadius x:Key="ChipCornerRadius">999</CornerRadius>
```

A `CornerRadius` larger than half an element's rendered height/width simply clips to a perfect pill/circle in XAML — it doesn't overflow or distort — so `999` is a safe "always fully round regardless of exact padding" value rather than a number that has to be kept in sync with the button's actual height. The previous fixed `20` was a hair short of a true pill at these buttons' actual rendered height (padding `28,12` plus font line height puts them close to 44px tall, whose true pill radius is 22), which is exactly the "partially-rounded rectangle" look the spec rules out.

- [ ] **Step 2: Make the mini-mode toggle button a full pill**

In `windows/PomodoroWindows/MainWindow.xaml`, change:

```xml
            <Button x:Name="ModeToggleButton" Grid.Column="3" Content="미니" Click="ModeToggleButton_Click"
                    VerticalAlignment="Center" Margin="0,0,4,0" Padding="10,4" FontSize="12" CornerRadius="6"/>
```

to:

```xml
            <Button x:Name="ModeToggleButton" Grid.Column="3" Content="미니" Click="ModeToggleButton_Click"
                    VerticalAlignment="Center" Margin="0,0,4,0" Padding="12,4" FontSize="12" CornerRadius="999"/>
```

(Padding widened slightly from `10,4` to `12,4` so the fully round corners don't visually clip the label's side padding at this small size.)

- [ ] **Step 3: Build to confirm no regressions**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: same pre-existing failures as Task 3 Step 5 (`palette.Primary` in `TimerPage.xaml.cs`/`SettingsPage.xaml.cs`), nothing new — XAML resource/property edits don't introduce compile errors of their own kind.

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows/App.xaml windows/PomodoroWindows/MainWindow.xaml
git commit -m "feat(windows): grow card corner radius and make the mode toggle a full pill"
```

---

## Task 5: `TimerProgressRing` — a hand-drawn ring control with a controllable stroke

**Files:**
- Create: `windows/PomodoroWindows/Controls/TimerProgressRing.xaml`
- Create: `windows/PomodoroWindows/Controls/TimerProgressRing.xaml.cs`

**Interfaces:**
- Produces: `public sealed partial class TimerProgressRing : UserControl` with dependency properties `Diameter: double`, `RingThickness: double`, `Progress: double` (0.0–1.0), `RingBrush: Brush?`, `TrackBrush: Brush?`. Consumed by Task 6, which replaces `TimerPage.xaml`'s `<ProgressRing>` with this control and drives `Progress`/`RingBrush` every frame.

WinUI 3's stock `Microsoft.UI.Xaml.Controls.ProgressRing` has no stroke-thickness property — confirmed via `microsoft/microsoft-ui-xaml` issue #3293 ("Proposal: Customizing ProgressRing by exposing thickness property"), still open, and Microsoft Learn's own guidance pointing to the (now-deprecated) Community Toolkit `RadialProgressBar` as the only built-in alternative with a `Thickness` property. Rather than take on a Community Toolkit dependency or retemplate the built-in control against Microsoft's internal `generic.xaml` (which changes across Windows App SDK versions), this control draws the ring itself: a full-circle `Ellipse` track plus a `Path`/`ArcSegment` progress arc, both sized from `Diameter`/`RingThickness` directly.

- [ ] **Step 1: Create the control's XAML**

Create `windows/PomodoroWindows/Controls/TimerProgressRing.xaml`:

```xml
<UserControl
    x:Class="PomodoroWindows.Controls.TimerProgressRing"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Grid x:Name="RootGrid">
        <Ellipse x:Name="TrackEllipse"/>
        <Path x:Name="ProgressPath" StrokeLineJoin="Round" StrokeStartLineCap="Round" StrokeEndLineCap="Round"/>
    </Grid>
</UserControl>
```

- [ ] **Step 2: Implement the code-behind**

Create `windows/PomodoroWindows/Controls/TimerProgressRing.xaml.cs`:

```csharp
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;

namespace PomodoroWindows.Controls;

public sealed partial class TimerProgressRing : UserControl
{
    public static readonly DependencyProperty DiameterProperty = DependencyProperty.Register(
        nameof(Diameter), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(240.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty RingThicknessProperty = DependencyProperty.Register(
        nameof(RingThickness), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(8.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty ProgressProperty = DependencyProperty.Register(
        nameof(Progress), typeof(double), typeof(TimerProgressRing),
        new PropertyMetadata(0.0, OnLayoutPropertyChanged));

    public static readonly DependencyProperty RingBrushProperty = DependencyProperty.Register(
        nameof(RingBrush), typeof(Brush), typeof(TimerProgressRing),
        new PropertyMetadata(null, OnBrushPropertyChanged));

    public static readonly DependencyProperty TrackBrushProperty = DependencyProperty.Register(
        nameof(TrackBrush), typeof(Brush), typeof(TimerProgressRing),
        new PropertyMetadata(null, OnBrushPropertyChanged));

    public double Diameter
    {
        get => (double)GetValue(DiameterProperty);
        set => SetValue(DiameterProperty, value);
    }

    public double RingThickness
    {
        get => (double)GetValue(RingThicknessProperty);
        set => SetValue(RingThicknessProperty, value);
    }

    public double Progress
    {
        get => (double)GetValue(ProgressProperty);
        set => SetValue(ProgressProperty, value);
    }

    public Brush? RingBrush
    {
        get => (Brush?)GetValue(RingBrushProperty);
        set => SetValue(RingBrushProperty, value);
    }

    public Brush? TrackBrush
    {
        get => (Brush?)GetValue(TrackBrushProperty);
        set => SetValue(TrackBrushProperty, value);
    }

    public TimerProgressRing()
    {
        InitializeComponent();
        UpdateBrushes();
        UpdateGeometry();
    }

    private static void OnLayoutPropertyChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((TimerProgressRing)d).UpdateGeometry();

    private static void OnBrushPropertyChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((TimerProgressRing)d).UpdateBrushes();

    private void UpdateBrushes()
    {
        TrackEllipse.Stroke = TrackBrush;
        ProgressPath.Stroke = RingBrush;
    }

    private void UpdateGeometry()
    {
        RootGrid.Width = Diameter;
        RootGrid.Height = Diameter;

        var inset = Math.Max(Diameter - RingThickness, 0);
        TrackEllipse.Width = inset;
        TrackEllipse.Height = inset;
        TrackEllipse.StrokeThickness = RingThickness;

        ProgressPath.StrokeThickness = RingThickness;
        ProgressPath.Data = BuildArcGeometry(Diameter, RingThickness, Progress);
    }

    private static PathGeometry? BuildArcGeometry(double diameter, double thickness, double progress)
    {
        if (progress <= 0.0005) return null;

        var radius = (diameter - thickness) / 2.0;
        var center = new Point(diameter / 2.0, diameter / 2.0);
        // A single ArcSegment degenerates when its start and end point coincide, which is
        // exactly what a mathematically-complete 360-degree sweep would do — clamping just
        // under a full turn keeps the arc well-defined with a visually imperceptible gap.
        var sweepAngle = Math.Min(progress, 0.9995) * 360.0;

        var startPoint = PointOnCircle(center, radius, -90.0);
        var endPoint = PointOnCircle(center, radius, -90.0 + sweepAngle);

        var arcSegment = new ArcSegment
        {
            Point = endPoint,
            Size = new Size(radius, radius),
            SweepDirection = SweepDirection.Clockwise,
            IsLargeArc = sweepAngle > 180.0
        };

        var figure = new PathFigure { StartPoint = startPoint, IsClosed = false };
        figure.Segments.Add(arcSegment);

        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return geometry;
    }

    private static Point PointOnCircle(Point center, double radius, double angleDegrees)
    {
        var radians = angleDegrees * Math.PI / 180.0;
        return new Point(
            center.X + radius * Math.Cos(radians),
            center.Y + radius * Math.Sin(radians));
    }
}
```

The arc starts at the top (`-90°`, i.e. 12 o'clock) and sweeps clockwise, matching the direction the stock `ProgressRing` drew in today. `StrokeStartLineCap`/`StrokeEndLineCap="Round"` on the `Path` gives the progress arc's leading/trailing tips a soft rounded cap rather than a hard-cut edge, matching the spec's "warm" direction.

- [ ] **Step 3: Build to confirm the new control compiles**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: the pre-existing `palette.Primary` failures from Task 3 still show (unrelated, fixed in Task 6), but no new errors from `TimerProgressRing.xaml`/`.xaml.cs` — confirms the control itself compiles and its XAML markup is valid before it's wired into `TimerPage` next.

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows/Controls/TimerProgressRing.xaml windows/PomodoroWindows/Controls/TimerProgressRing.xaml.cs
git commit -m "feat(windows): add a hand-drawn TimerProgressRing with a controllable stroke"
```

---

## Task 6: Apply the new palette, Manrope digits, `TimerProgressRing`, and spring motion in `TimerPage`

**Files:**
- Modify: `windows/PomodoroWindows/Views/TimerPage.xaml`
- Modify: `windows/PomodoroWindows/Views/TimerPage.xaml.cs`

**Interfaces:**
- Consumes: `PomodoroPalette` (Task 3), `TimerProgressRing` (Task 5), `Spring1D` (Task 2).
- Produces: no new public symbols — `TimerPage`'s public surface (`IsMiniMode`, `MiniModeChanged`, `ToggleMiniMode()`) is unchanged.

- [ ] **Step 1: Replace `TimerPage.xaml`**

Replace the full contents of `windows/PomodoroWindows/Views/TimerPage.xaml`:

```xml
<Page
    x:Class="PomodoroWindows.Views.TimerPage"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
    xmlns:controls="using:PomodoroWindows.Controls">
    <Grid x:Name="RootGrid">
        <Border x:Name="TimerCard" HorizontalAlignment="Center" VerticalAlignment="Center"
                Style="{StaticResource SurfaceCardStyle}" Padding="40" MinWidth="380">
            <StackPanel HorizontalAlignment="Center" Spacing="24">
                <Border x:Name="PhaseBadge" Style="{StaticResource ChipBorderStyle}" HorizontalAlignment="Center">
                    <TextBlock x:Name="PhaseText" HorizontalAlignment="Center" FontSize="13" FontWeight="SemiBold"/>
                </Border>

                <Grid HorizontalAlignment="Center">
                    <controls:TimerProgressRing x:Name="ProgressRing" Diameter="280" RingThickness="10"/>
                    <TextBlock x:Name="RemainingText" HorizontalAlignment="Center" VerticalAlignment="Center"
                               FontSize="60" FontFamily="ms-appx:///Assets/Fonts/Manrope-Variable.ttf#Manrope"
                               FontWeight="ExtraBold" Typography.NumeralAlignment="Tabular"/>
                </Grid>

                <StackPanel Orientation="Horizontal" HorizontalAlignment="Center" Spacing="12">
                    <Border Style="{StaticResource ChipBorderStyle}">
                        <TextBlock x:Name="CompletedText" FontSize="13"/>
                    </Border>
                    <Border x:Name="DailyGoalBadge" Style="{StaticResource ChipBorderStyle}" Visibility="Collapsed">
                        <TextBlock x:Name="DailyGoalText" FontSize="13"/>
                    </Border>
                </StackPanel>

                <StackPanel Orientation="Horizontal" HorizontalAlignment="Center" Spacing="12">
                    <Button x:Name="StartPauseButton" Content="시작" Style="{StaticResource AccentPillButtonStyle}" Click="StartPauseButton_Click"/>
                    <Button x:Name="ResetButton" Content="리셋" Style="{StaticResource SubtlePillButtonStyle}" Click="ResetButton_Click"/>
                    <Button x:Name="SettingsButton" Content="설정" Style="{StaticResource SubtlePillButtonStyle}" Click="SettingsButton_Click"/>
                </StackPanel>
            </StackPanel>
        </Border>

        <Border x:Name="MiniView" HorizontalAlignment="Center" VerticalAlignment="Center"
                Opacity="0" IsHitTestVisible="False" CornerRadius="16" Padding="20,14">
            <StackPanel Orientation="Horizontal" Spacing="10" HorizontalAlignment="Center">
                <Ellipse x:Name="MiniPhaseDot" Width="10" Height="10" VerticalAlignment="Center"/>
                <TextBlock x:Name="MiniRemainingText" FontSize="26"
                           FontFamily="ms-appx:///Assets/Fonts/Manrope-Variable.ttf#Manrope"
                           FontWeight="ExtraBold" VerticalAlignment="Center" Typography.NumeralAlignment="Tabular"/>
            </StackPanel>
        </Border>
    </Grid>
</Page>
```

`Typography.NumeralAlignment="Tabular"` is the WinUI/UWP attached property that turns on the OpenType `tnum` feature so digit width never shifts as the countdown changes, mirroring the watch's `fontFeatureSettings = "tnum"` — it's set here as a plain XAML attribute the same way Microsoft's own samples set `Typography.*` properties (e.g. `Typography.Capitals`) directly on a `TextBlock`, no extra namespace prefix needed since the default `presentation` xmlns already maps the `Microsoft.UI.Xaml.Documents` attached properties. `FontWeight="ExtraBold"` (value 800) resolves against the Manrope variable font's `wght` axis via DirectWrite, the same mechanism already used for "Segoe UI Variable" elsewhere in WinUI.

- [ ] **Step 2: Replace the palette-consuming and rendering logic in `TimerPage.xaml.cs`**

Replace the full contents of `windows/PomodoroWindows/Views/TimerPage.xaml.cs`:

```csharp
using System.ComponentModel;
using System.Diagnostics;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;
using Windows.Storage;
using Windows.UI;

namespace PomodoroWindows.Views;

public sealed partial class TimerPage : Page
{
    private const string MiniModeKey = "IsMiniMode";
    private const double ProgressSpringStiffness = 90.0;
    private const double ProgressSpringDamping = 12.0;
    private const double ColorMixSpringStiffness = 140.0;
    private const double ColorMixSpringDamping = 24.0;

    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;
    private readonly Spring1D _progressSpring = new(0.0, ProgressSpringStiffness, ProgressSpringDamping);
    private readonly Spring1D _colorMixSpring = new(1.0, ColorMixSpringStiffness, ColorMixSpringDamping);
    private TimerViewModel? _viewModel;
    private Models.TimerSettings _settings = new();
    private Color _previousAccent;
    private Color _targetAccent;
    private long _lastRenderingTimestamp;
    private bool _isRenderingLoopActive;

    public bool IsMiniMode { get; private set; }
    public event EventHandler<bool>? MiniModeChanged;

    public TimerPage()
    {
        InitializeComponent();
        IsMiniMode = _localSettings.Values.TryGetValue(MiniModeKey, out var saved) && saved is true;
        ApplyModeVisibility(isInitialLoad: true);
    }

    public void ToggleMiniMode()
    {
        IsMiniMode = !IsMiniMode;
        _localSettings.Values[MiniModeKey] = IsMiniMode;
        ApplyModeVisibility(isInitialLoad: false);
        Render(resetMotion: false);
        MiniModeChanged?.Invoke(this, IsMiniMode);
    }

    // Both views stay in the live layout tree at all times (Opacity/hit-testing only) so
    // neither is ever fully Collapsed. Their ActualWidth/Height still isn't trustworthy here
    // though: whichever view is currently hidden keeps getting arranged inside whatever the
    // window's CURRENT size happens to be (e.g. squeezed into the small mini window), so
    // reading its ActualSize after switching modes just reads back that squeezed leftover.
    // Measuring against infinite space instead yields the view's true, unconstrained size.
    private void ApplyModeVisibility(bool isInitialLoad)
    {
        TimerCard.Opacity = IsMiniMode ? 0 : 1;
        TimerCard.IsHitTestVisible = !IsMiniMode;
        MiniView.Opacity = IsMiniMode ? 1 : 0;
        MiniView.IsHitTestVisible = IsMiniMode;

        FrameworkElement active = IsMiniMode ? MiniView : TimerCard;

        if (isInitialLoad)
        {
            void OnFirstSizeChanged(object s, Microsoft.UI.Xaml.SizeChangedEventArgs e)
            {
                active.SizeChanged -= OnFirstSizeChanged;
                FitToNaturalSize(active);
            }
            active.SizeChanged += OnFirstSizeChanged;
        }
        else
        {
            FitToNaturalSize(active);
        }
    }

    private static void FitToNaturalSize(FrameworkElement element)
    {
        element.Measure(new Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
        var size = element.DesiredSize;
        if (size.Height > 0)
        {
            MainWindow.Current?.FitToContent(size.Width, size.Height);
        }
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _viewModel = (TimerViewModel)e.Parameter;
        _settings = new Services.ApplicationDataSettingsStore().Load();
        _viewModel.PropertyChanged += ViewModel_PropertyChanged;
        ActualThemeChanged += ActualThemeChanged_Handler;
        StartRenderingLoop();
        Render(resetMotion: true);
    }

    protected override void OnNavigatedFrom(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (_viewModel is not null) _viewModel.PropertyChanged -= ViewModel_PropertyChanged;
        ActualThemeChanged -= ActualThemeChanged_Handler;
        StopRenderingLoop();
    }

    private void ViewModel_PropertyChanged(object? sender, PropertyChangedEventArgs e) =>
        DispatcherQueue.TryEnqueue(() => Render(resetMotion: false));

    private void ActualThemeChanged_Handler(FrameworkElement sender, object args) =>
        DispatcherQueue.TryEnqueue(() => Render(resetMotion: false));

    private void StartRenderingLoop()
    {
        if (_isRenderingLoopActive) return;
        _isRenderingLoopActive = true;
        _lastRenderingTimestamp = Stopwatch.GetTimestamp();
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering += CompositionTarget_Rendering;
    }

    private void StopRenderingLoop()
    {
        if (!_isRenderingLoopActive) return;
        _isRenderingLoopActive = false;
        Microsoft.UI.Xaml.Media.CompositionTarget.Rendering -= CompositionTarget_Rendering;
    }

    private void CompositionTarget_Rendering(object? sender, object e)
    {
        var now = Stopwatch.GetTimestamp();
        var deltaSeconds = (now - _lastRenderingTimestamp) / (double)Stopwatch.Frequency;
        _lastRenderingTimestamp = now;
        // A dropped/late frame (e.g. the window was occluded) can report a huge delta; clamp it
        // so the spring doesn't take one enormous, visibly-jarring step to catch up.
        deltaSeconds = Math.Min(deltaSeconds, 1.0 / 15.0);

        _progressSpring.Step(deltaSeconds);
        _colorMixSpring.Step(deltaSeconds);

        ProgressRing.Progress = _progressSpring.Value;
        ProgressRing.RingBrush = new SolidColorBrush(Lerp(_previousAccent, _targetAccent, Clamp01(_colorMixSpring.Value)));
    }

    private static double Clamp01(double value) => Math.Max(0.0, Math.Min(1.0, value));

    private static Color Lerp(Color from, Color to, double t) => Color.FromArgb(
        255,
        (byte)(from.R + (to.R - from.R) * t),
        (byte)(from.G + (to.G - from.G) * t),
        (byte)(from.B + (to.B - from.B) * t));

    private void Render(bool resetMotion)
    {
        if (_viewModel is null) return;

        PhaseText.Text = _viewModel.Phase switch
        {
            TimerPhase.Focus => "집중",
            TimerPhase.ShortBreak => "휴식",
            TimerPhase.LongBreak => "긴 휴식",
            _ => string.Empty
        };

        var totalSeconds = Math.Max(_viewModel.RemainingMillis / 1000, 0);
        var timeText = $"{totalSeconds / 60:D2}:{totalSeconds % 60:D2}";
        RemainingText.Text = timeText;
        MiniRemainingText.Text = timeText;
        CompletedText.Text = $"완료한 뽀모도로: {_viewModel.CompletedPomodoros}";
        StartPauseButton.Content = _viewModel.IsRunning ? "일시정지" : "시작";

        var isDark = this.ActualTheme == ElementTheme.Dark;
        var palette = Palettes.PaletteFor(_settings.ColorTheme, isDark);
        var accent = _viewModel.Phase == TimerPhase.LongBreak ? palette.LongBreakAccent : palette.Accent;

        var progressFraction = _viewModel.TotalMillis > 0
            ? (double)(_viewModel.TotalMillis - _viewModel.RemainingMillis) / _viewModel.TotalMillis
            : 0.0;

        if (resetMotion)
        {
            _progressSpring.Reset(progressFraction);
            _previousAccent = accent;
            _targetAccent = accent;
            _colorMixSpring.Reset(1.0);
            ProgressRing.Progress = progressFraction;
            ProgressRing.RingBrush = new SolidColorBrush(accent);
        }
        else
        {
            _progressSpring.SetTarget(progressFraction);
            if (accent != _targetAccent)
            {
                _previousAccent = Lerp(_previousAccent, _targetAccent, Clamp01(_colorMixSpring.Value));
                _targetAccent = accent;
                _colorMixSpring.Reset(0.0);
                _colorMixSpring.SetTarget(1.0);
            }
        }

        ProgressRing.TrackBrush = new SolidColorBrush(palette.Border);

        if (_settings.DailyGoalEnabled)
        {
            DailyGoalBadge.Visibility = Microsoft.UI.Xaml.Visibility.Visible;
            DailyGoalText.Text = $"오늘 목표: {_viewModel.DailyGoalCount}/{_settings.DailyGoalTarget}";
        }
        else
        {
            DailyGoalBadge.Visibility = Microsoft.UI.Xaml.Visibility.Collapsed;
        }

        // In mini mode the window backdrop is real Acrylic (see MainWindow), so the page itself
        // must stay transparent or it would paint over — and hide — the see-through effect.
        RootGrid.Background = new SolidColorBrush(IsMiniMode ? Colors.Transparent : palette.Background);
        TimerCard.Background = new SolidColorBrush(palette.Surface);
        TimerCard.BorderBrush = new SolidColorBrush(palette.Border);

        RemainingText.Foreground = new SolidColorBrush(palette.TextPrimary);
        MiniRemainingText.Foreground = new SolidColorBrush(palette.TextPrimary);
        CompletedText.Foreground = new SolidColorBrush(palette.TextSecondary);
        DailyGoalText.Foreground = new SolidColorBrush(palette.TextSecondary);

        PhaseBadge.Background = new SolidColorBrush(Color.FromArgb(38, accent.R, accent.G, accent.B));
        PhaseText.Foreground = new SolidColorBrush(accent);
        MiniPhaseDot.Fill = new SolidColorBrush(accent);

        // A frosted-glass tint over the acrylic backdrop — translucent enough to read as a
        // floating widget rather than an opaque card, while staying legible over any wallpaper.
        MiniView.Background = new SolidColorBrush(isDark
            ? Color.FromArgb(140, 20, 20, 20)
            : Color.FromArgb(140, 255, 255, 255));

        ApplyAccentButtonColors(StartPauseButton, accent, palette.OnAccent);

        var minutesRemaining = _settings.ShowTaskbarBadge && _viewModel.IsRunning
            ? (int?)(totalSeconds / 60)
            : null;
        MainWindow.Current?.UpdateTaskbarBadge(minutesRemaining, accent);
    }

    private static void ApplyAccentButtonColors(Button button, Color background, Color foreground)
    {
        button.Resources["ButtonBackground"] = new SolidColorBrush(background);
        button.Resources["ButtonBackgroundPointerOver"] = new SolidColorBrush(Blend(background, Colors.White, 0.12));
        button.Resources["ButtonBackgroundPressed"] = new SolidColorBrush(Blend(background, Colors.Black, 0.12));
        button.Resources["ButtonForeground"] = new SolidColorBrush(foreground);
        button.Resources["ButtonForegroundPointerOver"] = new SolidColorBrush(foreground);
        button.Resources["ButtonForegroundPressed"] = new SolidColorBrush(foreground);
        var transparent = new SolidColorBrush(Colors.Transparent);
        button.Resources["ButtonBorderBrush"] = transparent;
        button.Resources["ButtonBorderBrushPointerOver"] = transparent;
        button.Resources["ButtonBorderBrushPressed"] = transparent;
    }

    private static Color Blend(Color from, Color to, double amount) => Color.FromArgb(
        255,
        (byte)(from.R + (to.R - from.R) * amount),
        (byte)(from.G + (to.G - from.G) * amount),
        (byte)(from.B + (to.B - from.B) * amount));

    private void StartPauseButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.StartPauseCommand.Execute(DateTimeOffset.UtcNow);

    private void ResetButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.ResetCommand.Execute(null);

    private void SettingsButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        Frame.Navigate(typeof(SettingsPage), _viewModel);
}
```

Notes on the change from the original `Render()`:
- `Render()` now takes a `resetMotion` flag: `true` on first navigation to the page (snap directly to the correct progress/color with no spring-in-from-zero glitch), `false` on every subsequent view-model tick or theme change (let the springs animate toward the new target).
- The per-second `ViewModel_PropertyChanged` tick only *sets spring targets*; the actual `ProgressRing.Progress`/`RingBrush` assignment now happens every frame in `CompositionTarget_Rendering`, which is what makes the motion smooth instead of ticking once per second.
- The reset-branch's `_colorMixSpring.Reset(1.0)` primes it as "already arrived" (mix ratio 1.0 means "fully at `_targetAccent`") so a plain `Render(resetMotion: true)` shows the correct color with no fade; the non-reset branch's `_colorMixSpring.Reset(0.0)` + `SetTarget(1.0)` is what actually kicks off a color transition when the accent changes.

- [ ] **Step 3: Build**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: still **build FAILS**, but now with only `error CS1061` on `palette.Primary` in `Views/SettingsPage.xaml.cs` (Task 7 fixes it) — `TimerPage.xaml`/`.xaml.cs` themselves contribute no errors, but `dotnet build` builds the whole `PomodoroWindows` project, and `PomodoroWindows.Tests` has a project reference to it, so neither the main project nor its test project can produce a green build/test run until Task 7 also lands. Do not attempt to run `dotnet test` yet — it would fail on this same pre-existing compile error, not on anything this task changed.

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows/Views/TimerPage.xaml windows/PomodoroWindows/Views/TimerPage.xaml.cs
git commit -m "feat(windows): render timer digits in Manrope, use TimerProgressRing, spring-animate progress and phase color"
```

---

## Task 7: Apply the new palette to `SettingsPage`

**Files:**
- Modify: `windows/PomodoroWindows/Views/SettingsPage.xaml.cs`

**Interfaces:**
- Consumes: `PomodoroPalette` (Task 3).
- Produces: no new public symbols.

The settings page currently relies entirely on WinUI's system `ThemeResource` brushes and never reads the palette except for theme swatches — this task brings it in line with `TimerPage` so navigating between the two doesn't feel like two different apps: cards get the palette's `Surface`/`Border`, section headers and page title get `TextPrimary`/`TextSecondary`, and swatches switch from the old `palette.Primary` to `palette.Accent`.

- [ ] **Step 1: Add named `x:Name`s to the page title and the 5 card borders**

In `windows/PomodoroWindows/Views/SettingsPage.xaml`, add `x:Name` to the title and each `SurfaceCardStyle` `Border` (and their `SectionHeaderTextStyle` headers) so code-behind can color them. Replace the full contents of `windows/PomodoroWindows/Views/SettingsPage.xaml`:

```xml
<Page
    x:Class="PomodoroWindows.Views.SettingsPage"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <ScrollViewer>
        <StackPanel Padding="32" Spacing="20" MaxWidth="560">
            <TextBlock x:Name="TitleText" Text="설정" FontSize="28" FontWeight="SemiBold"/>

            <Border x:Name="TimerCard" Style="{StaticResource SurfaceCardStyle}">
                <StackPanel Spacing="16">
                    <TextBlock x:Name="TimerHeaderText" Text="타이머" Style="{StaticResource SectionHeaderTextStyle}"/>
                    <StackPanel Spacing="4">
                        <TextBlock x:Name="FocusLabel"/>
                        <Slider x:Name="FocusSlider" Minimum="5" Maximum="60" ValueChanged="FocusSlider_ValueChanged"/>
                    </StackPanel>
                    <StackPanel Spacing="4">
                        <TextBlock x:Name="BreakLabel"/>
                        <Slider x:Name="BreakSlider" Minimum="1" Maximum="30" ValueChanged="BreakSlider_ValueChanged"/>
                    </StackPanel>
                    <StackPanel Spacing="4">
                        <TextBlock x:Name="LongBreakLabel"/>
                        <Slider x:Name="LongBreakSlider" Minimum="5" Maximum="45" ValueChanged="LongBreakSlider_ValueChanged"/>
                    </StackPanel>
                    <StackPanel Spacing="4">
                        <TextBlock x:Name="PomodorosUntilLongBreakLabel"/>
                        <Slider x:Name="PomodorosUntilLongBreakSlider" Minimum="2" Maximum="8" ValueChanged="PomodorosUntilLongBreakSlider_ValueChanged"/>
                    </StackPanel>
                </StackPanel>
            </Border>

            <Border x:Name="AutomationCard" Style="{StaticResource SurfaceCardStyle}">
                <StackPanel Spacing="12">
                    <TextBlock x:Name="AutomationHeaderText" Text="자동화" Style="{StaticResource SectionHeaderTextStyle}"/>
                    <ToggleSwitch x:Name="AutoStartBreakToggle" Header="자동으로 휴식 시작" Toggled="AutoStartBreakToggle_Toggled"/>
                    <ToggleSwitch x:Name="AutoStartFocusToggle" Header="자동으로 다음 뽀모도로 시작" Toggled="AutoStartFocusToggle_Toggled"/>
                    <ToggleSwitch x:Name="KeepScreenOnToggle" Header="화면 꺼짐 방지" Toggled="KeepScreenOnToggle_Toggled"/>
                    <ToggleSwitch x:Name="ShowTaskbarBadgeToggle" Header="작업 표시줄에 남은 시간 배지 표시" Toggled="ShowTaskbarBadgeToggle_Toggled"/>
                </StackPanel>
            </Border>

            <Border x:Name="GoalCard" Style="{StaticResource SurfaceCardStyle}">
                <StackPanel Spacing="12">
                    <TextBlock x:Name="GoalHeaderText" Text="목표" Style="{StaticResource SectionHeaderTextStyle}"/>
                    <ToggleSwitch x:Name="DailyGoalEnabledToggle" Header="일일 목표 사용" Toggled="DailyGoalEnabledToggle_Toggled"/>
                    <StackPanel Spacing="4">
                        <TextBlock x:Name="DailyGoalTargetLabel"/>
                        <Slider x:Name="DailyGoalTargetSlider" Minimum="1" Maximum="20" ValueChanged="DailyGoalTargetSlider_ValueChanged"/>
                    </StackPanel>
                </StackPanel>
            </Border>

            <Border x:Name="NotificationsCard" Style="{StaticResource SurfaceCardStyle}">
                <StackPanel Spacing="12">
                    <TextBlock x:Name="NotificationsHeaderText" Text="알림" Style="{StaticResource SectionHeaderTextStyle}"/>
                    <ToggleSwitch x:Name="NotificationsEnabledToggle" Header="알림 사용" Toggled="NotificationsEnabledToggle_Toggled"/>
                    <ToggleSwitch x:Name="UseAlarmSoundToggle" Header="알람음 사용 (끄면 알림음)" Toggled="UseAlarmSoundToggle_Toggled"/>
                </StackPanel>
            </Border>

            <Border x:Name="ThemeCard" Style="{StaticResource SurfaceCardStyle}">
                <StackPanel Spacing="12">
                    <TextBlock x:Name="ThemeHeaderText" Text="테마" Style="{StaticResource SectionHeaderTextStyle}"/>
                    <StackPanel x:Name="ThemeSwatchPanel" Orientation="Horizontal" Spacing="16"/>
                </StackPanel>
            </Border>

            <Button x:Name="BackButton" Content="뒤로" Style="{StaticResource SubtlePillButtonStyle}" Click="BackButton_Click" Margin="0,4,0,16"/>
        </StackPanel>
    </ScrollViewer>
</Page>
```

- [ ] **Step 2: Apply the palette in `SettingsPage.xaml.cs`**

In `windows/PomodoroWindows/Views/SettingsPage.xaml.cs`, replace the `using` block and `OnNavigatedTo` method, and add theming methods. Replace:

```csharp
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Views;

public sealed partial class SettingsPage : Page
{
    private SettingsViewModel? _viewModel;
    private bool _isLoading;

    public SettingsPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        var timerViewModel = (TimerViewModel)e.Parameter;
        _viewModel = new SettingsViewModel(new Services.ApplicationDataSettingsStore(), timerViewModel);
        LoadIntoControls();
    }
```

with:

```csharp
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Views;

public sealed partial class SettingsPage : Page
{
    private SettingsViewModel? _viewModel;
    private bool _isLoading;

    public SettingsPage()
    {
        InitializeComponent();
        ActualThemeChanged += (_, _) => ApplyPaletteToChrome();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        var timerViewModel = (TimerViewModel)e.Parameter;
        _viewModel = new SettingsViewModel(new Services.ApplicationDataSettingsStore(), timerViewModel);
        LoadIntoControls();
        ApplyPaletteToChrome();
    }

    private void ApplyPaletteToChrome()
    {
        if (_viewModel is null) return;

        var isDark = this.ActualTheme == ElementTheme.Dark;
        var palette = Palettes.PaletteFor(_viewModel.ColorTheme, isDark);

        TitleText.Foreground = new SolidColorBrush(palette.TextPrimary);

        foreach (var card in new[] { TimerCard, AutomationCard, GoalCard, NotificationsCard, ThemeCard })
        {
            card.Background = new SolidColorBrush(palette.Surface);
            card.BorderBrush = new SolidColorBrush(palette.Border);
        }

        foreach (var header in new[] { TimerHeaderText, AutomationHeaderText, GoalHeaderText, NotificationsHeaderText, ThemeHeaderText })
        {
            header.Foreground = new SolidColorBrush(palette.TextSecondary);
        }
    }
```

- [ ] **Step 3: Update `RenderThemeSwatches` to use `Accent` and re-apply chrome on theme change**

Replace:

```csharp
    private void RenderThemeSwatches()
    {
        if (_viewModel is null) return;

        ThemeSwatchPanel.Children.Clear();
        foreach (ColorTheme theme in Enum.GetValues<ColorTheme>())
        {
            var palette = Palettes.PaletteFor(theme, darkTheme: false);
            var isSelected = theme == _viewModel.ColorTheme;

            var swatch = new Ellipse
            {
                Width = 36,
                Height = 36,
                Fill = new SolidColorBrush(palette.Primary),
            };

            var ring = new Border
            {
                Width = 44,
                Height = 44,
                CornerRadius = new CornerRadius(22),
                BorderThickness = new Thickness(isSelected ? 2 : 0),
                BorderBrush = new SolidColorBrush(palette.Primary),
                Padding = new Thickness(4),
                Tag = theme,
                Child = swatch
            };
            ring.Tapped += ThemeSwatch_Tapped;
            ThemeSwatchPanel.Children.Add(ring);
        }
    }

    private void ThemeSwatch_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _viewModel.ColorTheme = (ColorTheme)((Border)sender).Tag;
        _viewModel.Save();
        RenderThemeSwatches();
    }
```

with:

```csharp
    private void RenderThemeSwatches()
    {
        if (_viewModel is null) return;

        ThemeSwatchPanel.Children.Clear();
        foreach (ColorTheme theme in Enum.GetValues<ColorTheme>())
        {
            var palette = Palettes.PaletteFor(theme, darkTheme: false);
            var isSelected = theme == _viewModel.ColorTheme;

            var swatch = new Ellipse
            {
                Width = 36,
                Height = 36,
                Fill = new SolidColorBrush(palette.Accent),
            };

            var ring = new Border
            {
                Width = 44,
                Height = 44,
                CornerRadius = new CornerRadius(22),
                BorderThickness = new Thickness(isSelected ? 2 : 0),
                BorderBrush = new SolidColorBrush(palette.Accent),
                Padding = new Thickness(4),
                Tag = theme,
                Child = swatch
            };
            ring.Tapped += ThemeSwatch_Tapped;
            ThemeSwatchPanel.Children.Add(ring);
        }
    }

    private void ThemeSwatch_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _viewModel.ColorTheme = (ColorTheme)((Border)sender).Tag;
        _viewModel.Save();
        RenderThemeSwatches();
        ApplyPaletteToChrome();
    }
```

Theme swatches themselves intentionally keep sampling the **light** palette regardless of the page's current mode (`darkTheme: false`, unchanged from before this task) — they're meant to show each theme's identifying color consistently, not preview the current light/dark mode.

- [ ] **Step 4: Build**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: `Build succeeded` — this was the last file referencing the old `palette.Primary` field, so the whole solution should now build clean.

- [ ] **Step 5: Run the full Windows test suite**

```bash
dotnet test windows/PomodoroWindows.Tests/PomodoroWindows.Tests.csproj
dotnet test windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj
```

Expected: `Passed!` for both projects — `TimerViewModelTests`, `PomodoroPaletteTests` (Task 3), `Spring1DTests` (Task 2), and every other pre-existing test.

- [ ] **Step 6: Commit**

```bash
git add windows/PomodoroWindows/Views/SettingsPage.xaml windows/PomodoroWindows/Views/SettingsPage.xaml.cs
git commit -m "feat(windows): apply the monochrome palette to SettingsPage cards, headers, and swatches"
```

---

## Task 8: Spring-based press feedback on pill buttons

**Files:**
- Create: `windows/PomodoroWindows/Controls/PressScaleBehavior.cs`
- Modify: `windows/PomodoroWindows/Views/TimerPage.xaml.cs`
- Modify: `windows/PomodoroWindows/Views/SettingsPage.xaml.cs`

**Interfaces:**
- Produces: `public static class PressScaleBehavior { public static void Attach(UIElement element); }`. Consumed by `TimerPage`'s `OnNavigatedTo` and `SettingsPage`'s `OnNavigatedTo`.

This is the spec's "button press feedback (a small scale-down, e.g. ~0.94, that springs back on release)" — the one motion requirement that isn't the progress ring. Unlike the ring's progress/color (a value this plan recomputes into `Path` geometry every frame, which is why Task 2 built a manual `Spring1D`), a press scale is a plain Visual-layer transform, which is exactly what WinUI 3's own Composition spring animations are for — `ElementCompositionPreview.GetElementVisual(element)` gets the element's backing `Visual`, and `visual.Compositor.CreateSpringVector3Animation()` (confirmed present on `Microsoft.UI.Composition.Compositor`, mirroring the same-named UWP `Windows.UI.Composition` API 1:1 the way WinUI 3's Composition types consistently do) animates its `Scale` property with real spring physics, so there's no reason to route it through the custom `Spring1D` helper instead.

- [ ] **Step 1: Implement `PressScaleBehavior`**

Create `windows/PomodoroWindows/Controls/PressScaleBehavior.cs`:

```csharp
using System.Numerics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Hosting;

namespace PomodoroWindows.Controls;

/// <summary>
/// Wires up a small spring-based scale-down-on-press, spring-back-on-release effect on any
/// UIElement, matching the shared design spec's button press motion token.
/// </summary>
public static class PressScaleBehavior
{
    private const float PressedScale = 0.94f;

    public static void Attach(UIElement element)
    {
        var visual = ElementCompositionPreview.GetElementVisual(element);
        var compositor = visual.Compositor;

        void UpdateCenterPoint()
        {
            if (element is FrameworkElement frameworkElement)
            {
                visual.CenterPoint = new Vector3(
                    (float)(frameworkElement.ActualWidth / 2.0),
                    (float)(frameworkElement.ActualHeight / 2.0),
                    0f);
            }
        }

        UpdateCenterPoint();
        if (element is FrameworkElement trackedElement)
        {
            trackedElement.SizeChanged += (_, _) => UpdateCenterPoint();
        }

        var spring = compositor.CreateSpringVector3Animation();
        spring.Target = "Scale";
        spring.DampingRatio = 0.5f;
        spring.Period = TimeSpan.FromMilliseconds(150);

        void AnimateTo(float scale)
        {
            spring.FinalValue = new Vector3(scale, scale, 1f);
            visual.StartAnimation("Scale", spring);
        }

        element.PointerPressed += (_, _) => AnimateTo(PressedScale);
        element.PointerReleased += (_, _) => AnimateTo(1f);
        element.PointerCanceled += (_, _) => AnimateTo(1f);
        element.PointerCaptureLost += (_, _) => AnimateTo(1f);
    }
}
```

- [ ] **Step 2: Attach it to `TimerPage`'s three pill buttons**

In `windows/PomodoroWindows/Views/TimerPage.xaml.cs`, add `using PomodoroWindows.Controls;` to the `using` block at the top of the file, and in `OnNavigatedTo`, right after `StartRenderingLoop();`, add:

```csharp
        if (!_pressScaleAttached)
        {
            _pressScaleAttached = true;
            PressScaleBehavior.Attach(StartPauseButton);
            PressScaleBehavior.Attach(ResetButton);
            PressScaleBehavior.Attach(SettingsButton);
        }
```

and add the backing field next to the other fields declared at the top of the class:

```csharp
    private bool _pressScaleAttached;
```

`OnNavigatedTo` can run more than once for the same `TimerPage` instance (navigating away to Settings and back), so this guard keeps `Attach` from wiring duplicate pointer-event handlers onto the same buttons.

- [ ] **Step 3: Attach it to `SettingsPage`'s back button**

In `windows/PomodoroWindows/Views/SettingsPage.xaml.cs`, add `using PomodoroWindows.Controls;` to the `using` block at the top of the file, and in `OnNavigatedTo`, right after `ApplyPaletteToChrome();`, add:

```csharp
        PressScaleBehavior.Attach(BackButton);
```

`SettingsPage` is freshly constructed by `Frame.Navigate` on every visit (unlike `TimerPage`, which persists), so there's no risk of double-attaching here and no guard field is needed.

- [ ] **Step 4: Build**

```bash
dotnet build windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

Expected: `Build succeeded`.

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows/Controls/PressScaleBehavior.cs windows/PomodoroWindows/Views/TimerPage.xaml.cs windows/PomodoroWindows/Views/SettingsPage.xaml.cs
git commit -m "feat(windows): spring-animate a press scale-down on pill buttons"
```

---

## Task 9: Manual on-device verification

Visual and motion quality aren't meaningfully unit-testable — this task is the human check, matching the pattern already established for this project's other visual features (see `color-themes-design.md`'s Testing Strategy, and the equivalent Task 5 of the watch's `2026-08-23-wear-modern-design-tokens.md`).

**Files:** none — this task is verification only.

- [ ] **Step 1: Run the app**

```bash
dotnet run --project windows/PomodoroWindows/PomodoroWindows.csproj -c Debug
```

- [ ] **Step 2: Check color and typography across all 4 themes, in both light and dark mode**

Open Settings, cycle through Default/Ocean/Forest/Lavender, and toggle Windows' own light/dark mode (Settings > Personalization > Colors) while the app is running. Confirm for each combination: the background/card colors match the new near-black/near-white or pure-white neutrals (not the old warm-tinted per-theme backgrounds), the countdown digits render visibly bolder/different from the phase-label text (Manrope ExtraBold vs. Segoe UI Variable), and the accent color matches the new hex from Global Constraints. If the countdown digits do **not** look different from the rest of the UI, the `ms-appx:///...#Manrope` font-family lookup silently fell back to the default font — check the exact family name embedded in `Manrope-Variable.ttf` (e.g. via a font viewer) rather than assuming `#Manrope` is correct, and adjust the `FontFamily` string in `TimerPage.xaml` to match.

- [ ] **Step 3: Check the progress ring**

Confirm the ring reads thinner with more surrounding space than the old stock `ProgressRing`, that it still fills clockwise from the top, and that a long-break phase shows the same indigo regardless of which of the 4 themes is active.

- [ ] **Step 4: Check motion feel**

Watch the ring update over a few seconds of a running timer — it should read as a smooth, slightly organic movement, not a linear tick. Trigger a phase change (or set a short focus/break duration in Settings to force one quickly) and confirm the ring color cross-fades rather than snapping instantly.

- [ ] **Step 5: Check button press feedback**

Click and hold the 시작/리셋/설정 pill buttons (and 뒤로 on Settings) — each should visibly shrink slightly on press and spring back on release, not just change color. If nothing visibly moves, check that `PressScaleBehavior.Attach` is actually being called for that button (Task 8) before assuming the spring itself is wrong.

- [ ] **Step 6: Check mini mode and the taskbar badge are unaffected**

Toggle mini mode (the pill button in the title bar) and confirm the mini view still shows the frosted-glass pill with correct accent/text colors and Manrope digits. If "작업 표시줄에 남은 시간 배지 표시" is on, confirm the taskbar badge still renders with the current accent color.

- [ ] **Step 7: Report findings**

If everything in Steps 2–6 checks out, this plan is done. If anything looks wrong, note exactly which step and what was observed — do not silently adjust values without going back to a numbered step above and re-running its verification.
