# Windows 네이티브 뽀모도로 앱 (1단계: 독립 앱) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone WinUI 3 Windows app that reproduces the phone app's Pomodoro timer, settings, themes, and notifications — with no dependency on the phone/watch (sync is a future, separate project).

**Architecture:** A `PomodoroWindows.Core` class library (no WinUI dependency) holds the ported timer/domain logic and is unit-tested with xUnit, mirroring the Kotlin `:core` module. A `PomodoroWindows` WinUI 3 app project (MVVM, `CommunityToolkit.Mvvm`) consumes it: a `TimerViewModel` drives the UI and is itself unit-testable because `TimerEngine` takes `now` as an explicit parameter rather than reading the wall clock. Platform services (settings storage, toast notifications, tray icon, keep-awake) sit behind small interfaces so the view model can be tested with fakes; their concrete WinRT-backed implementations are verified manually (per the spec's testing plan — WinUI 3 UI automation setup cost is out of scope).

**Tech Stack:** .NET 8, WinUI 3 (Windows App SDK), MSIX packaging, `CommunityToolkit.Mvvm`, `H.NotifyIcon.WinUI`, xUnit.

**Spec:** [docs/superpowers/specs/2026-08-22-windows-companion-design.md](../specs/2026-08-22-windows-companion-design.md)

## Global Constraints

- All Windows-side code lives under a new top-level `windows/` directory (sibling to `app/`, `core/`, `wear/`) — this is a separate solution, not part of the Gradle build.
- `TimerEngine` excludes `adoptRemote`/`SyncPayload` entirely — sync is a separate future project (spec §범위 제외).
- No vibration setting, no onboarding flow (spec §기능 범위 / 제외).
- Korean UI strings/notification text are copied verbatim from the Kotlin source (`NotificationContent.kt`, `SettingsScreen.kt`) — do not retranslate or reword.
- NuGet package versions below are current as of plan authoring (2026-08-22). If `dotnet restore` reports a version doesn't exist, re-resolve with `dotnet add package <id>` (no version pin) to pick up the current stable release instead of guessing a number.
- **PATH note for this machine:** `dotnet` was just installed via winget and the installer's PATH update has not propagated to already-running shells. Prefix every PowerShell command in this plan's steps with:
  ```powershell
  $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
  ```
  (harmless no-op once a fresh shell already has `dotnet` on PATH).
- **Solution-level build/test commands must NOT pass `-p:Platform=x64`.** `PomodoroWindows.sln`'s solution-configuration map only declares `Debug|Any CPU`/`Release|Any CPU` (routed per-project to `x64` via `ProjectConfigurationPlatforms.ActiveCfg`, standard `dotnet sln add` output for a `Platforms=x64;arm64`-only project) — forcing `-p:Platform=x64` on a bare `dotnet build`/`dotnet test` (no project path) breaks that routing with `MSB4126`. Use plain `dotnet build` / `dotnet test` when invoking at the solution level (no project argument); keep `-p:Platform=x64` only on commands that target a specific `.csproj` directly (e.g. `dotnet build PomodoroWindows\PomodoroWindows.csproj ...`), where it's required and already correct throughout this plan.
- **`dotnet test` against any project that references `PomodoroWindows.csproj`** (i.e. `PomodoroWindows.Tests`) **requires two extra flags**: `-p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false`. Without the first, the build fails (`PomodoroWindows.csproj` has no AnyCPU config). Without the second, every test throws `COMException (REGDB_E_CLASSNOTREG)` from a Windows App SDK deployment-bootstrap module initializer baked into `PomodoroWindows.dll` (triggered by its `WindowsPackageType=MSIX`+`WinExe` config) that fires on any assembly load outside a packaged/deployed app — including the xUnit test host. This does not affect `PomodoroWindows.Core.Tests` (pure class library, no WindowsAppSDK dependency) — its bare `dotnet test` commands elsewhere in this plan are unaffected. Full form: `dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false`.
- **`dotnet run --project ... -p:Platform=x64` does not work** for this multi-platform (`Platforms=x64;arm64`) packaged project — it looks for the exe under `bin\Debug\...` instead of the actual `bin\x64\Debug\...`, and even fixed, a `WindowsPackageType=MSIX` app needs package identity to activate its WinRT/WindowsAppSDK dependencies (plain exe launch fails with `REGDB_E_CLASSNOTREG`, the same underlying cause as the test-flag issue above). Wherever this plan's steps say "run the app" from the command line (not applicable to a human launching it from the Start Menu, which works normally), use this instead:
  ```powershell
  $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
  cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
  dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
  Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
  Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
  Start-Sleep -Seconds 3
  Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
  ```
  (`PomodoroWindows_hqfn3qvq51sga` is this app's package family name, deterministic from `Package.appxmanifest`'s `Identity Name`/`Publisher` — stable across ordinary rebuilds. `Add-AppxPackage -Register` re-registers in place each time, so this is safe to repeat after every rebuild.) This requires Windows Developer Mode to be enabled (Settings → Privacy & Security → For Developers → Developer Mode) — already turned on for this machine as of Task 6.
- **Implementer subagents cannot click buttons or read on-screen text** — they have no GUI interaction tool, only PowerShell process/window inspection. For Tasks 10-14's "manually verify" steps, an implementer's achievable verification is: build succeeds, the app registers and launches via the pattern above, `Get-Process` shows the expected `MainWindowTitle`, and the process doesn't crash within a few seconds. The deeper interaction checks each task's "Manually verify" prose describes (clicking buttons, reading updated text, toggling settings) are real requirements, but they are verified by the human user during Task 15's manual QA checklist, not by the implementer subagent — an implementer should say so explicitly in its report rather than claim it clicked anything.

---

## File Structure

```
windows/
  PomodoroWindows.sln
  PomodoroWindows.Core/                    (net8.0 class library, no WinUI dependency)
    PomodoroWindows.Core.csproj
    TimerPhase.cs                          Focus/ShortBreak/LongBreak enum
    TimerDefaults.cs                       min/max/default constants
    ColorTheme.cs                          Default/Ocean/Forest/Lavender enum
    TimerState.cs                          immutable state record
    DailyGoalProgress.cs + DailyGoalTracker.cs
    NotificationContent.cs                 pure text-mapping functions
    TimerEngine.cs                         phase-transition state machine
  PomodoroWindows.Core.Tests/               (xUnit)
    PomodoroWindows.Core.Tests.csproj
    TimerEngineTests.cs
    DailyGoalTrackerTests.cs
    NotificationContentTests.cs
  PomodoroWindows/                          (WinUI 3 app, MSIX-packaged)
    PomodoroWindows.csproj
    app.manifest
    Package.appxmanifest
    Assets/ (Square150x150Logo.png, Square44x44Logo.png, StoreLogo.png, SplashScreen.png, Wide310x150Logo.png — placeholder PNGs)
    App.xaml / App.xaml.cs
    MainWindow.xaml / MainWindow.xaml.cs
    Models/TimerSettings.cs                 mirrors Kotlin TimerSettings (minus vibration/onboarding)
    Services/ISettingsStore.cs, ApplicationDataSettingsStore.cs, FakeSettingsStore.cs
    Services/INotificationService.cs, AppNotificationService.cs
    Services/ITrayIconService.cs, TrayIconService.cs
    Services/IKeepAwakeService.cs, KeepAwakeService.cs
    Theme/PomodoroPalette.cs                 ColorTheme -> (light,dark) brush values, ported from Color.kt
    ViewModels/TimerViewModel.cs
    ViewModels/SettingsViewModel.cs
    Views/TimerPage.xaml / .cs
    Views/SettingsPage.xaml / .cs
  PomodoroWindows.Tests/                     (xUnit, references PomodoroWindows — view-model logic only)
    PomodoroWindows.Tests.csproj
    TimerViewModelTests.cs
```

---

### Task 1: Solution and Core project scaffolding

**Files:**
- Create: `windows/PomodoroWindows.sln`
- Create: `windows/PomodoroWindows.Core/PomodoroWindows.Core.csproj`
- Create: `windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj`
- Create: `windows/PomodoroWindows.Core.Tests/SmokeTest.cs`

**Interfaces:**
- Produces: a `PomodoroWindows.Core` class library project that later tasks add files to; a working `dotnet test` loop.

- [ ] **Step 1: Create the directory and class library project**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion
mkdir windows
cd windows
dotnet new sln -n PomodoroWindows
dotnet new classlib -n PomodoroWindows.Core -o PomodoroWindows.Core --framework net8.0
Remove-Item PomodoroWindows.Core\Class1.cs
dotnet sln add PomodoroWindows.Core\PomodoroWindows.Core.csproj
```

- [ ] **Step 2: Create the xUnit test project and reference Core**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet new xunit -n PomodoroWindows.Core.Tests -o PomodoroWindows.Core.Tests --framework net8.0
Remove-Item PomodoroWindows.Core.Tests\UnitTest1.cs
dotnet add PomodoroWindows.Core.Tests\PomodoroWindows.Core.Tests.csproj reference PomodoroWindows.Core\PomodoroWindows.Core.csproj
dotnet sln add PomodoroWindows.Core.Tests\PomodoroWindows.Core.Tests.csproj
```

- [ ] **Step 3: Write a smoke test**

`windows/PomodoroWindows.Core.Tests/SmokeTest.cs`:
```csharp
namespace PomodoroWindows.Core.Tests;

public class SmokeTest
{
    [Fact]
    public void True_is_true()
    {
        Assert.True(true);
    }
}
```

- [ ] **Step 4: Run the test suite and verify it passes**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: `Passed! - Failed: 0, Passed: 1`

- [ ] **Step 5: Commit**

```bash
git add windows/
git commit -m "chore: scaffold PomodoroWindows.Core solution and test project"
```

---

### Task 2: TimerPhase, TimerDefaults, ColorTheme, TimerState

Small, non-branching value types — no TDD cycle needed for plain enums/records, but a short assertion test locks in the record equality semantics `TimerEngine` will depend on.

**Files:**
- Create: `windows/PomodoroWindows.Core/TimerPhase.cs`
- Create: `windows/PomodoroWindows.Core/TimerDefaults.cs`
- Create: `windows/PomodoroWindows.Core/ColorTheme.cs`
- Create: `windows/PomodoroWindows.Core/TimerState.cs`
- Test: `windows/PomodoroWindows.Core.Tests/TimerStateTests.cs`

**Interfaces:**
- Produces: `TimerPhase` (`Focus`, `ShortBreak`, `LongBreak`), `TimerDefaults` (const ints), `ColorTheme` (`Default`, `Ocean`, `Forest`, `Lavender`), `TimerState` record with `Phase`, `IsRunning`, `RemainingMillis`, `TotalMillis`, `CompletedPomodoros`.

- [ ] **Step 1: Write the types**

`windows/PomodoroWindows.Core/TimerPhase.cs`:
```csharp
namespace PomodoroWindows.Core;

public enum TimerPhase
{
    Focus,
    ShortBreak,
    LongBreak
}
```

`windows/PomodoroWindows.Core/TimerDefaults.cs`:
```csharp
namespace PomodoroWindows.Core;

public static class TimerDefaults
{
    public const int DefaultFocusMinutes = 25;
    public const int DefaultBreakMinutes = 5;
    public const int MinFocusMinutes = 5;
    public const int MaxFocusMinutes = 60;
    public const int MinBreakMinutes = 1;
    public const int MaxBreakMinutes = 30;
    public const int DefaultLongBreakMinutes = 15;
    public const int MinLongBreakMinutes = 5;
    public const int MaxLongBreakMinutes = 45;
    public const int DefaultPomodorosUntilLongBreak = 4;
    public const int MinPomodorosUntilLongBreak = 2;
    public const int MaxPomodorosUntilLongBreak = 8;
    public const int DefaultDailyGoalTarget = 8;
    public const int MinDailyGoalTarget = 1;
    public const int MaxDailyGoalTarget = 20;
}
```

`windows/PomodoroWindows.Core/ColorTheme.cs`:
```csharp
namespace PomodoroWindows.Core;

public enum ColorTheme
{
    Default,
    Ocean,
    Forest,
    Lavender
}
```

`windows/PomodoroWindows.Core/TimerState.cs`:
```csharp
namespace PomodoroWindows.Core;

public sealed record TimerState(
    TimerPhase Phase,
    bool IsRunning,
    long RemainingMillis,
    long TotalMillis,
    int CompletedPomodoros = 0);
```

- [ ] **Step 2: Write a test locking in record value-equality**

`windows/PomodoroWindows.Core.Tests/TimerStateTests.cs`:
```csharp
namespace PomodoroWindows.Core.Tests;

public class TimerStateTests
{
    [Fact]
    public void Two_states_with_the_same_field_values_are_equal()
    {
        var a = new TimerState(TimerPhase.Focus, true, 1000L, 2000L, 3);
        var b = new TimerState(TimerPhase.Focus, true, 1000L, 2000L, 3);

        Assert.Equal(a, b);
    }
}
```

- [ ] **Step 3: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: `Passed! - Failed: 0, Passed: 2`

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows.Core windows/PomodoroWindows.Core.Tests
git commit -m "feat: add TimerPhase, TimerDefaults, ColorTheme, TimerState"
```

---

### Task 3: DailyGoalProgress / DailyGoalTracker

Ported 1:1 from `core/src/main/kotlin/com/pomodoro/timer/core/DailyGoalProgress.kt` and its test.

**Files:**
- Create: `windows/PomodoroWindows.Core/DailyGoalProgress.cs`
- Test: `windows/PomodoroWindows.Core.Tests/DailyGoalTrackerTests.cs`

**Interfaces:**
- Produces: `DailyGoalProgress(string Date, int Count)` record; `static class DailyGoalTracker` with `ForToday(DailyGoalProgress?, string today)`, `RecordCompletion(DailyGoalProgress?, string today)`, `JustReachedGoal(int previousCount, int count, int target)`.

- [ ] **Step 1: Write the failing tests**

`windows/PomodoroWindows.Core.Tests/DailyGoalTrackerTests.cs`:
```csharp
namespace PomodoroWindows.Core.Tests;

public class DailyGoalTrackerTests
{
    [Fact]
    public void ForToday_returns_stored_progress_unchanged_when_dates_match()
    {
        var stored = new DailyGoalProgress("2026-08-19", 3);

        var result = DailyGoalTracker.ForToday(stored, today: "2026-08-19");

        Assert.Equal(stored, result);
    }

    [Fact]
    public void ForToday_zeroes_out_on_a_date_change()
    {
        var stored = new DailyGoalProgress("2026-08-18", 5);

        var result = DailyGoalTracker.ForToday(stored, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 0), result);
    }

    [Fact]
    public void ForToday_zeroes_out_when_stored_is_null()
    {
        var result = DailyGoalTracker.ForToday(null, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 0), result);
    }

    [Fact]
    public void RecordCompletion_increments_within_a_day()
    {
        var current = new DailyGoalProgress("2026-08-19", 3);

        var result = DailyGoalTracker.RecordCompletion(current, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 4), result);
    }

    [Fact]
    public void RecordCompletion_restarts_at_1_across_a_date_change()
    {
        var current = new DailyGoalProgress("2026-08-18", 7);

        var result = DailyGoalTracker.RecordCompletion(current, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 1), result);
    }

    [Fact]
    public void RecordCompletion_starts_at_1_when_current_is_null()
    {
        var result = DailyGoalTracker.RecordCompletion(null, today: "2026-08-19");

        Assert.Equal(new DailyGoalProgress("2026-08-19", 1), result);
    }

    [Fact]
    public void JustReachedGoal_is_true_only_on_the_exact_crossing()
    {
        Assert.True(DailyGoalTracker.JustReachedGoal(previousCount: 7, count: 8, target: 8));
    }

    [Fact]
    public void JustReachedGoal_is_false_below_the_target()
    {
        Assert.False(DailyGoalTracker.JustReachedGoal(previousCount: 6, count: 7, target: 8));
    }

    [Fact]
    public void JustReachedGoal_is_false_once_already_past_the_target()
    {
        Assert.False(DailyGoalTracker.JustReachedGoal(previousCount: 8, count: 9, target: 8));
    }
}
```

- [ ] **Step 2: Run tests and verify they fail to compile (types don't exist yet)**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: build error, `DailyGoalProgress`/`DailyGoalTracker` not found.

- [ ] **Step 3: Implement**

`windows/PomodoroWindows.Core/DailyGoalProgress.cs`:
```csharp
namespace PomodoroWindows.Core;

public sealed record DailyGoalProgress(string Date, int Count);

public static class DailyGoalTracker
{
    public static DailyGoalProgress ForToday(DailyGoalProgress? stored, string today) =>
        stored is not null && stored.Date == today ? stored : new DailyGoalProgress(today, 0);

    public static DailyGoalProgress RecordCompletion(DailyGoalProgress? current, string today)
    {
        var baseProgress = ForToday(current, today);
        return baseProgress with { Count = baseProgress.Count + 1 };
    }

    public static bool JustReachedGoal(int previousCount, int count, int target) =>
        previousCount < target && count >= target;
}
```

- [ ] **Step 4: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: `Passed! - Failed: 0, Passed: 11`

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows.Core/DailyGoalProgress.cs windows/PomodoroWindows.Core.Tests/DailyGoalTrackerTests.cs
git commit -m "feat: port DailyGoalProgress/DailyGoalTracker from Kotlin core"
```

---

### Task 4: NotificationContent

Ported 1:1 from `NotificationContent.kt`. Korean strings copied verbatim.

**Files:**
- Create: `windows/PomodoroWindows.Core/NotificationContent.cs`
- Test: `windows/PomodoroWindows.Core.Tests/NotificationContentTests.cs`

**Interfaces:**
- Produces: `static class NotificationContent` with `OngoingTitle(TimerPhase)`, `OngoingText(long remainingMillis)`, `SessionEndTitle(TimerPhase newPhase)`, `DailyGoalReachedTitle(int target)`, `ToggleActionLabel(bool isRunning)` — all `string`-returning.

- [ ] **Step 1: Write the failing tests**

`windows/PomodoroWindows.Core.Tests/NotificationContentTests.cs`:
```csharp
namespace PomodoroWindows.Core.Tests;

public class NotificationContentTests
{
    [Fact]
    public void Ongoing_title_reflects_phase()
    {
        Assert.Equal("집중 중", NotificationContent.OngoingTitle(TimerPhase.Focus));
        Assert.Equal("휴식 중", NotificationContent.OngoingTitle(TimerPhase.ShortBreak));
        Assert.Equal("긴 휴식 중", NotificationContent.OngoingTitle(TimerPhase.LongBreak));
    }

    [Fact]
    public void Ongoing_text_formats_remaining_time_as_mm_colon_ss()
    {
        Assert.Equal("12:34 남음", NotificationContent.OngoingText(754_000L));
        Assert.Equal("00:05 남음", NotificationContent.OngoingText(5_000L));
        Assert.Equal("00:00 남음", NotificationContent.OngoingText(0L));
    }

    [Fact]
    public void Session_end_title_reflects_the_phase_just_entered()
    {
        Assert.Equal("휴식 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.ShortBreak));
        Assert.Equal("긴 휴식 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.LongBreak));
        Assert.Equal("다시 집중할 시간입니다!", NotificationContent.SessionEndTitle(TimerPhase.Focus));
    }

    [Fact]
    public void Daily_goal_reached_title_includes_the_target_count()
    {
        Assert.Equal("오늘의 목표 8개를 달성했어요!", NotificationContent.DailyGoalReachedTitle(8));
        Assert.Equal("오늘의 목표 1개를 달성했어요!", NotificationContent.DailyGoalReachedTitle(1));
    }

    [Fact]
    public void Toggle_action_label_reflects_whether_the_timer_is_running()
    {
        Assert.Equal("일시정지", NotificationContent.ToggleActionLabel(isRunning: true));
        Assert.Equal("재생", NotificationContent.ToggleActionLabel(isRunning: false));
    }
}
```

- [ ] **Step 2: Run tests and verify they fail to compile**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: build error, `NotificationContent` not found.

- [ ] **Step 3: Implement**

`windows/PomodoroWindows.Core/NotificationContent.cs`:
```csharp
namespace PomodoroWindows.Core;

public static class NotificationContent
{
    public static string OngoingTitle(TimerPhase phase) => phase switch
    {
        TimerPhase.Focus => "집중 중",
        TimerPhase.ShortBreak => "휴식 중",
        TimerPhase.LongBreak => "긴 휴식 중",
        _ => throw new ArgumentOutOfRangeException(nameof(phase))
    };

    public static string OngoingText(long remainingMillis)
    {
        var totalSeconds = Math.Max(remainingMillis / 1000, 0);
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return $"{minutes:D2}:{seconds:D2} 남음";
    }

    public static string SessionEndTitle(TimerPhase newPhase) => newPhase switch
    {
        TimerPhase.ShortBreak => "휴식 시간입니다!",
        TimerPhase.LongBreak => "긴 휴식 시간입니다!",
        TimerPhase.Focus => "다시 집중할 시간입니다!",
        _ => throw new ArgumentOutOfRangeException(nameof(newPhase))
    };

    public static string DailyGoalReachedTitle(int target) => $"오늘의 목표 {target}개를 달성했어요!";

    public static string ToggleActionLabel(bool isRunning) => isRunning ? "일시정지" : "재생";
}
```

- [ ] **Step 4: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: `Passed! - Failed: 0, Passed: 16`

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows.Core/NotificationContent.cs windows/PomodoroWindows.Core.Tests/NotificationContentTests.cs
git commit -m "feat: port NotificationContent from Kotlin core"
```

---

### Task 5: TimerEngine

Ported from `TimerEngine.kt`, excluding `adoptRemote`/`SyncPayload` (sync is a future project — see Global Constraints). Test cases below are the `TimerEngineTest.kt` cases that don't touch `adoptRemote`.

**Files:**
- Create: `windows/PomodoroWindows.Core/TimerEngine.cs`
- Test: `windows/PomodoroWindows.Core.Tests/TimerEngineTests.cs`

**Interfaces:**
- Consumes: `TimerPhase`, `TimerState`, `TimerDefaults` (Task 2).
- Produces: `class TimerEngine` with constructor `(long focusMillis, long shortBreakMillis, long longBreakMillis = ..., int pomodorosUntilLongBreak = ..., bool autoStartBreak = false, bool autoStartFocus = false)` and methods `CurrentState(long now)`, `Start(long now)`, `Pause(long now)`, `Reset(long now)`, `Tick(long now)`, `UpdateDurations(long focusMillis, long shortBreakMillis, long longBreakMillis = ..., int pomodorosUntilLongBreak = ..., bool autoStartBreak = ..., bool autoStartFocus = ...)`, all returning `TimerState` except `UpdateDurations` (void). `now`/timestamps are `long` milliseconds since epoch (caller-supplied — no wall-clock reads inside the engine, matching the Kotlin design so the view model layer stays testable).

- [ ] **Step 1: Write the failing tests**

`windows/PomodoroWindows.Core.Tests/TimerEngineTests.cs`:
```csharp
namespace PomodoroWindows.Core.Tests;

public class TimerEngineTests
{
    private const long FocusMillis = 25 * 60_000L;
    private const long BreakMillis = 5 * 60_000L;

    [Fact]
    public void Initial_state_is_idle_focus_with_full_duration()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        var state = engine.CurrentState(now: 0L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
        Assert.Equal(FocusMillis, state.TotalMillis);
    }

    [Fact]
    public void Start_begins_counting_down_from_now()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);

        var state = engine.Tick(now: 10_000L);

        Assert.True(state.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, state.RemainingMillis);
    }

    [Fact]
    public void Pause_freezes_remaining_time_and_resume_continues_from_same_point()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        var paused = engine.Pause(now: 10_000L);
        Assert.False(paused.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, paused.RemainingMillis);

        var stillPaused = engine.CurrentState(now: 60_000L);
        Assert.Equal(FocusMillis - 10_000L, stillPaused.RemainingMillis);

        var resumed = engine.Start(now: 60_000L);
        Assert.True(resumed.IsRunning);
        Assert.Equal(FocusMillis - 10_000L, resumed.RemainingMillis);

        var laterTick = engine.Tick(now: 65_000L);
        Assert.Equal(FocusMillis - 15_000L, laterTick.RemainingMillis);
    }

    [Fact]
    public void Reset_returns_to_idle_focus_with_full_duration()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        engine.Tick(now: 10_000L);

        var state = engine.Reset(now: 10_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
    }

    [Fact]
    public void Tick_past_end_of_focus_phase_transitions_to_break_without_drift_when_autostart_is_enabled()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.True(state.IsRunning);
        Assert.Equal(BreakMillis - 5_000L, state.RemainingMillis);
        Assert.Equal(BreakMillis, state.TotalMillis);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_break_phase_transitions_back_to_focus_when_autostart_is_enabled()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Tick(now: FocusMillis + BreakMillis + 2_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.Equal(FocusMillis - 2_000L, state.RemainingMillis);
    }

    [Fact]
    public void Tick_while_paused_does_not_change_state()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        var before = engine.CurrentState(now: 0L);

        var after = engine.Tick(now: 999_000L);

        Assert.Equal(before, after);
    }

    [Fact]
    public void UpdateDurations_changes_the_duration_used_by_the_current_idle_phase()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.UpdateDurations(focusMillis: 10 * 60_000L, shortBreakMillis: 2 * 60_000L);

        var state = engine.CurrentState(now: 0L);

        Assert.Equal(10 * 60_000L, state.RemainingMillis);
        Assert.Equal(10 * 60_000L, state.TotalMillis);
    }

    [Fact]
    public void UpdateDurations_mid_session_does_not_change_the_running_phase_total_until_it_flips()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis);
        engine.Start(now: 0L);
        engine.Tick(now: 60_000L);

        engine.UpdateDurations(focusMillis: 10 * 60_000L, shortBreakMillis: 2 * 60_000L);

        var midSession = engine.CurrentState(now: 60_000L);
        Assert.Equal(FocusMillis, midSession.TotalMillis);
        Assert.Equal(FocusMillis - 60_000L, midSession.RemainingMillis);
        Assert.True(midSession.RemainingMillis <= midSession.TotalMillis);

        var afterFlip = engine.Tick(now: FocusMillis);
        Assert.Equal(TimerPhase.ShortBreak, afterFlip.Phase);
        Assert.Equal(2 * 60_000L, afterFlip.TotalMillis);
    }

    [Fact]
    public void Completing_the_Nth_focus_session_enters_LongBreak_and_resets_the_count_when_it_ends()
    {
        var engine = new TimerEngine(
            FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 2,
            autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);

        engine.Tick(now: FocusMillis);
        engine.Tick(now: FocusMillis + BreakMillis);

        var afterSecondFocus = engine.Tick(now: FocusMillis + BreakMillis + FocusMillis);
        Assert.Equal(TimerPhase.LongBreak, afterSecondFocus.Phase);
        Assert.Equal(2, afterSecondFocus.CompletedPomodoros);
        Assert.Equal(900_000L, afterSecondFocus.TotalMillis);

        var afterLongBreak = engine.Tick(now: FocusMillis + BreakMillis + FocusMillis + 900_000L);
        Assert.Equal(TimerPhase.Focus, afterLongBreak.Phase);
        Assert.Equal(0, afterLongBreak.CompletedPomodoros);
    }

    [Fact]
    public void The_default_threshold_of_4_enters_LongBreak_on_the_4th_focus_completion_not_the_3rd()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: true);
        engine.Start(now: 0L);

        long now = 0L;
        for (var cycle = 0; cycle < 3; cycle++)
        {
            now += FocusMillis;
            var afterFocus = engine.Tick(now: now);
            Assert.Equal(TimerPhase.ShortBreak, afterFocus.Phase);
            Assert.Equal(cycle + 1, afterFocus.CompletedPomodoros);

            now += BreakMillis;
            var afterBreak = engine.Tick(now: now);
            Assert.Equal(TimerPhase.Focus, afterBreak.Phase);
        }

        now += FocusMillis;
        var afterFourthFocus = engine.Tick(now: now);
        Assert.Equal(TimerPhase.LongBreak, afterFourthFocus.Phase);
        Assert.Equal(4, afterFourthFocus.CompletedPomodoros);
        Assert.Equal(TimerDefaults.DefaultLongBreakMinutes * 60_000L, afterFourthFocus.TotalMillis);
    }

    [Fact]
    public void Reset_preserves_the_completed_pomodoros_count()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 4);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Reset(now: FocusMillis);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void UpdateDurations_floors_pomodorosUntilLongBreak_at_1()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 4);
        engine.UpdateDurations(FocusMillis, BreakMillis, 900_000L, pomodorosUntilLongBreak: 0);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis);

        Assert.Equal(TimerPhase.LongBreak, state.Phase);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_focus_phase_pauses_at_ShortBreak_when_autoStartBreak_is_false()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(BreakMillis, state.RemainingMillis);
        Assert.Equal(BreakMillis, state.TotalMillis);
        Assert.Equal(1, state.CompletedPomodoros);
    }

    [Fact]
    public void Tick_past_end_of_break_phase_pauses_at_Focus_when_autoStartFocus_is_false()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: true, autoStartFocus: false);
        engine.Start(now: 0L);
        engine.Tick(now: FocusMillis);

        var state = engine.Tick(now: FocusMillis + BreakMillis + 2_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
        Assert.Equal(FocusMillis, state.TotalMillis);
    }

    [Fact]
    public void AutoStartFocus_not_autoStartBreak_governs_leaving_LongBreak()
    {
        var engine = new TimerEngine(
            FocusMillis, BreakMillis, longBreakMillis: 900_000L, pomodorosUntilLongBreak: 1,
            autoStartBreak: true, autoStartFocus: false);
        engine.Start(now: 0L);
        var enteredLongBreak = engine.Tick(now: FocusMillis);
        Assert.True(enteredLongBreak.IsRunning);

        var state = engine.Tick(now: FocusMillis + 900_000L + 1_000L);

        Assert.Equal(TimerPhase.Focus, state.Phase);
        Assert.False(state.IsRunning);
        Assert.Equal(FocusMillis, state.RemainingMillis);
    }

    [Fact]
    public void UpdateDurations_changes_which_future_transitions_auto_continue()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.UpdateDurations(FocusMillis, BreakMillis, autoStartBreak: true);
        engine.Start(now: 0L);

        var state = engine.Tick(now: FocusMillis + 5_000L);

        Assert.Equal(TimerPhase.ShortBreak, state.Phase);
        Assert.True(state.IsRunning);
    }

    [Fact]
    public void UpdateDurations_mid_session_does_not_retroactively_auto_continue_the_in_flight_phase()
    {
        var engine = new TimerEngine(FocusMillis, BreakMillis, autoStartBreak: false);
        engine.Start(now: 0L);
        engine.Tick(now: 60_000L);

        engine.UpdateDurations(FocusMillis, BreakMillis, autoStartBreak: true);

        var stillMidFocus = engine.CurrentState(now: 120_000L);
        Assert.Equal(TimerPhase.Focus, stillMidFocus.Phase);
        Assert.True(stillMidFocus.IsRunning);

        var afterBoundary = engine.Tick(now: FocusMillis + 5_000L);
        Assert.Equal(TimerPhase.ShortBreak, afterBoundary.Phase);
        Assert.True(afterBoundary.IsRunning);
    }
}
```

- [ ] **Step 2: Run tests and verify they fail to compile**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: build error, `TimerEngine` not found.

- [ ] **Step 3: Implement**

`windows/PomodoroWindows.Core/TimerEngine.cs`:
```csharp
namespace PomodoroWindows.Core;

public sealed class TimerEngine
{
    private static readonly long MinPhaseMillis = Math.Min(
        TimerDefaults.MinFocusMinutes,
        Math.Min(TimerDefaults.MinBreakMinutes, TimerDefaults.MinLongBreakMinutes)) * 60_000L;

    private long _focusMillis;
    private long _shortBreakMillis;
    private long _longBreakMillis;
    private int _pomodorosUntilLongBreak;
    private bool _autoStartBreak;
    private bool _autoStartFocus;

    private TimerPhase _phase = TimerPhase.Focus;
    private bool _isRunning;
    private long _endTimestamp;
    private long _remainingWhenIdle;
    private int _completedPomodoros;
    private long _currentPhaseDurationMillis;

    public TimerEngine(
        long focusMillis,
        long shortBreakMillis,
        long longBreakMillis = TimerDefaults.DefaultLongBreakMinutes * 60_000L,
        int pomodorosUntilLongBreak = TimerDefaults.DefaultPomodorosUntilLongBreak,
        bool autoStartBreak = false,
        bool autoStartFocus = false)
    {
        _focusMillis = focusMillis;
        _shortBreakMillis = shortBreakMillis;
        _longBreakMillis = longBreakMillis;
        _pomodorosUntilLongBreak = pomodorosUntilLongBreak;
        _autoStartBreak = autoStartBreak;
        _autoStartFocus = autoStartFocus;
        _remainingWhenIdle = focusMillis;
        _currentPhaseDurationMillis = focusMillis;
    }

    private long DurationFor(TimerPhase phase) => phase switch
    {
        TimerPhase.Focus => _focusMillis,
        TimerPhase.ShortBreak => _shortBreakMillis,
        TimerPhase.LongBreak => _longBreakMillis,
        _ => throw new ArgumentOutOfRangeException(nameof(phase))
    };

    private TimerPhase NextPhase()
    {
        switch (_phase)
        {
            case TimerPhase.Focus:
                _completedPomodoros += 1;
                return _completedPomodoros >= _pomodorosUntilLongBreak ? TimerPhase.LongBreak : TimerPhase.ShortBreak;
            case TimerPhase.ShortBreak:
                return TimerPhase.Focus;
            case TimerPhase.LongBreak:
                _completedPomodoros = 0;
                return TimerPhase.Focus;
            default:
                throw new ArgumentOutOfRangeException();
        }
    }

    private bool AutoStartAllowed(TimerPhase departingPhase) =>
        departingPhase == TimerPhase.Focus ? _autoStartBreak : _autoStartFocus;

    private void AdvanceAcrossPhaseBoundaries(long now)
    {
        while (_isRunning && now >= _endTimestamp)
        {
            var departingPhase = _phase;
            _phase = NextPhase();
            _currentPhaseDurationMillis = DurationFor(_phase);
            if (AutoStartAllowed(departingPhase))
            {
                _endTimestamp += _currentPhaseDurationMillis;
            }
            else
            {
                _isRunning = false;
                _remainingWhenIdle = _currentPhaseDurationMillis;
            }
        }
    }

    public TimerState CurrentState(long now)
    {
        var remaining = _isRunning ? Math.Max(_endTimestamp - now, 0) : _remainingWhenIdle;
        return new TimerState(_phase, _isRunning, remaining, _currentPhaseDurationMillis, _completedPomodoros);
    }

    public TimerState Start(long now)
    {
        if (!_isRunning)
        {
            _isRunning = true;
            _endTimestamp = now + _remainingWhenIdle;
        }
        return CurrentState(now);
    }

    public TimerState Pause(long now)
    {
        if (_isRunning)
        {
            _remainingWhenIdle = Math.Max(_endTimestamp - now, 0);
            _isRunning = false;
        }
        return CurrentState(now);
    }

    public TimerState Reset(long now)
    {
        _isRunning = false;
        _phase = TimerPhase.Focus;
        _remainingWhenIdle = _focusMillis;
        _currentPhaseDurationMillis = _focusMillis;
        return CurrentState(now);
    }

    public TimerState Tick(long now)
    {
        if (!_isRunning) return CurrentState(now);

        AdvanceAcrossPhaseBoundaries(now);
        if (_isRunning)
        {
            _remainingWhenIdle = Math.Max(_endTimestamp - now, 0);
        }
        return CurrentState(now);
    }

    public void UpdateDurations(
        long focusMillis,
        long shortBreakMillis,
        long? longBreakMillis = null,
        int? pomodorosUntilLongBreak = null,
        bool? autoStartBreak = null,
        bool? autoStartFocus = null)
    {
        _focusMillis = Math.Max(focusMillis, MinPhaseMillis);
        _shortBreakMillis = Math.Max(shortBreakMillis, MinPhaseMillis);
        _longBreakMillis = Math.Max(longBreakMillis ?? _longBreakMillis, MinPhaseMillis);
        _pomodorosUntilLongBreak = Math.Max(pomodorosUntilLongBreak ?? _pomodorosUntilLongBreak, 1);
        _autoStartBreak = autoStartBreak ?? _autoStartBreak;
        _autoStartFocus = autoStartFocus ?? _autoStartFocus;
        if (!_isRunning)
        {
            _remainingWhenIdle = DurationFor(_phase);
            _currentPhaseDurationMillis = DurationFor(_phase);
        }
    }
}
```

**Note on `UpdateDurations`:** Kotlin uses positional defaults (`= this.longBreakMillis`, etc.) which C# cannot express for instance-state defaults in a method signature — nullable parameters with `?? currentField` fallback reproduce the same "omit to keep current value" behavior. Callers in later tasks pass named arguments (e.g. `engine.UpdateDurations(focusMillis, breakMillis, autoStartBreak: true)`), matching the test calls above.

- [ ] **Step 4: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test
```

Expected: `Passed! - Failed: 0, Passed: 34`

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows.Core/TimerEngine.cs windows/PomodoroWindows.Core.Tests/TimerEngineTests.cs
git commit -m "feat: port TimerEngine from Kotlin core (excluding sync/adoptRemote)"
```

---

### Task 6: WinUI 3 app project scaffolding

Creates the packaged WinUI 3 app shell referencing `PomodoroWindows.Core`, with a dev signing certificate so MSIX builds locally. No business logic yet — this task's deliverable is "the app builds and an empty window opens."

**Files:**
- Create: `windows/PomodoroWindows/PomodoroWindows.csproj`
- Create: `windows/PomodoroWindows/app.manifest`
- Create: `windows/PomodoroWindows/Package.appxmanifest`
- Create: `windows/PomodoroWindows/Assets/*.png` (placeholder icons)
- Create: `windows/PomodoroWindows/App.xaml` / `App.xaml.cs`
- Create: `windows/PomodoroWindows/MainWindow.xaml` / `MainWindow.xaml.cs`

**Interfaces:**
- Consumes: `PomodoroWindows.Core` (project reference).
- Produces: a launchable `PomodoroWindows` app; `MainWindow` with an empty `Frame x:Name="RootFrame"` that Task 8+ navigate.

- [ ] **Step 1: Create the project file**

`windows/PomodoroWindows/PomodoroWindows.csproj`:
```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>WinExe</OutputType>
    <TargetFramework>net8.0-windows10.0.19041.0</TargetFramework>
    <TargetPlatformMinVersion>10.0.17763.0</TargetPlatformMinVersion>
    <RootNamespace>PomodoroWindows</RootNamespace>
    <ApplicationManifest>app.manifest</ApplicationManifest>
    <Platforms>x64;arm64</Platforms>
    <RuntimeIdentifiers>win-x64;win-arm64</RuntimeIdentifiers>
    <UseWinUI>true</UseWinUI>
    <EnableMsixTooling>true</EnableMsixTooling>
    <WindowsPackageType>MSIX</WindowsPackageType>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <PublishProfile>win-$(Platform).pubxml</PublishProfile>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.WindowsAppSDK" Version="1.8.260209005" />
    <PackageReference Include="Microsoft.Windows.SDK.BuildTools" Version="10.0.28000.2526" />
    <PackageReference Include="CommunityToolkit.Mvvm" Version="8.4.2" />
    <PackageReference Include="H.NotifyIcon.WinUI" Version="2.3.2" />
  </ItemGroup>
  <ItemGroup>
    <ProjectReference Include="..\PomodoroWindows.Core\PomodoroWindows.Core.csproj" />
  </ItemGroup>
  <ItemGroup>
    <Manifest Include="$(ApplicationManifest)" />
  </ItemGroup>
</Project>
```

`windows/PomodoroWindows/app.manifest`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<assembly manifestVersion="1.0" xmlns="urn:schemas-microsoft-com:asm.v1">
  <application xmlns="urn:schemas-microsoft-com:asm.v3">
    <windowsSettings>
      <activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>
    </windowsSettings>
  </application>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}" />
      <supportedOS Id="{1f676c76-80e1-4239-95bb-83d0f6d0da78}" />
      <supportedOS Id="{4a2f28e3-53b9-4441-ba9c-d69d4a4a6e38}" />
      <supportedOS Id="{35138b9a-5d96-4fbd-8e2d-a2440225f93a}" />
      <supportedOS Id="{e2011457-1546-43c5-a5fe-008deee3d3f0}" />
    </application>
  </compatibility>
</assembly>
```

`windows/PomodoroWindows/Package.appxmanifest`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<Package
  xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
  xmlns:mp="http://schemas.microsoft.com/appx/2014/phone/manifest"
  xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
  xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
  IgnorableNamespaces="uap mp rescap">

  <Identity Name="PomodoroWindows" Publisher="CN=PomodoroWindowsDev" Version="1.0.0.0" />

  <mp:PhoneIdentity PhoneProductId="a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d" PhonePublisherId="00000000-0000-0000-0000-000000000000"/>

  <Properties>
    <DisplayName>Pomodoro</DisplayName>
    <PublisherDisplayName>PomodoroWindowsDev</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>

  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.22621.0" />
  </Dependencies>

  <Resources>
    <Resource Language="ko-kr"/>
  </Resources>

  <Applications>
    <Application Id="App" Executable="$targetnametoken$.exe" EntryPoint="$targetentrypoint$">
      <uap:VisualElements
        DisplayName="Pomodoro"
        Description="뽀모도로 타이머"
        BackgroundColor="transparent"
        Square150x150Logo="Assets\Square150x150Logo.png"
        Square44x44Logo="Assets\Square44x44Logo.png">
        <uap:DefaultTile Wide310x150Logo="Assets\Wide310x150Logo.png" />
        <uap:SplashScreen Image="Assets\SplashScreen.png" />
      </uap:VisualElements>
    </Application>
  </Applications>

  <Capabilities>
    <rescap:Capability Name="runFullTrust" />
  </Capabilities>
</Package>
```

- [ ] **Step 2: Add placeholder icon assets**

WinUI 3 requires these five PNGs to exist and be referenced from `Package.appxmanifest`; exact artwork is out of scope for this task, so generate flat-color placeholders:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows\PomodoroWindows
mkdir Assets
Add-Type -AssemblyName System.Drawing
function New-PlaceholderPng($path, $w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(255, 232, 93, 74))
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}
New-PlaceholderPng "Assets\Square150x150Logo.png" 150 150
New-PlaceholderPng "Assets\Square44x44Logo.png" 44 44
New-PlaceholderPng "Assets\StoreLogo.png" 50 50
New-PlaceholderPng "Assets\SplashScreen.png" 620 300
New-PlaceholderPng "Assets\Wide310x150Logo.png" 310 150
```

- [ ] **Step 3: Write App.xaml / App.xaml.cs**

`windows/PomodoroWindows/App.xaml`:
```xml
<Application
    x:Class="PomodoroWindows.App"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <XamlControlsResources xmlns="using:Microsoft.UI.Xaml.Controls" />
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
```

`windows/PomodoroWindows/App.xaml.cs`:
```csharp
using Microsoft.UI.Xaml;

namespace PomodoroWindows;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }
}
```

- [ ] **Step 4: Write MainWindow.xaml / MainWindow.xaml.cs**

`windows/PomodoroWindows/MainWindow.xaml`:
```xml
<Window
    x:Class="PomodoroWindows.MainWindow"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Frame x:Name="RootFrame" x:FieldModifier="public"/>
</Window>
```

`windows/PomodoroWindows/MainWindow.xaml.cs`:
```csharp
using Microsoft.UI.Xaml;

namespace PomodoroWindows;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Title = "Pomodoro";
    }
}
```

- [ ] **Step 5: Add the project to the solution and build**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet sln add PomodoroWindows\PomodoroWindows.csproj
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
```

Expected: `Build succeeded.` If it instead fails with a certificate/signing error, run Step 6 first, then retry this build.

- [ ] **Step 6: Create and trust a dev signing certificate (only if Step 5's build fails on signing)**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
$cert = New-SelfSignedCertificate -Type Custom -Subject "CN=PomodoroWindowsDev" -KeyUsage DigitalSignature -FriendlyName "PomodoroWindows Dev Cert" -CertStoreLocation "Cert:\CurrentUser\My" -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3","2.5.29.19={text}Subject Type:End Entity")
Export-Certificate -Cert $cert -FilePath "D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows\PomodoroWindows\PomodoroWindowsDev.cer"
Import-Certificate -FilePath "D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows\PomodoroWindows\PomodoroWindowsDev.cer" -CertStoreLocation "Cert:\LocalMachine\TrustedPeople"
```

Then re-run the Step 5 build command. `Package.appxmanifest`'s `Identity Publisher` (`CN=PomodoroWindowsDev`) already matches this certificate's subject, so no manifest edit should be needed.

- [ ] **Step 7: Run the app and verify a blank window titled "Pomodoro" opens**

`dotnet run` does not work for this multi-platform packaged project — use the build+register+launch pattern from Global Constraints instead:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

Expected: a `PomodoroWindows` process is running with `MainWindowTitle` exactly `Pomodoro`. Then close it: `Stop-Process -Name PomodoroWindows -ErrorAction SilentlyContinue`. (This requires Windows Developer Mode to be enabled — see Global Constraints; if `Add-AppxPackage` fails with a developer-license error, stop and ask the user to enable it, per this plan's stop-and-ask policy for security-sensitive/admin-gated changes.)

- [ ] **Step 8: Commit**

```bash
git add windows/PomodoroWindows windows/PomodoroWindows.sln
git commit -m "feat: scaffold WinUI 3 app project with MSIX packaging"
```

---

### Task 7: TimerSettings model and settings storage

**Files:**
- Create: `windows/PomodoroWindows/Models/TimerSettings.cs`
- Create: `windows/PomodoroWindows/Services/ISettingsStore.cs`
- Create: `windows/PomodoroWindows/Services/ApplicationDataSettingsStore.cs`
- Create: `windows/PomodoroWindows/Services/FakeSettingsStore.cs`

**Interfaces:**
- Produces: `TimerSettings` record mirroring Kotlin `TimerSettings` (minus `Vibration`, `OnboardingCompleted`); `interface ISettingsStore { TimerSettings Load(); void Save(TimerSettings settings); }`; `ApplicationDataSettingsStore` (real, WinRT-backed — build-verified, not unit-tested per Global Constraints); `FakeSettingsStore` (in-memory, used by Task 9's view-model tests).

- [ ] **Step 1: Write the model and interface**

`windows/PomodoroWindows/Models/TimerSettings.cs`:
```csharp
using PomodoroWindows.Core;

namespace PomodoroWindows.Models;

public sealed record TimerSettings(
    int FocusMinutes = TimerDefaults.DefaultFocusMinutes,
    int BreakMinutes = TimerDefaults.DefaultBreakMinutes,
    int LongBreakMinutes = TimerDefaults.DefaultLongBreakMinutes,
    int PomodorosUntilLongBreak = TimerDefaults.DefaultPomodorosUntilLongBreak,
    bool AutoStartBreak = false,
    bool AutoStartFocus = false,
    bool KeepScreenOn = false,
    bool DailyGoalEnabled = false,
    int DailyGoalTarget = TimerDefaults.DefaultDailyGoalTarget,
    bool NotificationsEnabled = true,
    bool UseAlarmSound = false,
    ColorTheme ColorTheme = ColorTheme.Default);
```

`windows/PomodoroWindows/Services/ISettingsStore.cs`:
```csharp
using PomodoroWindows.Models;

namespace PomodoroWindows.Services;

public interface ISettingsStore
{
    TimerSettings Load();
    void Save(TimerSettings settings);
}
```

- [ ] **Step 2: Implement the real (WinRT-backed) store**

`windows/PomodoroWindows/Services/ApplicationDataSettingsStore.cs`:
```csharp
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using Windows.Storage;

namespace PomodoroWindows.Services;

public sealed class ApplicationDataSettingsStore : ISettingsStore
{
    private readonly ApplicationDataContainer _container = ApplicationData.Current.LocalSettings;

    public TimerSettings Load()
    {
        var defaults = new TimerSettings();
        return new TimerSettings(
            FocusMinutes: GetInt(nameof(TimerSettings.FocusMinutes), defaults.FocusMinutes),
            BreakMinutes: GetInt(nameof(TimerSettings.BreakMinutes), defaults.BreakMinutes),
            LongBreakMinutes: GetInt(nameof(TimerSettings.LongBreakMinutes), defaults.LongBreakMinutes),
            PomodorosUntilLongBreak: GetInt(nameof(TimerSettings.PomodorosUntilLongBreak), defaults.PomodorosUntilLongBreak),
            AutoStartBreak: GetBool(nameof(TimerSettings.AutoStartBreak), defaults.AutoStartBreak),
            AutoStartFocus: GetBool(nameof(TimerSettings.AutoStartFocus), defaults.AutoStartFocus),
            KeepScreenOn: GetBool(nameof(TimerSettings.KeepScreenOn), defaults.KeepScreenOn),
            DailyGoalEnabled: GetBool(nameof(TimerSettings.DailyGoalEnabled), defaults.DailyGoalEnabled),
            DailyGoalTarget: GetInt(nameof(TimerSettings.DailyGoalTarget), defaults.DailyGoalTarget),
            NotificationsEnabled: GetBool(nameof(TimerSettings.NotificationsEnabled), defaults.NotificationsEnabled),
            UseAlarmSound: GetBool(nameof(TimerSettings.UseAlarmSound), defaults.UseAlarmSound),
            ColorTheme: (ColorTheme)GetInt(nameof(TimerSettings.ColorTheme), (int)defaults.ColorTheme));
    }

    public void Save(TimerSettings settings)
    {
        _container.Values[nameof(TimerSettings.FocusMinutes)] = settings.FocusMinutes;
        _container.Values[nameof(TimerSettings.BreakMinutes)] = settings.BreakMinutes;
        _container.Values[nameof(TimerSettings.LongBreakMinutes)] = settings.LongBreakMinutes;
        _container.Values[nameof(TimerSettings.PomodorosUntilLongBreak)] = settings.PomodorosUntilLongBreak;
        _container.Values[nameof(TimerSettings.AutoStartBreak)] = settings.AutoStartBreak;
        _container.Values[nameof(TimerSettings.AutoStartFocus)] = settings.AutoStartFocus;
        _container.Values[nameof(TimerSettings.KeepScreenOn)] = settings.KeepScreenOn;
        _container.Values[nameof(TimerSettings.DailyGoalEnabled)] = settings.DailyGoalEnabled;
        _container.Values[nameof(TimerSettings.DailyGoalTarget)] = settings.DailyGoalTarget;
        _container.Values[nameof(TimerSettings.NotificationsEnabled)] = settings.NotificationsEnabled;
        _container.Values[nameof(TimerSettings.UseAlarmSound)] = settings.UseAlarmSound;
        _container.Values[nameof(TimerSettings.ColorTheme)] = (int)settings.ColorTheme;
    }

    private int GetInt(string key, int fallback) =>
        _container.Values.TryGetValue(key, out var value) && value is int i ? i : fallback;

    private bool GetBool(string key, bool fallback) =>
        _container.Values.TryGetValue(key, out var value) && value is bool b ? b : fallback;
}
```

- [ ] **Step 3: Implement the fake store for tests**

`windows/PomodoroWindows/Services/FakeSettingsStore.cs`:
```csharp
using PomodoroWindows.Models;

namespace PomodoroWindows.Services;

public sealed class FakeSettingsStore : ISettingsStore
{
    public TimerSettings Current { get; private set; } = new();

    public TimerSettings Load() => Current;

    public void Save(TimerSettings settings) => Current = settings;
}
```

- [ ] **Step 4: Build and verify no compile errors**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
```

Expected: `Build succeeded.`

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows/Models windows/PomodoroWindows/Services
git commit -m "feat: add TimerSettings model and settings store (real + fake)"
```

---

### Task 8: TimerViewModel (unit-tested) and TimerTickService

The view model is the piece with real logic (deciding when to notify, when to record a daily-goal completion), so it gets a real TDD cycle in a new test project. The `DispatcherQueueTimer` wiring is a thin, untested adapter (Step 5) — everything it calls is already covered.

**Files:**
- Create: `windows/PomodoroWindows.Tests/PomodoroWindows.Tests.csproj`
- Create: `windows/PomodoroWindows.Tests/TimerViewModelTests.cs`
- Create: `windows/PomodoroWindows/Services/INotificationService.cs`
- Create: `windows/PomodoroWindows/Services/FakeNotificationService.cs`
- Create: `windows/PomodoroWindows/ViewModels/TimerViewModel.cs`
- Create: `windows/PomodoroWindows/Services/TimerTickService.cs`

**Interfaces:**
- Consumes: `TimerEngine`, `DailyGoalTracker`, `NotificationContent` (Core); `ISettingsStore`, `TimerSettings` (Task 7).
- Produces: `interface INotificationService { void NotifySessionEnd(TimerPhase newPhase); void NotifyDailyGoalReached(int target); }`; `class TimerViewModel : ObservableObject` with observable properties `Phase`, `IsRunning`, `RemainingMillis`, `TotalMillis`, `CompletedPomodoros`, `DailyGoalCount`, commands `StartPauseCommand`, `ResetCommand`, and a public `void Tick(DateTimeOffset now)` that later tasks (and the tick service) call every second; `TimerTickService` (production `DispatcherQueueTimer` adapter, calls `viewModel.Tick(DateTimeOffset.UtcNow)` every second).

- [ ] **Step 1: Create the view-model test project**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet new xunit -n PomodoroWindows.Tests -o PomodoroWindows.Tests --framework net8.0-windows10.0.19041.0
Remove-Item PomodoroWindows.Tests\UnitTest1.cs
dotnet add PomodoroWindows.Tests\PomodoroWindows.Tests.csproj reference PomodoroWindows\PomodoroWindows.csproj
dotnet sln add PomodoroWindows.Tests\PomodoroWindows.Tests.csproj
```

Note the test TFM matches the app's Windows-flavored TFM (`net8.0-windows10.0.19041.0`) so it can reference WinUI-touching types transitively, even though `TimerViewModel` itself only touches `ObservableObject`/`RelayCommand` (no live WinRT calls) and so is safe to instantiate directly in xUnit.

- [ ] **Step 2: Write `INotificationService` and its fake**

`windows/PomodoroWindows/Services/INotificationService.cs`:
```csharp
using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public interface INotificationService
{
    void NotifySessionEnd(TimerPhase newPhase);
    void NotifyDailyGoalReached(int target);
}
```

`windows/PomodoroWindows/Services/FakeNotificationService.cs`:
```csharp
using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public sealed class FakeNotificationService : INotificationService
{
    public List<TimerPhase> SessionEndCalls { get; } = new();
    public List<int> DailyGoalReachedCalls { get; } = new();

    public void NotifySessionEnd(TimerPhase newPhase) => SessionEndCalls.Add(newPhase);
    public void NotifyDailyGoalReached(int target) => DailyGoalReachedCalls.Add(target);
}
```

- [ ] **Step 3: Write the failing view-model tests**

`windows/PomodoroWindows.Tests/TimerViewModelTests.cs`:
```csharp
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Tests;

public class TimerViewModelTests
{
    private static TimerViewModel CreateViewModel(
        FakeSettingsStore? settingsStore = null,
        FakeNotificationService? notificationService = null,
        TimerSettings? initialSettings = null)
    {
        var store = settingsStore ?? new FakeSettingsStore();
        if (initialSettings is not null) store.Save(initialSettings);
        var notifications = notificationService ?? new FakeNotificationService();
        return new TimerViewModel(store, notifications);
    }

    [Fact]
    public void Initial_state_is_idle_focus_with_full_duration()
    {
        var vm = CreateViewModel(initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5));

        Assert.Equal(TimerPhase.Focus, vm.Phase);
        Assert.False(vm.IsRunning);
        Assert.Equal(25 * 60_000L, vm.RemainingMillis);
    }

    [Fact]
    public void StartPauseCommand_toggles_running_state()
    {
        var vm = CreateViewModel();
        var start = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.Tick(start);

        vm.StartPauseCommand.Execute(start);
        Assert.True(vm.IsRunning);

        vm.StartPauseCommand.Execute(start);
        Assert.False(vm.IsRunning);
    }

    [Fact]
    public void ResetCommand_returns_to_idle_focus()
    {
        var vm = CreateViewModel(initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);
        vm.Tick(DateTimeOffset.FromUnixTimeMilliseconds(10_000));

        vm.ResetCommand.Execute(null);

        Assert.Equal(TimerPhase.Focus, vm.Phase);
        Assert.False(vm.IsRunning);
        Assert.Equal(25 * 60_000L, vm.RemainingMillis);
    }

    [Fact]
    public void Tick_past_a_phase_boundary_with_notifications_enabled_fires_session_end_notification()
    {
        var notifications = new FakeNotificationService();
        var vm = CreateViewModel(
            notificationService: notifications,
            initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, NotificationsEnabled: true));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Equal(TimerPhase.ShortBreak, vm.Phase);
        Assert.Equal(new[] { TimerPhase.ShortBreak }, notifications.SessionEndCalls);
    }

    [Fact]
    public void Tick_past_a_phase_boundary_with_notifications_disabled_does_not_notify()
    {
        var notifications = new FakeNotificationService();
        var vm = CreateViewModel(
            notificationService: notifications,
            initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, NotificationsEnabled: false));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Empty(notifications.SessionEndCalls);
    }

    [Fact]
    public void Completing_a_focus_session_records_a_daily_goal_completion_and_notifies_on_the_exact_crossing()
    {
        var notifications = new FakeNotificationService();
        var settingsStore = new FakeSettingsStore();
        var vm = CreateViewModel(
            settingsStore, notifications,
            new TimerSettings(FocusMinutes: 25, BreakMinutes: 5, AutoStartBreak: false, DailyGoalEnabled: true, DailyGoalTarget: 1));
        var t0 = DateTimeOffset.FromUnixTimeMilliseconds(0);
        vm.StartPauseCommand.Execute(t0);

        vm.Tick(t0.AddMilliseconds(25 * 60_000L + 1_000L));

        Assert.Equal(1, vm.DailyGoalCount);
        Assert.Equal(new[] { 1 }, notifications.DailyGoalReachedCalls);
    }
}
```

- [ ] **Step 4: Run tests and verify they fail to compile**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false
```

Expected: build error, `TimerViewModel` not found.

- [ ] **Step 5: Implement `TimerViewModel`**

`windows/PomodoroWindows/ViewModels/TimerViewModel.cs`:
```csharp
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;

namespace PomodoroWindows.ViewModels;

public sealed partial class TimerViewModel : ObservableObject
{
    private readonly ISettingsStore _settingsStore;
    private readonly INotificationService _notificationService;
    private readonly TimerEngine _engine;

    private TimerSettings _settings;
    private DailyGoalProgress? _dailyGoalProgress;

    [ObservableProperty] private TimerPhase phase;
    [ObservableProperty] private bool isRunning;
    [ObservableProperty] private long remainingMillis;
    [ObservableProperty] private long totalMillis;
    [ObservableProperty] private int completedPomodoros;
    [ObservableProperty] private int dailyGoalCount;

    public TimerViewModel(ISettingsStore settingsStore, INotificationService notificationService)
    {
        _settingsStore = settingsStore;
        _notificationService = notificationService;
        _settings = _settingsStore.Load();
        _engine = new TimerEngine(
            _settings.FocusMinutes * 60_000L,
            _settings.BreakMinutes * 60_000L,
            _settings.LongBreakMinutes * 60_000L,
            _settings.PomodorosUntilLongBreak,
            _settings.AutoStartBreak,
            _settings.AutoStartFocus);
        ApplyState(_engine.CurrentState(0L));
    }

    [RelayCommand]
    private void StartPause(DateTimeOffset now)
    {
        var nowMillis = now.ToUnixTimeMilliseconds();
        var next = IsRunning ? _engine.Pause(nowMillis) : _engine.Start(nowMillis);
        ApplyState(next);
    }

    [RelayCommand]
    private void Reset()
    {
        ApplyState(_engine.Reset(0L));
    }

    public void Tick(DateTimeOffset now)
    {
        var previousPhase = Phase;
        var previousCompleted = CompletedPomodoros;

        var next = _engine.Tick(now.ToUnixTimeMilliseconds());
        ApplyState(next);

        if (next.Phase != previousPhase)
        {
            if (_settings.NotificationsEnabled)
            {
                _notificationService.NotifySessionEnd(next.Phase);
            }
            if (previousPhase == TimerPhase.Focus)
            {
                RecordDailyGoalCompletion();
            }
        }
    }

    private void RecordDailyGoalCompletion()
    {
        var today = DateTimeOffset.UtcNow.ToString("yyyy-MM-dd");
        var previous = DailyGoalTracker.ForToday(_dailyGoalProgress, today);
        var updated = DailyGoalTracker.RecordCompletion(_dailyGoalProgress, today);
        _dailyGoalProgress = updated;
        DailyGoalCount = updated.Count;

        if (_settings.DailyGoalEnabled &&
            DailyGoalTracker.JustReachedGoal(previous.Count, updated.Count, _settings.DailyGoalTarget))
        {
            _notificationService.NotifyDailyGoalReached(_settings.DailyGoalTarget);
        }
    }

    private void ApplyState(TimerState state)
    {
        Phase = state.Phase;
        IsRunning = state.IsRunning;
        RemainingMillis = state.RemainingMillis;
        TotalMillis = state.TotalMillis;
        CompletedPomodoros = state.CompletedPomodoros;
    }
}
```

- [ ] **Step 6: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false
```

Expected: `Passed! - Failed: 0, Passed: 6`

- [ ] **Step 7: Add the production `DispatcherQueueTimer` adapter (not unit-tested — thin wiring only)**

`windows/PomodoroWindows/Services/TimerTickService.cs`:
```csharp
using Microsoft.UI.Dispatching;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Services;

public sealed class TimerTickService
{
    private readonly DispatcherQueueTimer _timer;

    public TimerTickService(DispatcherQueue dispatcherQueue, TimerViewModel viewModel)
    {
        _timer = dispatcherQueue.CreateTimer();
        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += (_, _) => viewModel.Tick(DateTimeOffset.UtcNow);
    }

    public void Start() => _timer.Start();
    public void Stop() => _timer.Stop();
}
```

- [ ] **Step 8: Build the whole solution**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build
```

(No `-p:Platform=x64` here — see Global Constraints: forcing it on a solution-level build breaks `PomodoroWindows.sln`'s per-project platform routing with `MSB4126`. Plain `dotnet build` uses the existing routing correctly.)

Expected: `Build succeeded.`

- [ ] **Step 9: Commit**

```bash
git add windows/PomodoroWindows.Tests windows/PomodoroWindows/Services windows/PomodoroWindows/ViewModels windows/PomodoroWindows.sln
git commit -m "feat: add TimerViewModel with daily-goal/notification logic and tick service"
```

---

### Task 9: Color theme palettes

Ported from `Color.kt`'s `LightPalettes`/`DarkPalettes`/`paletteFor`.

**Files:**
- Create: `windows/PomodoroWindows/Theme/PomodoroPalette.cs`

**Interfaces:**
- Consumes: `ColorTheme` (Core).
- Produces: `readonly record struct PomodoroPalette(Windows.UI.Color Primary, Windows.UI.Color OnPrimary, Windows.UI.Color Background, Windows.UI.Color LongBreakAccent)`; `static class Palettes` with `PaletteFor(ColorTheme theme, bool darkTheme)`.

- [ ] **Step 1: Implement**

`windows/PomodoroWindows/Theme/PomodoroPalette.cs`:
```csharp
using PomodoroWindows.Core;
using Windows.UI;

namespace PomodoroWindows.Theme;

public readonly record struct PomodoroPalette(Color Primary, Color OnPrimary, Color Background, Color LongBreakAccent);

public static class Palettes
{
    private static Color Rgb(byte r, byte g, byte b) => Color.FromArgb(255, r, g, b);

    private static readonly Dictionary<ColorTheme, PomodoroPalette> Light = new()
    {
        [ColorTheme.Default] = new PomodoroPalette(Rgb(0xE8, 0x5D, 0x4A), Rgb(0xFF, 0xFF, 0xFF), Rgb(0xFF, 0xFB, 0xF7), Rgb(0x5C, 0x6B, 0xC0)),
        [ColorTheme.Ocean] = new PomodoroPalette(Rgb(0x1E, 0x88, 0xE5), Rgb(0xFF, 0xFF, 0xFF), Rgb(0xF5, 0xFA, 0xFF), Rgb(0x26, 0xA6, 0x9A)),
        [ColorTheme.Forest] = new PomodoroPalette(Rgb(0x43, 0xA0, 0x47), Rgb(0xFF, 0xFF, 0xFF), Rgb(0xF6, 0xFB, 0xF4), Rgb(0xFB, 0x8C, 0x00)),
        [ColorTheme.Lavender] = new PomodoroPalette(Rgb(0x8E, 0x5D, 0xE8), Rgb(0xFF, 0xFF, 0xFF), Rgb(0xFA, 0xF7, 0xFF), Rgb(0xEC, 0x40, 0x7A)),
    };

    private static readonly Dictionary<ColorTheme, PomodoroPalette> Dark = new()
    {
        [ColorTheme.Default] = new PomodoroPalette(Rgb(0xE8, 0x5D, 0x4A), Rgb(0xFF, 0xFF, 0xFF), Rgb(0x1C, 0x16, 0x14), Rgb(0x79, 0x86, 0xCB)),
        [ColorTheme.Ocean] = new PomodoroPalette(Rgb(0x64, 0xB5, 0xF6), Rgb(0x00, 0x26, 0x3D), Rgb(0x0D, 0x1B, 0x24), Rgb(0x4D, 0xB6, 0xAC)),
        [ColorTheme.Forest] = new PomodoroPalette(Rgb(0x81, 0xC7, 0x84), Rgb(0x0B, 0x3B, 0x12), Rgb(0x10, 0x19, 0x0F), Rgb(0xFF, 0xB7, 0x4D)),
        [ColorTheme.Lavender] = new PomodoroPalette(Rgb(0xB6, 0x9C, 0xF0), Rgb(0x2C, 0x0F, 0x5C), Rgb(0x16, 0x10, 0x1F), Rgb(0xF0, 0x62, 0x92)),
    };

    public static PomodoroPalette PaletteFor(ColorTheme theme, bool darkTheme) =>
        (darkTheme ? Dark : Light)[theme];
}
```

- [ ] **Step 2: Build and verify no compile errors**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build
```

Expected: `Build succeeded.`

- [ ] **Step 3: Commit**

```bash
git add windows/PomodoroWindows/Theme
git commit -m "feat: port color theme palettes from Kotlin Color.kt"
```

---

### Task 10: TimerPage UI

Wires `TimerViewModel` + `Palettes` into a visible screen: remaining time, phase label, progress ring, start/pause + reset buttons, completed-pomodoro count. This is the first task with no automated test — verified by running the app (per spec §테스트 계획, WinUI 3 UI automation is out of scope).

**Files:**
- Create: `windows/PomodoroWindows/Views/TimerPage.xaml` / `TimerPage.xaml.cs`
- Create: `windows/PomodoroWindows/Views/SettingsPage.xaml` / `SettingsPage.xaml.cs` (minimal stub only — see Step 1b; Task 11 replaces both files wholesale)
- Modify: `windows/PomodoroWindows/App.xaml.cs` (construct `TimerViewModel`/`TimerTickService`, host `TimerPage`)

**Interfaces:**
- Consumes: `TimerViewModel` (Task 8), `Palettes`/`PomodoroPalette` (Task 9).

**Forward reference note (pre-flight scan gap, found during Task 10's implementation):** `TimerPage.xaml.cs`'s `SettingsButton_Click` (Step 2 below) navigates to `SettingsPage`, which isn't created until Task 11. Step 1b below adds a minimal stub so this compiles; Task 11 overwrites both `SettingsPage.xaml`/`.xaml.cs` files with the real implementation.

- [ ] **Step 1: Write `TimerPage.xaml`**

`windows/PomodoroWindows/Views/TimerPage.xaml`:
```xml
<Page
    x:Class="PomodoroWindows.Views.TimerPage"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Grid x:Name="RootGrid">
        <StackPanel HorizontalAlignment="Center" VerticalAlignment="Center" Spacing="16">
            <TextBlock x:Name="PhaseText" HorizontalAlignment="Center" FontSize="20"/>
            <ProgressRing x:Name="ProgressRing" Width="220" Height="220" IsIndeterminate="False"/>
            <TextBlock x:Name="RemainingText" HorizontalAlignment="Center" FontSize="48" FontWeight="SemiBold"/>
            <TextBlock x:Name="CompletedText" HorizontalAlignment="Center" FontSize="14"/>
            <TextBlock x:Name="DailyGoalText" HorizontalAlignment="Center" FontSize="14" Visibility="Collapsed"/>
            <StackPanel Orientation="Horizontal" HorizontalAlignment="Center" Spacing="12">
                <Button x:Name="StartPauseButton" Content="시작" Click="StartPauseButton_Click"/>
                <Button x:Name="ResetButton" Content="리셋" Click="ResetButton_Click"/>
                <Button x:Name="SettingsButton" Content="설정" Click="SettingsButton_Click"/>
            </StackPanel>
        </StackPanel>
    </Grid>
</Page>
```

- [ ] **Step 1b: Write a minimal `SettingsPage` stub (unblocks the forward reference below; Task 11 replaces both files entirely)**

`windows/PomodoroWindows/Views/SettingsPage.xaml`:
```xml
<Page
    x:Class="PomodoroWindows.Views.SettingsPage"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Grid/>
</Page>
```

`windows/PomodoroWindows/Views/SettingsPage.xaml.cs`:
```csharp
using Microsoft.UI.Xaml.Controls;

namespace PomodoroWindows.Views;

public sealed partial class SettingsPage : Page
{
    public SettingsPage()
    {
        InitializeComponent();
    }
}
```

- [ ] **Step 2: Write `TimerPage.xaml.cs`**

`windows/PomodoroWindows/Views/TimerPage.xaml.cs`:
```csharp
using System.ComponentModel;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using PomodoroWindows.Core;
using PomodoroWindows.Theme;
using PomodoroWindows.ViewModels;
using Windows.UI;

namespace PomodoroWindows.Views;

public sealed partial class TimerPage : Page
{
    private TimerViewModel? _viewModel;

    public TimerPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _viewModel = (TimerViewModel)e.Parameter;
        _viewModel.PropertyChanged += ViewModel_PropertyChanged;
        Render();
    }

    protected override void OnNavigatedFrom(Microsoft.UI.Xaml.Navigation.NavigationEventArgs e)
    {
        base.OnNavigatedFrom(e);
        if (_viewModel is not null) _viewModel.PropertyChanged -= ViewModel_PropertyChanged;
    }

    private void ViewModel_PropertyChanged(object? sender, PropertyChangedEventArgs e) =>
        DispatcherQueue.TryEnqueue(Render);

    private void Render()
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
        RemainingText.Text = $"{totalSeconds / 60:D2}:{totalSeconds % 60:D2}";
        CompletedText.Text = $"완료한 뽀모도로: {_viewModel.CompletedPomodoros}";
        StartPauseButton.Content = _viewModel.IsRunning ? "일시정지" : "시작";

        ProgressRing.Maximum = Math.Max(_viewModel.TotalMillis, 1);
        ProgressRing.Value = _viewModel.TotalMillis - _viewModel.RemainingMillis;

        var palette = Palettes.PaletteFor(ColorTheme.Default, darkTheme: false);
        var accent = _viewModel.Phase == TimerPhase.LongBreak ? palette.LongBreakAccent : palette.Primary;
        ProgressRing.Foreground = new SolidColorBrush(accent);
        RootGrid.Background = new SolidColorBrush(palette.Background);
    }

    private void StartPauseButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.StartPauseCommand.Execute(DateTimeOffset.UtcNow);

    private void ResetButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        _viewModel?.ResetCommand.Execute(null);

    private void SettingsButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e) =>
        Frame.Navigate(typeof(SettingsPage), _viewModel);
}
```

`ColorTheme.Default`/`darkTheme: false` are hardcoded here as placeholders; Task 11 replaces them with the real setting and system theme once `SettingsViewModel` exists.

- [ ] **Step 3: Wire `App.xaml.cs` to construct the view model, tick service, and navigate to `TimerPage`**

Replace `windows/PomodoroWindows/App.xaml.cs`'s `OnLaunched`:
```csharp
using Microsoft.UI.Xaml;
using PomodoroWindows.Services;
using PomodoroWindows.ViewModels;
using PomodoroWindows.Views;

namespace PomodoroWindows;

public partial class App : Application
{
    private Window? _window;
    private TimerTickService? _tickService;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        var settingsStore = new ApplicationDataSettingsStore();
        var notificationService = new FakeNotificationService(); // replaced with AppNotificationService in Task 12
        var timerViewModel = new TimerViewModel(settingsStore, notificationService);

        _window = new MainWindow();
        var frame = ((MainWindow)_window).RootFrame;
        frame.Navigate(typeof(TimerPage), timerViewModel);

        _tickService = new TimerTickService(Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread(), timerViewModel);
        _tickService.Start();

        _window.Activate();
    }
}
```

`windows/PomodoroWindows/MainWindow.xaml.cs` needs `RootFrame` reachable from `App.xaml.cs` — a different class in the same assembly. WinUI XAML codegen makes `x:Name` fields **private** by default, so `RootFrame` needs `x:FieldModifier="public"` in `MainWindow.xaml` (already specified in Task 6 Step 4 above) for this cross-class access to compile.

- [ ] **Step 4: Build and run; verify manually**

Use the build+register+launch pattern from Global Constraints (`dotnet run` doesn't work for this project):

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification here: the process launches with `MainWindowTitle` "Pomodoro" and doesn't crash — then close it (`Stop-Process -Name PomodoroWindows -ErrorAction SilentlyContinue`). The full interaction check (시작 button toggling to 일시정지, the countdown actually ticking, 리셋 restoring "25:00") requires clicking the UI and is verified by the human user, not the implementer — see Global Constraints. State this limitation explicitly in the task report rather than claiming the click-through was observed.

- [ ] **Step 5: Commit**

```bash
git add windows/PomodoroWindows/Views/TimerPage.xaml windows/PomodoroWindows/Views/TimerPage.xaml.cs windows/PomodoroWindows/App.xaml.cs
git commit -m "feat: add TimerPage UI wired to TimerViewModel"
```

---

### Task 11: SettingsPage UI and SettingsViewModel

**Files:**
- Create: `windows/PomodoroWindows/ViewModels/SettingsViewModel.cs`
- Modify: `windows/PomodoroWindows/Views/SettingsPage.xaml` / `SettingsPage.xaml.cs` (Task 10 created a minimal stub — a `Page` with an empty `<Grid/>` — solely so `TimerPage.xaml.cs`'s `Frame.Navigate(typeof(SettingsPage), ...)` would compile; this task overwrites both files entirely with the real content below, nothing from the stub survives)
- Modify: `windows/PomodoroWindows/Views/TimerPage.xaml.cs` (read live theme/dark-mode from `SettingsViewModel` instead of the Task 10 placeholder)

**Interfaces:**
- Consumes: `ISettingsStore`, `TimerSettings` (Task 7); `TimerViewModel` (Task 8, so setting changes can push new durations into the live engine via `UpdateDurations`).
- Produces: `class SettingsViewModel : ObservableObject` with a bindable property per `TimerSettings` field, plus `void Save()` that persists via `ISettingsStore` and calls `timerViewModel`'s duration/theme update.

- [ ] **Step 1: Implement `SettingsViewModel`**

`windows/PomodoroWindows/ViewModels/SettingsViewModel.cs`:
```csharp
using CommunityToolkit.Mvvm.ComponentModel;
using PomodoroWindows.Core;
using PomodoroWindows.Models;
using PomodoroWindows.Services;

namespace PomodoroWindows.ViewModels;

public sealed partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsStore _settingsStore;
    private readonly TimerViewModel _timerViewModel;

    [ObservableProperty] private int focusMinutes;
    [ObservableProperty] private int breakMinutes;
    [ObservableProperty] private int longBreakMinutes;
    [ObservableProperty] private int pomodorosUntilLongBreak;
    [ObservableProperty] private bool autoStartBreak;
    [ObservableProperty] private bool autoStartFocus;
    [ObservableProperty] private bool keepScreenOn;
    [ObservableProperty] private bool dailyGoalEnabled;
    [ObservableProperty] private int dailyGoalTarget;
    [ObservableProperty] private bool notificationsEnabled;
    [ObservableProperty] private bool useAlarmSound;
    [ObservableProperty] private ColorTheme colorTheme;

    public SettingsViewModel(ISettingsStore settingsStore, TimerViewModel timerViewModel)
    {
        _settingsStore = settingsStore;
        _timerViewModel = timerViewModel;

        var s = _settingsStore.Load();
        focusMinutes = s.FocusMinutes;
        breakMinutes = s.BreakMinutes;
        longBreakMinutes = s.LongBreakMinutes;
        pomodorosUntilLongBreak = s.PomodorosUntilLongBreak;
        autoStartBreak = s.AutoStartBreak;
        autoStartFocus = s.AutoStartFocus;
        keepScreenOn = s.KeepScreenOn;
        dailyGoalEnabled = s.DailyGoalEnabled;
        dailyGoalTarget = s.DailyGoalTarget;
        notificationsEnabled = s.NotificationsEnabled;
        useAlarmSound = s.UseAlarmSound;
        colorTheme = s.ColorTheme;
    }

    public void Save()
    {
        var settings = new TimerSettings(
            FocusMinutes, BreakMinutes, LongBreakMinutes, PomodorosUntilLongBreak,
            AutoStartBreak, AutoStartFocus, KeepScreenOn, DailyGoalEnabled, DailyGoalTarget,
            NotificationsEnabled, UseAlarmSound, ColorTheme);
        _settingsStore.Save(settings);
        _timerViewModel.ApplySettings(settings);
    }
}
```

- [ ] **Step 2: Add `TimerViewModel.ApplySettings` so Settings can push live duration/auto-start changes into the running engine**

Add to `windows/PomodoroWindows/ViewModels/TimerViewModel.cs` (inside the `TimerViewModel` class, alongside the existing methods):
```csharp
    public void ApplySettings(TimerSettings settings)
    {
        _settings = settings;
        _engine.UpdateDurations(
            settings.FocusMinutes * 60_000L,
            settings.BreakMinutes * 60_000L,
            settings.LongBreakMinutes * 60_000L,
            settings.PomodorosUntilLongBreak,
            settings.AutoStartBreak,
            settings.AutoStartFocus);
        ApplyState(_engine.CurrentState(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()));
    }
```

- [ ] **Step 3: Write a unit test for `ApplySettings` (extends Task 8's test class)**

Append to `windows/PomodoroWindows.Tests/TimerViewModelTests.cs`:
```csharp
    [Fact]
    public void ApplySettings_updates_idle_remaining_time_to_the_new_focus_duration()
    {
        var vm = CreateViewModel(initialSettings: new TimerSettings(FocusMinutes: 25, BreakMinutes: 5));

        vm.ApplySettings(new TimerSettings(FocusMinutes: 10, BreakMinutes: 5));

        Assert.Equal(10 * 60_000L, vm.RemainingMillis);
    }
```

- [ ] **Step 4: Run tests and verify they pass**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false
```

Expected: `Passed! - Failed: 0, Passed: 7`

- [ ] **Step 5: Write `SettingsPage.xaml`**

`windows/PomodoroWindows/Views/SettingsPage.xaml`:
```xml
<Page
    x:Class="PomodoroWindows.Views.SettingsPage"
    xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <ScrollViewer>
        <StackPanel Padding="24" Spacing="20" MaxWidth="480">
            <TextBlock Text="설정" FontSize="24" FontWeight="SemiBold"/>

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

            <ToggleSwitch x:Name="AutoStartBreakToggle" Header="자동으로 휴식 시작" Toggled="AutoStartBreakToggle_Toggled"/>
            <ToggleSwitch x:Name="AutoStartFocusToggle" Header="자동으로 다음 뽀모도로 시작" Toggled="AutoStartFocusToggle_Toggled"/>
            <ToggleSwitch x:Name="KeepScreenOnToggle" Header="화면 꺼짐 방지" Toggled="KeepScreenOnToggle_Toggled"/>
            <ToggleSwitch x:Name="DailyGoalEnabledToggle" Header="일일 목표 사용" Toggled="DailyGoalEnabledToggle_Toggled"/>

            <StackPanel Spacing="4">
                <TextBlock x:Name="DailyGoalTargetLabel"/>
                <Slider x:Name="DailyGoalTargetSlider" Minimum="1" Maximum="20" ValueChanged="DailyGoalTargetSlider_ValueChanged"/>
            </StackPanel>

            <ToggleSwitch x:Name="NotificationsEnabledToggle" Header="알림 사용" Toggled="NotificationsEnabledToggle_Toggled"/>
            <ToggleSwitch x:Name="UseAlarmSoundToggle" Header="알람음 사용 (끄면 알림음)" Toggled="UseAlarmSoundToggle_Toggled"/>

            <TextBlock Text="테마"/>
            <StackPanel x:Name="ThemeSwatchPanel" Orientation="Horizontal" Spacing="16"/>

            <Button x:Name="BackButton" Content="뒤로" Click="BackButton_Click"/>
        </StackPanel>
    </ScrollViewer>
</Page>
```

- [ ] **Step 6: Write `SettingsPage.xaml.cs`**

`windows/PomodoroWindows/Views/SettingsPage.xaml.cs`:
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

    private void LoadIntoControls()
    {
        if (_viewModel is null) return;
        _isLoading = true;

        FocusLabel.Text = $"집중 시간: {_viewModel.FocusMinutes}분";
        FocusSlider.Value = _viewModel.FocusMinutes;
        BreakLabel.Text = $"휴식 시간: {_viewModel.BreakMinutes}분";
        BreakSlider.Value = _viewModel.BreakMinutes;
        LongBreakLabel.Text = $"긴 휴식 시간: {_viewModel.LongBreakMinutes}분";
        LongBreakSlider.Value = _viewModel.LongBreakMinutes;
        PomodorosUntilLongBreakLabel.Text = $"긴 휴식까지 뽀모도로 횟수: {_viewModel.PomodorosUntilLongBreak}회";
        PomodorosUntilLongBreakSlider.Value = _viewModel.PomodorosUntilLongBreak;
        AutoStartBreakToggle.IsOn = _viewModel.AutoStartBreak;
        AutoStartFocusToggle.IsOn = _viewModel.AutoStartFocus;
        KeepScreenOnToggle.IsOn = _viewModel.KeepScreenOn;
        DailyGoalEnabledToggle.IsOn = _viewModel.DailyGoalEnabled;
        DailyGoalTargetLabel.Text = $"일일 목표: {_viewModel.DailyGoalTarget}개";
        DailyGoalTargetSlider.Value = _viewModel.DailyGoalTarget;
        NotificationsEnabledToggle.IsOn = _viewModel.NotificationsEnabled;
        UseAlarmSoundToggle.IsOn = _viewModel.UseAlarmSound;

        ThemeSwatchPanel.Children.Clear();
        foreach (ColorTheme theme in Enum.GetValues<ColorTheme>())
        {
            var palette = Palettes.PaletteFor(theme, darkTheme: false);
            var swatch = new Ellipse
            {
                Width = 40,
                Height = 40,
                Fill = new SolidColorBrush(palette.Primary),
                Tag = theme
            };
            swatch.Tapped += ThemeSwatch_Tapped;
            ThemeSwatchPanel.Children.Add(swatch);
        }

        _isLoading = false;
    }

    private void ThemeSwatch_Tapped(object sender, Microsoft.UI.Xaml.Input.TappedRoutedEventArgs e)
    {
        if (_viewModel is null) return;
        _viewModel.ColorTheme = (ColorTheme)((Ellipse)sender).Tag;
        _viewModel.Save();
    }

    private void FocusSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.FocusMinutes = (int)e.NewValue;
        FocusLabel.Text = $"집중 시간: {_viewModel.FocusMinutes}분";
        _viewModel.Save();
    }

    private void BreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.BreakMinutes = (int)e.NewValue;
        BreakLabel.Text = $"휴식 시간: {_viewModel.BreakMinutes}분";
        _viewModel.Save();
    }

    private void LongBreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.LongBreakMinutes = (int)e.NewValue;
        LongBreakLabel.Text = $"긴 휴식 시간: {_viewModel.LongBreakMinutes}분";
        _viewModel.Save();
    }

    private void PomodorosUntilLongBreakSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.PomodorosUntilLongBreak = (int)e.NewValue;
        PomodorosUntilLongBreakLabel.Text = $"긴 휴식까지 뽀모도로 횟수: {_viewModel.PomodorosUntilLongBreak}회";
        _viewModel.Save();
    }

    private void DailyGoalTargetSlider_ValueChanged(object sender, RangeBaseValueChangedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.DailyGoalTarget = (int)e.NewValue;
        DailyGoalTargetLabel.Text = $"일일 목표: {_viewModel.DailyGoalTarget}개";
        _viewModel.Save();
    }

    private void AutoStartBreakToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.AutoStartBreak = AutoStartBreakToggle.IsOn;
        _viewModel.Save();
    }

    private void AutoStartFocusToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.AutoStartFocus = AutoStartFocusToggle.IsOn;
        _viewModel.Save();
    }

    private void KeepScreenOnToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.KeepScreenOn = KeepScreenOnToggle.IsOn;
        _viewModel.Save();
    }

    private void DailyGoalEnabledToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.DailyGoalEnabled = DailyGoalEnabledToggle.IsOn;
        _viewModel.Save();
    }

    private void NotificationsEnabledToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.NotificationsEnabled = NotificationsEnabledToggle.IsOn;
        _viewModel.Save();
    }

    private void UseAlarmSoundToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_isLoading || _viewModel is null) return;
        _viewModel.UseAlarmSound = UseAlarmSoundToggle.IsOn;
        _viewModel.Save();
    }

    private void BackButton_Click(object sender, RoutedEventArgs e) => Frame.GoBack();
}
```

- [ ] **Step 7: Build and run; verify manually**

Use the build+register+launch pattern from Global Constraints:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification: launches with `MainWindowTitle` "Pomodoro", doesn't crash — then close it. The full interaction check (설정 navigation, slider drag, theme swatch tap) requires clicking the UI and is verified by the human user, not the implementer — see Global Constraints.

- [ ] **Step 8: Replace Task 10's hardcoded `ColorTheme.Default`/`darkTheme: false` in `TimerPage.xaml.cs` with the live setting**

In `windows/PomodoroWindows/Views/TimerPage.xaml.cs`, change the `Render()` method's theme lookup. First add a field and load it in `OnNavigatedTo`:
```csharp
    private ColorTheme _colorTheme = ColorTheme.Default;
```
In `OnNavigatedTo`, after `_viewModel = (TimerViewModel)e.Parameter;`, add:
```csharp
        _colorTheme = new Services.ApplicationDataSettingsStore().Load().ColorTheme;
```
And in `Render()`, replace:
```csharp
        var palette = Palettes.PaletteFor(ColorTheme.Default, darkTheme: false);
```
with:
```csharp
        var currentSettings = new Services.ApplicationDataSettingsStore().Load();
        _colorTheme = currentSettings.ColorTheme;
        var isDark = Microsoft.UI.Xaml.Application.Current.RequestedTheme == ApplicationTheme.Dark;
        var palette = Palettes.PaletteFor(_colorTheme, isDark);

        if (currentSettings.DailyGoalEnabled)
        {
            DailyGoalText.Visibility = Microsoft.UI.Xaml.Visibility.Visible;
            DailyGoalText.Text = $"오늘 목표: {_viewModel.DailyGoalCount}/{currentSettings.DailyGoalTarget}";
        }
        else
        {
            DailyGoalText.Visibility = Microsoft.UI.Xaml.Visibility.Collapsed;
        }
```
(add `using Microsoft.UI.Xaml;` for `ApplicationTheme` if not already present via the existing `Microsoft.UI.Xaml.Controls` usings — reference it fully qualified as shown to avoid an extra using line).

- [ ] **Step 8b: Manually verify the daily goal display**

Turn on 일일 목표 사용 in Settings with target 3, go back to the timer page — "오늘 목표: 0/3" appears under the completed-pomodoro count. Complete one focus session — updates to "오늘 목표: 1/3".

- [ ] **Step 9: Run tests, build, and manually verify theme switching**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification: tests pass, app launches with `MainWindowTitle` "Pomodoro", doesn't crash — then close it. The theme-swatch interaction check is verified by the human user, not the implementer — see Global Constraints.

- [ ] **Step 10: Commit**

```bash
git add windows/PomodoroWindows/ViewModels/SettingsViewModel.cs windows/PomodoroWindows/ViewModels/TimerViewModel.cs windows/PomodoroWindows/Views/SettingsPage.xaml windows/PomodoroWindows/Views/SettingsPage.xaml.cs windows/PomodoroWindows/Views/TimerPage.xaml.cs windows/PomodoroWindows.Tests/TimerViewModelTests.cs
git commit -m "feat: add SettingsPage UI and wire live color theme into TimerPage"
```

---

### Task 12: Toast notifications (`AppNotificationService`)

Replaces Task 10's `FakeNotificationService` wiring in `App.xaml.cs` with the real `AppNotificationManager`-backed implementation.

**Files:**
- Create: `windows/PomodoroWindows/Services/AppNotificationService.cs`
- Modify: `windows/PomodoroWindows/App.xaml.cs` (use `AppNotificationService`, call `AppNotificationManager.Default.Register()` at startup)

**Interfaces:**
- Consumes: `INotificationService` (Task 8), `NotificationContent` (Core), `TimerSettings.UseAlarmSound`.

- [ ] **Step 1: Implement**

`windows/PomodoroWindows/Services/AppNotificationService.cs`:
```csharp
using Microsoft.Windows.AppNotifications;
using Microsoft.Windows.AppNotifications.Builder;
using PomodoroWindows.Core;

namespace PomodoroWindows.Services;

public sealed class AppNotificationService : INotificationService
{
    private readonly Func<bool> _useAlarmSound;

    public AppNotificationService(Func<bool> useAlarmSound)
    {
        _useAlarmSound = useAlarmSound;
    }

    public void NotifySessionEnd(TimerPhase newPhase)
    {
        Send(NotificationContent.SessionEndTitle(newPhase));
    }

    public void NotifyDailyGoalReached(int target)
    {
        Send(NotificationContent.DailyGoalReachedTitle(target));
    }

    private void Send(string title)
    {
        var builder = new AppNotificationBuilder().AddText(title);
        if (_useAlarmSound())
        {
            builder.SetAudioUri(new Uri("ms-winsoundevent:Notification.Looping.Alarm"));
        }
        AppNotificationManager.Default.Show(builder.BuildNotification());
    }
}
```

- [ ] **Step 2: Wire it into `App.xaml.cs`**

In `windows/PomodoroWindows/App.xaml.cs`, replace:
```csharp
        var notificationService = new FakeNotificationService(); // replaced with AppNotificationService in Task 12
```
with:
```csharp
        AppNotificationManager.Default.Register();
        var notificationService = new AppNotificationService(() => settingsStore.Load().UseAlarmSound);
```
Add `using Microsoft.Windows.AppNotifications;` and `using PomodoroWindows.Services;` (the latter likely already present) at the top.

- [ ] **Step 3: Build and run; verify manually**

Use the build+register+launch pattern from Global Constraints:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification: launches with `MainWindowTitle` "Pomodoro", doesn't crash — then close it. Triggering an actual session-end toast requires clicking 시작 and waiting out a real focus duration (or a temporary local edit to `TimerDefaults.MinFocusMinutes` to shorten it, reverted after), which needs UI interaction beyond what an implementer subagent can do — the human user verifies this in Task 15's manual QA checklist (see Global Constraints).

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows/Services/AppNotificationService.cs windows/PomodoroWindows/App.xaml.cs
git commit -m "feat: wire real toast notifications via AppNotificationManager"
```

---

### Task 13: Tray icon and background operation

**Files:**
- Create: `windows/PomodoroWindows/Services/TrayIconService.cs`
- Modify: `windows/PomodoroWindows/MainWindow.xaml.cs` (intercept close, hide instead of exit)
- Modify: `windows/PomodoroWindows/App.xaml.cs` (construct `TrayIconService`, wire "종료" to actually close)

**Interfaces:**
- Consumes: `TimerViewModel` (Task 8), `MainWindow`.
- Produces: `class TrayIconService : IDisposable` with a public `event Action? ExitRequested` and methods `Show()`.

- [ ] **Step 1: Add the `System.Drawing.Common` package**

`TrayIconService` (Step 2 below) sets the tray icon from `System.Drawing.SystemIcons.Application`, which is not covered by any package Task 6 added:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet add PomodoroWindows\PomodoroWindows.csproj package System.Drawing.Common
```

If `H.NotifyIcon.WinUI`'s `TaskbarIcon.Icon` property rejects `System.Drawing.Icon` (a build error naming a different expected type, e.g. `Microsoft.UI.Xaml.Controls.IconSource`), that means this package version's WinUI-flavored `TaskbarIcon` uses a WinUI-native icon type instead — in that case, skip the `System.Drawing.Common` icon assignment and construct the icon via that expected type instead (check the compiler error's expected type and IntelliSense on `TaskbarIcon.Icon` for the exact API).

- [ ] **Step 2: Implement `TrayIconService`**

`windows/PomodoroWindows/Services/TrayIconService.cs`:
```csharp
using H.NotifyIcon;
using Microsoft.UI.Xaml;
using PomodoroWindows.Core;
using PomodoroWindows.ViewModels;

namespace PomodoroWindows.Services;

public sealed class TrayIconService : IDisposable
{
    private readonly TaskbarIcon _taskbarIcon;
    private readonly TimerViewModel _timerViewModel;
    private readonly Window _window;

    public event Action? ExitRequested;

    public TrayIconService(Window window, TimerViewModel timerViewModel)
    {
        _window = window;
        _timerViewModel = timerViewModel;

        _taskbarIcon = new TaskbarIcon
        {
            ToolTipText = "Pomodoro",
            Icon = System.Drawing.SystemIcons.Application
        };
        _taskbarIcon.LeftClickCommand = new RelayNoArgCommand(RestoreWindow);

        var menu = new Microsoft.UI.Xaml.Controls.MenuFlyout();
        var toggleItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "시작/일시정지" };
        toggleItem.Click += (_, _) => _timerViewModel.StartPauseCommand.Execute(DateTimeOffset.UtcNow);
        var resetItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "리셋" };
        resetItem.Click += (_, _) => _timerViewModel.ResetCommand.Execute(null);
        var openItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "열기" };
        openItem.Click += (_, _) => RestoreWindow();
        var exitItem = new Microsoft.UI.Xaml.Controls.MenuFlyoutItem { Text = "종료" };
        exitItem.Click += (_, _) => ExitRequested?.Invoke();

        menu.Items.Add(toggleItem);
        menu.Items.Add(resetItem);
        menu.Items.Add(openItem);
        menu.Items.Add(exitItem);
        _taskbarIcon.ContextFlyout = menu;
    }

    public void Show() => _taskbarIcon.ForceCreate();

    private void RestoreWindow()
    {
        _window.Activate();
    }

    public void Dispose() => _taskbarIcon.Dispose();
}

internal sealed class RelayNoArgCommand : System.Windows.Input.ICommand
{
    private readonly Action _action;
    public RelayNoArgCommand(Action action) => _action = action;
    public event EventHandler? CanExecuteChanged;
    public bool CanExecute(object? parameter) => true;
    public void Execute(object? parameter) => _action();
}
```

- [ ] **Step 3: Intercept `MainWindow` close to hide instead of exit**

Replace `windows/PomodoroWindows/MainWindow.xaml.cs`:
```csharp
using Microsoft.UI.Xaml;
using WinRT.Interop;

namespace PomodoroWindows;

public sealed partial class MainWindow : Window
{
    public bool AllowClose { get; set; }

    public MainWindow()
    {
        InitializeComponent();
        Title = "Pomodoro";
        AppWindow.Closing += AppWindow_Closing;
    }

    private void AppWindow_Closing(Microsoft.UI.Windowing.AppWindow sender, Microsoft.UI.Windowing.AppWindowClosingEventArgs args)
    {
        if (AllowClose) return;
        args.Cancel = true;
        AppWindow.Hide();
    }
}
```

- [ ] **Step 4: Wire it into `App.xaml.cs`**

In `windows/PomodoroWindows/App.xaml.cs`, after `_window.Activate();`, add:
```csharp
        var trayIconService = new TrayIconService(_window, timerViewModel);
        trayIconService.Show();
        trayIconService.ExitRequested += () =>
        {
            ((MainWindow)_window).AllowClose = true;
            _tickService?.Stop();
            trayIconService.Dispose();
            _window.Close();
        };
```
Add `using PomodoroWindows.Services;` if not already present (it is, from Task 8/12).

- [ ] **Step 5: Build and run; verify manually**

Use the build+register+launch pattern from Global Constraints:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification: launches with `MainWindowTitle` "Pomodoro", doesn't crash — then close it (`Stop-Process -Name PomodoroWindows -ErrorAction SilentlyContinue`). The full interaction check (clicking X to minimize to tray, process surviving with the window gone, tray menu clicks) requires UI interaction beyond what an implementer subagent can do — the human user verifies this in Task 15's manual QA checklist (see Global Constraints).

- [ ] **Step 6: Commit**

```bash
git add windows/PomodoroWindows/Services/TrayIconService.cs windows/PomodoroWindows/MainWindow.xaml.cs windows/PomodoroWindows/App.xaml.cs windows/PomodoroWindows/PomodoroWindows.csproj
git commit -m "feat: add tray icon with minimize-to-tray on window close"
```

---

### Task 14: Keep-awake service

**Files:**
- Create: `windows/PomodoroWindows/Services/KeepAwakeService.cs`
- Modify: `windows/PomodoroWindows/App.xaml.cs` (subscribe to `TimerViewModel.PropertyChanged`, toggle keep-awake when `IsRunning` changes and `KeepScreenOn` is on)

**Interfaces:**
- Consumes: `TimerViewModel.IsRunning`, `TimerSettings.KeepScreenOn`.
- Produces: `class KeepAwakeService` with `void SetActive(bool active)`.

- [ ] **Step 1: Implement**

`windows/PomodoroWindows/Services/KeepAwakeService.cs`:
```csharp
using System.Runtime.InteropServices;

namespace PomodoroWindows.Services;

public sealed class KeepAwakeService
{
    [Flags]
    private enum ExecutionState : uint
    {
        Continuous = 0x80000000,
        SystemRequired = 0x00000001,
        DisplayRequired = 0x00000002
    }

    [DllImport("kernel32.dll")]
    private static extern ExecutionState SetThreadExecutionState(ExecutionState state);

    public void SetActive(bool active)
    {
        SetThreadExecutionState(active
            ? ExecutionState.Continuous | ExecutionState.SystemRequired | ExecutionState.DisplayRequired
            : ExecutionState.Continuous);
    }
}
```

- [ ] **Step 2: Wire it into `App.xaml.cs`**

In `windows/PomodoroWindows/App.xaml.cs`, after constructing `timerViewModel`, add:
```csharp
        var keepAwakeService = new KeepAwakeService();
        timerViewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TimerViewModel.IsRunning))
            {
                var keepScreenOn = settingsStore.Load().KeepScreenOn;
                keepAwakeService.SetActive(timerViewModel.IsRunning && keepScreenOn);
            }
        };
```
Add `using System.ComponentModel;` if needed for the event signature (usually inferred; add only if the build reports it missing).

- [ ] **Step 3: Build and run; verify manually**

Use the build+register+launch pattern from Global Constraints:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet build PomodoroWindows\PomodoroWindows.csproj -c Debug -p:Platform=x64
Add-AppxPackage -Register PomodoroWindows\bin\x64\Debug\net8.0-windows10.0.19041.0\AppxManifest.xml
Start-Process shell:AppsFolder\PomodoroWindows_hqfn3qvq51sga!App
Start-Sleep -Seconds 3
Get-Process -Name PomodoroWindows -ErrorAction SilentlyContinue | Select-Object Id, MainWindowTitle
```

An implementer subagent's achievable verification: launches with `MainWindowTitle` "Pomodoro", doesn't crash — then close it. The full check (toggling 화면 꺼짐 방지, starting the timer via UI, then confirming via an *elevated* `powercfg /requests` that `PomodoroWindows.exe` appears under `DISPLAY:` while running and disappears when paused) requires UI interaction and admin elevation beyond what an implementer subagent can do — the human user verifies this in Task 15's manual QA checklist (see Global Constraints).

- [ ] **Step 4: Commit**

```bash
git add windows/PomodoroWindows/Services/KeepAwakeService.cs windows/PomodoroWindows/App.xaml.cs
git commit -m "feat: prevent display sleep while a session is running and keepScreenOn is on"
```

---

### Task 15: Final MSIX publish and manual QA pass

**Files:** none new — verification only, per spec §테스트 계획's manual checklist.

- [ ] **Step 1: Run the full automated test suite**

Run each test project explicitly rather than a solution-wide `dotnet test` — `PomodoroWindows.csproj`'s `Platforms=x64;arm64`-only config (see Global Constraints) means a bare solution-level `dotnet test` fails at the build step before any tests run:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet test PomodoroWindows.Core.Tests\PomodoroWindows.Core.Tests.csproj
dotnet test PomodoroWindows.Tests\PomodoroWindows.Tests.csproj -p:Platform=x64 -p:WindowsAppSdkDeploymentManagerInitialize=false
```

Expected: `PomodoroWindows.Core.Tests` passes all 34 tests, `PomodoroWindows.Tests` passes all of its tests (7 as of Task 11), 0 failures in either.

- [ ] **Step 2: Publish an MSIX package**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
cd D:\make_pomodoro_app\.claude\worktrees\windows-companion\windows
dotnet publish PomodoroWindows\PomodoroWindows.csproj -c Release -p:Platform=x64 -p:GenerateAppxPackageOnBuild=true
```

Expected: build succeeds and an `.msix` file is produced under `PomodoroWindows\bin\x64\Release\...\AppPackages\`. If signing fails, re-run Task 6 Step 6 (dev certificate) first.

- [ ] **Step 3: Manual QA checklist (walk through each once, in order)**

1. Fresh install: run the `.msix` from Step 2 (double-click, install, launch from Start Menu) — app opens to 집중/25:00 idle.
2. 시작 → time counts down once per second; 일시정지 → freezes; 시작 again → resumes from the frozen value (not from 25:00).
3. 리셋 → returns to idle 집중/25:00.
4. Settings: drag 집중 시간 to 5, 뒤로 → timer page shows 05:00.
5. Turn on 자동으로 휴식 시작, start a session, let it run past the (now 5-minute) focus duration → transitions to 휴식 automatically, still running.
6. Turn off notifications in Settings, let a phase end → no toast appears. Turn notifications back on, let another phase end → toast titled per `NotificationContent.SessionEndTitle` appears.
7. Turn on 일일 목표 사용 with target 1, complete one focus session → toast "오늘의 목표 1개를 달성했어요!" appears.
8. Pick each of the 4 theme swatches in Settings → timer ring/background color changes to match `Palettes`.
9. Turn on 화면 꺼짐 방지, start a session, confirm via `powercfg /requests` that display sleep is blocked; pause and confirm it's no longer blocked.
10. Close the window (X) while a session is running → window hides, tray icon remains, `Get-Process PomodoroWindows` still shows the process; wait past a phase boundary → toast still fires while hidden.
11. Right-click tray icon → 열기 restores the window showing the still-current state; 종료 fully exits the process.
12. Switch Windows to dark mode (Settings → Personalization → Colors) while the app is running, reopen the app → dark palette applied.

- [ ] **Step 4: Record the outcome**

If all 12 checks pass, the phase-1 standalone app is complete. If any fail, fix inline (small, targeted fixes — not new tasks) and re-run the relevant checklist items before considering this task done. No commit for this task unless a fix was needed, in which case commit that fix with a message describing what QA step caught it.
