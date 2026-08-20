# Color Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick one of 4 color themes for the app's UI (primary, onPrimary, background, and the long-break accent color, each with a light and dark variant), matching the reference app's color-theme feature. This is feature 6 of the feature-gap backlog (`docs/feature-backlog.md`).

**Architecture:** `:core` gains a `ColorTheme` enum holding only the 4 theme identifiers (`DEFAULT`, `OCEAN`, `FOREST`, `LAVENDER`) — no color values, mirroring how `TimerPhase` is a bare identifier that each UI module renders independently. `TimerSettingsRepository` persists the selection; `SyncPayload` carries it so the watch mirrors whatever the phone has selected (the watch has no picker of its own). `:app`'s `Color.kt`/`Theme.kt` and `:wear`'s `WearTheme.kt` each define their own `Map<ColorTheme, Palette>` (light + dark for phone; watch stays background-black, matching its existing fixed scheme) and a lookup function; `TimerScreen`/`WearTimerScreen` use that lookup instead of the old single hardcoded `PomodoroLongBreakAccent` constant. `SettingsScreen` gets a row of 4 tappable color swatches.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 on phone, Wear Compose Material on watch), AndroidX DataStore Preferences, Google Play Services Wearable Data Layer API, JUnit4 (local unit tests only — this environment has no attached Android device/emulator, so `androidTest` instrumented suites cannot be run here; verify those layers by compiling and defer to manual on-device testing).

**Spec:** `docs/superpowers/specs/2026-08-21-color-themes-design.md`

## Global Constraints

- A theme controls the **full palette**: `primary`, `onPrimary`, `background`, and the long-break accent color — not just the primary accent.
- Each theme defines **both a light and a dark variant** on the phone; the app still auto-switches via `isSystemInDarkTheme()`, unchanged. The watch has no system dark-mode concept (existing behavior) — one palette per theme, background fixed at black as today.
- **4 themes at launch**: `DEFAULT` (current orange — must render pixel-identical to today, since it's what every existing installed user already has), `OCEAN` (blue/teal), `FOREST` (green), `LAVENDER` (purple).
- Default theme is `ColorTheme.DEFAULT` everywhere — no `TimerDefaults` indirection is needed for it, unlike numeric settings (`focusMinutes` etc.), because the enum constant already **is** the default value.
- **Phone-only selection UI** (`SettingsScreen`, a new "테마" section with 4 tappable swatches). The **watch has no theme picker** — it only renders whatever theme is currently synced from the phone, the same relationship autostart/daily-goal/notification settings already have.
- `:core` holds only the `ColorTheme` identifier, never actual color values — precedent: `PomodoroLongBreakAccent` (app) and `PomodoroWearLongBreakAccent` (wear) already duplicate the same hex in each UI module rather than sharing it through `:core`.
- Out of scope: onboarding flow, custom/user-defined themes, more than 4 themes, watch-side theme picker UI, per-element theme customization beyond the 4-color palette.

---

## Task 1: Core wire format — `ColorTheme` identity, sync key, payload field

**Files:**
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/ColorTheme.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`

**Interfaces:**
- Consumes: nothing (pure `:core` module, no dependency on other tasks).
- Produces: `enum class ColorTheme { DEFAULT, OCEAN, FOREST, LAVENDER }`; `SyncKeys.KEY_COLOR_THEME`; `SyncPayload.colorTheme: ColorTheme = ColorTheme.DEFAULT`. Every later task depends on this.

This is a plain data addition (a 4-value enum, a string constant, a data class field with a default) — the same kind of mechanical, no-new-test step Task 2 Step 1 of the daily-goal plan used for its defaults/keys/payload fields. `ColorTheme` itself needs no dedicated test file, mirroring `TimerPhase`, which also has none.

- [ ] **Step 1: Create the `ColorTheme` enum**

Create `core/src/main/kotlin/com/pomodoro/timer/core/ColorTheme.kt`:

```kotlin
package com.pomodoro.timer.core

enum class ColorTheme {
    DEFAULT, OCEAN, FOREST, LAVENDER
}
```

- [ ] **Step 2: Add the sync key**

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`, add after `KEY_USE_ALARM_SOUND`:

```kotlin
    const val KEY_COLOR_THEME = "colorTheme"
```

- [ ] **Step 3: Add the payload field**

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`, add after `useAlarmSound: Boolean = false`:

```kotlin
    val useAlarmSound: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT
```

(replace the trailing field so the data class closes correctly — `useAlarmSound` keeps its existing default, `colorTheme` is the new last field.)

- [ ] **Step 4: Verify `:core` compiles**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/pomodoro/timer/core/ColorTheme.kt core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt
git commit -m "feat: add ColorTheme identity and wire-format field"
```

---

## Task 2: Sync wire format — carry `colorTheme` between phone and watch

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`

**Interfaces:**
- Consumes: `SyncKeys.KEY_COLOR_THEME`, `SyncPayload.colorTheme`, `ColorTheme` (Task 1).
- Produces: both transports' `publish`/`decode` round-trip `colorTheme`. Tasks 6 and 8 depend on this (their `SyncPayload(...)` calls need the value to actually reach the paired device).

No local unit tests exist for these transports (they wrap the Play Services Wearable `DataClient`, which needs a real device/emulator); verification here is compile-check plus the manual on-device check called out at the end of Task 9. Keep the change purely mechanical and symmetric with the existing `phase` field, which already demonstrates the enum-via-string pattern.

- [ ] **Step 1: Add `colorTheme` to `PlayServicesSyncTransport`'s encode and decode**

In `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`, in `publish`, add after `dataMap.putBoolean(SyncKeys.KEY_USE_ALARM_SOUND, payload.useAlarmSound)`:

```kotlin
            dataMap.putString(SyncKeys.KEY_COLOR_THEME, payload.colorTheme.name)
```

In `decode`, change the tail from:

```kotlin
        notificationsEnabled = map.getBoolean(SyncKeys.KEY_NOTIFICATIONS_ENABLED, true),
        vibrationEnabled = map.getBoolean(SyncKeys.KEY_VIBRATION_ENABLED, true),
        useAlarmSound = map.getBoolean(SyncKeys.KEY_USE_ALARM_SOUND, false)
    )
```

to:

```kotlin
        notificationsEnabled = map.getBoolean(SyncKeys.KEY_NOTIFICATIONS_ENABLED, true),
        vibrationEnabled = map.getBoolean(SyncKeys.KEY_VIBRATION_ENABLED, true),
        useAlarmSound = map.getBoolean(SyncKeys.KEY_USE_ALARM_SOUND, false),
        colorTheme = enumValues<ColorTheme>()
            .firstOrNull { it.name == map.getString(SyncKeys.KEY_COLOR_THEME, ColorTheme.DEFAULT.name) }
            ?: ColorTheme.DEFAULT
    )
```

Add the import `com.pomodoro.timer.core.ColorTheme` alongside the existing `TimerPhase` import.

- [ ] **Step 2: Mirror the identical change in `WearPlayServicesSyncTransport`**

Apply the same two edits (plus the same import) to `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`.

- [ ] **Step 3: Verify both modules compile**

Run: `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt
git commit -m "feat: sync color theme selection between phone and watch"
```

---

## Task 3: Phone settings persistence — `TimerSettings` / `TimerSettingsRepository`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Test: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `ColorTheme` (Task 1).
- Produces: `TimerSettings.colorTheme: ColorTheme`. `TimerSettingsRepository.updateColorTheme(theme: ColorTheme)`. Tasks 4, 6, and 7 depend on these.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`, inside the class (add the import `import com.pomodoro.timer.core.ColorTheme` at the top alongside the existing imports):

```kotlin
    @Test
    fun `settings default to ColorTheme DEFAULT`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(ColorTheme.DEFAULT, settings.colorTheme)
    }

    @Test
    fun `updateColorTheme persists each theme`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        for (theme in ColorTheme.entries) {
            repository.updateColorTheme(theme)
            assertEquals(theme, repository.settings.first().colorTheme)
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: compile failure — `colorTheme`/`updateColorTheme` unresolved.

- [ ] **Step 3: Add the settings field**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`, add the import `import com.pomodoro.timer.core.ColorTheme` and a field after `useAlarmSound`:

```kotlin
    val useAlarmSound: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT
)
```

- [ ] **Step 4: Add the repository key and method**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`, add the import:

```kotlin
import com.pomodoro.timer.core.ColorTheme
```

Add to the companion object, after `USE_ALARM_SOUND_KEY`:

```kotlin
        val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
```

In the `settings` flow mapping, add to the `TimerSettings(...)` construction, after `useAlarmSound = prefs[USE_ALARM_SOUND_KEY] ?: false`:

```kotlin
            useAlarmSound = prefs[USE_ALARM_SOUND_KEY] ?: false,
            colorTheme = ColorTheme.entries.firstOrNull { it.name == prefs[COLOR_THEME_KEY] } ?: ColorTheme.DEFAULT
```

Add a method after `updateUseAlarmSound`:

```kotlin
    suspend fun updateColorTheme(theme: ColorTheme) {
        dataStore.edit { it[COLOR_THEME_KEY] = theme.name }
    }
```

- [ ] **Step 5: Run the tests and verify they pass, plus the full settings test suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: BUILD SUCCESSFUL, all tests green (existing and new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt
git commit -m "feat: persist color theme selection"
```

---

## Task 4: `TimerUiState` — carry `colorTheme` to the UI layer

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`

**Interfaces:**
- Consumes: `TimerSettings.colorTheme` (Task 3).
- Produces: `TimerUiState.colorTheme: ColorTheme`. Tasks 5, 6, and 7 depend on this.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`, inside the class (add the import `import com.pomodoro.timer.core.ColorTheme` alongside the existing imports):

```kotlin
    @Test
    fun `buildUiState carries colorTheme through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, colorTheme = ColorTheme.OCEAN)

        val uiState = buildUiState(timer, settings)

        assertEquals(ColorTheme.OCEAN, uiState.colorTheme)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest"`
Expected: compile failure — `colorTheme` unresolved on `TimerUiState`/`TimerSettings`.

- [ ] **Step 3: Add the field**

In `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`, add the import `import com.pomodoro.timer.core.ColorTheme` and a field after `useAlarmSound`:

```kotlin
    val useAlarmSound: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT
)

fun buildUiState(timer: TimerState, settings: TimerSettings): TimerUiState = TimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    focusMinutes = settings.focusMinutes,
    breakMinutes = settings.breakMinutes,
    longBreakMinutes = settings.longBreakMinutes,
    completedPomodoros = timer.completedPomodoros,
    pomodorosUntilLongBreak = settings.pomodorosUntilLongBreak,
    autoStartBreak = settings.autoStartBreak,
    autoStartFocus = settings.autoStartFocus,
    keepScreenOn = settings.keepScreenOn,
    dailyGoalEnabled = settings.dailyGoalEnabled,
    dailyGoalTarget = settings.dailyGoalTarget,
    notificationsEnabled = settings.notificationsEnabled,
    vibrationEnabled = settings.vibrationEnabled,
    useAlarmSound = settings.useAlarmSound,
    colorTheme = settings.colorTheme
)
```

- [ ] **Step 4: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt
git commit -m "feat: carry color theme through TimerUiState"
```

---

## Task 5: Phone palettes and `TimerScreen` accent lookup

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/theme/ColorTest.kt` (new)

**Interfaces:**
- Consumes: `ColorTheme` (Task 1), `TimerUiState.colorTheme` (Task 4).
- Produces: `data class PomodoroPalette(primary, onPrimary, background, longBreakAccent: Color)`; `fun paletteFor(theme: ColorTheme, darkTheme: Boolean): PomodoroPalette`; `PomodoroTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: ...)`. Task 7 (`MainActivity`) depends on the `PomodoroTheme` signature change.

`DEFAULT`'s light values (`primary`/`onPrimary`/`background`) and long-break accent must exactly match today's `PomodoroPrimary`/`PomodoroOnPrimary`/`PomodoroBackground`/`PomodoroLongBreakAccent` — this task only reorganizes where those 4 colors live, it must not change what an existing install with no theme selected sees.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/pomodoro/timer/ui/theme/ColorTest.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTest {

    @Test
    fun `paletteFor DEFAULT light matches the original hardcoded colors`() {
        val palette = paletteFor(ColorTheme.DEFAULT, darkTheme = false)

        assertEquals(Color(0xFFE85D4A), palette.primary)
        assertEquals(Color(0xFFFFFFFF), palette.onPrimary)
        assertEquals(Color(0xFFFFFBF7), palette.background)
        assertEquals(Color(0xFF5C6BC0), palette.longBreakAccent)
    }

    @Test
    fun `paletteFor returns a distinct palette per theme`() {
        val themes = ColorTheme.entries
        val lightPrimaries = themes.map { paletteFor(it, darkTheme = false).primary }
        val darkPrimaries = themes.map { paletteFor(it, darkTheme = true).primary }

        assertEquals(themes.size, lightPrimaries.distinct().size)
        assertEquals(themes.size, darkPrimaries.distinct().size)
    }

    @Test
    fun `paletteFor OCEAN differs between light and dark`() {
        val light = paletteFor(ColorTheme.OCEAN, darkTheme = false)
        val dark = paletteFor(ColorTheme.OCEAN, darkTheme = true)

        assertEquals(Color(0xFF1E88E5), light.primary)
        assertEquals(Color(0xFF64B5F6), dark.primary)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.theme.ColorTest"`
Expected: compile failure — `paletteFor`/`PomodoroPalette` unresolved.

- [ ] **Step 3: Replace `Color.kt`'s flat constants with the palette maps**

Replace the full contents of `app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme

data class PomodoroPalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val longBreakAccent: Color
)

private val LightPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFFFFFBF7), Color(0xFF5C6BC0)),
    ColorTheme.OCEAN to PomodoroPalette(Color(0xFF1E88E5), Color(0xFFFFFFFF), Color(0xFFF5FAFF), Color(0xFF26A69A)),
    ColorTheme.FOREST to PomodoroPalette(Color(0xFF43A047), Color(0xFFFFFFFF), Color(0xFFF6FBF4), Color(0xFFFB8C00)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFF8E5DE8), Color(0xFFFFFFFF), Color(0xFFFAF7FF), Color(0xFFEC407A))
)

private val DarkPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFF1C1614), Color(0xFF7986CB)),
    ColorTheme.OCEAN to PomodoroPalette(Color(0xFF64B5F6), Color(0xFF00263D), Color(0xFF0D1B24), Color(0xFF4DB6AC)),
    ColorTheme.FOREST to PomodoroPalette(Color(0xFF81C784), Color(0xFF0B3B12), Color(0xFF10190F), Color(0xFFFFB74D)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFFB69CF0), Color(0xFF2C0F5C), Color(0xFF16101F), Color(0xFFF06292))
)

fun paletteFor(theme: ColorTheme, darkTheme: Boolean): PomodoroPalette =
    (if (darkTheme) DarkPalettes else LightPalettes).getValue(theme)
```

- [ ] **Step 4: Update `Theme.kt` to build the color scheme from the selected palette**

Replace the full contents of `app/src/main/java/com/pomodoro/timer/ui/theme/Theme.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.pomodoro.timer.core.ColorTheme

@Composable
fun PomodoroTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val palette = paletteFor(colorTheme, darkTheme)
    val colors = if (darkTheme) {
        darkColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, background = palette.background)
    } else {
        lightColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, background = palette.background)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PomodoroTypography,
        content = content
    )
}
```

- [ ] **Step 5: Point `TimerScreen` at the palette lookup instead of the old constant**

In `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`, replace the import:

```kotlin
import com.pomodoro.timer.ui.theme.PomodoroLongBreakAccent
```

with:

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import com.pomodoro.timer.ui.theme.paletteFor
```

Change the `CircularProgressIndicator`'s `color` argument from:

```kotlin
                color = progressColorFor(uiState.phase, MaterialTheme.colorScheme.primary, PomodoroLongBreakAccent),
```

to:

```kotlin
                color = progressColorFor(
                    uiState.phase,
                    MaterialTheme.colorScheme.primary,
                    paletteFor(uiState.colorTheme, isSystemInDarkTheme()).longBreakAccent
                ),
```

`progressColorFor`'s own signature is unchanged — it still just picks between two `Color` values — so `TimerScreenColorTest` needs no changes.

- [ ] **Step 6: Run the tests and verify they pass, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (including the existing `TimerScreenColorTest`, untouched).

- [ ] **Step 7: Verify the module compiles end to end (catches the `MainActivity` call site, updated in Task 7)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `PomodoroTheme(colorTheme = ...)` isn't called yet anywhere (existing callers use the trailing-lambda form, which still works because `colorTheme` has a default), so this should already compile before Task 7.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt app/src/main/java/com/pomodoro/timer/ui/theme/Theme.kt app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt app/src/test/java/com/pomodoro/timer/ui/theme/ColorTest.kt
git commit -m "feat: add phone color-theme palettes and wire TimerScreen's accent color to them"
```

---

## Task 6: `TimerService` (phone) and `TimerViewModel` — plumb `colorTheme` through settings, service, and sync

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerSettingsRepository.updateColorTheme` (Task 3), `TimerUiState.colorTheme` (Task 4), `SyncPayload.colorTheme` (Task 1, wired by Task 2).
- Produces: `TimerService.EXTRA_COLOR_THEME` (`String` constant); `TimerViewModel.updateColorTheme(theme: ColorTheme)`. Task 7 (`SettingsScreen`/`PomodoroApp`) depends on these.

No local unit test exists for `TimerService` itself (it's an Android `Service`, instrumented-test territory only, and this environment has no attached device) — its half of this task is verified by compiling. `TimerViewModel`'s half gets a real unit test, same as every other setting.

- [ ] **Step 1: Add the intent extra and static field to `TimerService`**

In `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`, add the import `com.pomodoro.timer.core.ColorTheme`.

Add to the `companion object`, after `EXTRA_USE_ALARM_SOUND`:

```kotlin
        const val EXTRA_COLOR_THEME = "extra_color_theme"
```

Add after `useAlarmSound`:

```kotlin
        private var colorTheme: ColorTheme = ColorTheme.DEFAULT
```

- [ ] **Step 2: Adopt it in `applyRemoteState`**

In `applyRemoteState`, add after `useAlarmSound = payload.useAlarmSound`:

```kotlin
        useAlarmSound = payload.useAlarmSound
        colorTheme = payload.colorTheme
```

- [ ] **Step 3: Include it in `publishSyncState`**

In `publishSyncState`'s `SyncPayload(...)` call, add after `useAlarmSound = useAlarmSound`:

```kotlin
                useAlarmSound = useAlarmSound,
                colorTheme = colorTheme
```

- [ ] **Step 4: Read the extra in `ACTION_UPDATE_DURATIONS`**

In `handleAction`'s `ACTION_UPDATE_DURATIONS` branch, add after `useAlarmSound = intent.getBooleanExtra(EXTRA_USE_ALARM_SOUND, false)`:

```kotlin
                useAlarmSound = intent.getBooleanExtra(EXTRA_USE_ALARM_SOUND, false)
                colorTheme = ColorTheme.entries
                    .firstOrNull { it.name == intent.getStringExtra(EXTRA_COLOR_THEME) } ?: ColorTheme.DEFAULT
```

- [ ] **Step 5: Verify `:app` compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Write the failing `TimerViewModel` test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`, inside the class, after `` `updateDailyGoalEnabled and updateDailyGoalTarget persist through the repository` `` (add the import `import com.pomodoro.timer.core.ColorTheme`):

```kotlin
    @Test
    fun `updateColorTheme persists through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateColorTheme(ColorTheme.FOREST)

        val uiState = viewModel.uiState.first { it.colorTheme == ColorTheme.FOREST }

        assertEquals(ColorTheme.FOREST, uiState.colorTheme)
    }
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: compile failure — `updateColorTheme` unresolved on `TimerViewModel`.

- [ ] **Step 8: Add the method and wire the intent extra**

In `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`, add the import `import com.pomodoro.timer.core.ColorTheme`.

Add after `updateUseAlarmSound`:

```kotlin
    fun updateColorTheme(theme: ColorTheme) {
        viewModelScope.launch { settingsRepository.updateColorTheme(theme) }
    }
```

In `Factory.create`'s `onUpdateDurations` lambda, add after `.putExtra(TimerService.EXTRA_USE_ALARM_SOUND, settings.useAlarmSound)`:

```kotlin
                    .putExtra(TimerService.EXTRA_USE_ALARM_SOUND, settings.useAlarmSound)
                    .putExtra(TimerService.EXTRA_COLOR_THEME, settings.colorTheme.name)
```

- [ ] **Step 9: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerService.kt app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: plumb color theme through TimerService and TimerViewModel"
```

---

## Task 7: Phone UI — `SettingsScreen` swatch row and `MainActivity` wiring

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/MainActivity.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `TimerUiState.colorTheme` (Task 4), `TimerViewModel.updateColorTheme` (Task 6), `PomodoroTheme(colorTheme, content)` (Task 5).
- Produces: nothing consumed elsewhere — the final phone-side task.

This is the one instrumented (`androidTest`) UI change in this plan. This environment has no attached emulator/device to run it, so this task's test step is written but verified only by compiling; run it manually on a device before considering the phone side done.

- [ ] **Step 1: Write the (unrun) instrumented test**

Append to `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`, inside the class (add the import `import com.pomodoro.timer.core.ColorTheme` alongside the existing imports):

```kotlin
    @Test
    fun colorThemeSwatches_areDisplayed_andInvokeCallback() {
        var themeSelected: ColorTheme? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    colorTheme = ColorTheme.DEFAULT,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onColorThemeChange = { themeSelected = it },
                    onBack = {}
                )
            }
        }

        for (theme in ColorTheme.entries) {
            composeRule.onNodeWithTag("colorThemeSwatch_${theme.name}").assertExists()
        }

        composeRule.onNodeWithTag("colorThemeSwatch_${ColorTheme.OCEAN.name}").performScrollTo().performClick()

        assertTrue(themeSelected == ColorTheme.OCEAN)
    }
```

- [ ] **Step 2: Add the swatch row to `SettingsScreen`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`, add imports:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.ui.theme.paletteFor
```

(`Arrangement`, `Row`, `Spacer`, `Text`, `MaterialTheme`, `Modifier`, `dp` are already imported in this file from the other settings rows; `Color` isn't needed since the swatch color comes from `PomodoroPalette.primary`, already a `Color`.)

Add two new parameters to the function signature, after `onUseAlarmSoundChange`:

```kotlin
    onUseAlarmSoundChange: (Boolean) -> Unit = {},
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    onColorThemeChange: (ColorTheme) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
```

Add a "테마" section after the `useAlarmSound` Row block and before the final `Spacer`/`Button`:

```kotlin
        Spacer(Modifier.height(24.dp))
        Text("테마")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (theme in ColorTheme.entries) {
                val swatchColor = paletteFor(theme, darkTheme = false).primary
                val isSelected = theme == colorTheme
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = CircleShape
                        )
                        .clickable { onColorThemeChange(theme) }
                        .testTag("colorThemeSwatch_${theme.name}")
                )
            }
        }
```

- [ ] **Step 3: Wire it up in `PomodoroApp`**

In `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`, extend the `SettingsScreen(...)` call, adding after `onUseAlarmSoundChange = viewModel::updateUseAlarmSound`:

```kotlin
                onUseAlarmSoundChange = viewModel::updateUseAlarmSound,
                colorTheme = uiState.colorTheme,
                onColorThemeChange = viewModel::updateColorTheme,
                onBack = { navController.popBackStack() }
```

- [ ] **Step 4: Wire the selected theme into `PomodoroTheme` in `MainActivity`**

In `app/src/main/java/com/pomodoro/timer/MainActivity.kt`, add the import `androidx.lifecycle.compose.collectAsStateWithLifecycle`.

Change `setContent`:

```kotlin
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PomodoroTheme(colorTheme = uiState.colorTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PomodoroApp(viewModel)
                }
            }
        }
```

`PomodoroApp` keeps collecting its own `uiState` internally for `TimerScreen`/`SettingsScreen` — this is a second, independent collector of the same `StateFlow`, which is normal in Compose and avoids restructuring `PomodoroApp`'s signature.

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt app/src/main/java/com/pomodoro/timer/PomodoroApp.kt app/src/main/java/com/pomodoro/timer/MainActivity.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
git commit -m "feat: add color theme swatch picker to SettingsScreen and wire it into the app theme"
```

---

## Task 8: `WearTimerService`, `WearTimerUiState`, `WearTimerViewModel` — mirror `colorTheme` from sync

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt`
- Test: `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt` (new, only if no such file exists — see Step 3)

**Interfaces:**
- Consumes: `SyncPayload.colorTheme` (Task 1, wired by Task 2).
- Produces: `WearTimerService.colorTheme: StateFlow<ColorTheme>`; `WearTimerUiState.colorTheme: ColorTheme`. Task 9 depends on both.

Unlike `autoStartBreak`/`dailyGoalEnabled` (kept private in `WearTimerService` because nothing in `:wear`'s UI reads them back out), `colorTheme` **must** be exposed publicly — the watch's own theme rendering needs it, the same reason `pomodorosUntilLongBreak` is already exposed as a `StateFlow` instead of a plain `var`.

- [ ] **Step 1: Expose `colorTheme` as a `StateFlow` in `WearTimerService`**

In `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`, add the import `com.pomodoro.timer.core.ColorTheme`.

Add to the companion object, after `private var useAlarmSound: Boolean = false` (the last field):

```kotlin

        // Exposed publicly (unlike _autoStartBreak/_dailyGoalEnabled above): the watch's own theme
        // rendering needs this value, the same reason pomodorosUntilLongBreak is a StateFlow.
        private val _colorTheme = MutableStateFlow(ColorTheme.DEFAULT)
        val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()
```

- [ ] **Step 2: Adopt it in `applyRemoteState` and include it in `onLocalStateChanged`**

In `applyRemoteState`, add after `useAlarmSound = payload.useAlarmSound`:

```kotlin
        useAlarmSound = payload.useAlarmSound
        _colorTheme.value = payload.colorTheme
```

In `onLocalStateChanged`'s `SyncPayload(...)` call, add after `useAlarmSound = useAlarmSound`:

```kotlin
                useAlarmSound = useAlarmSound,
                colorTheme = _colorTheme.value
```

- [ ] **Step 3: Add `colorTheme` to `WearTimerUiState`**

Check whether `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt` already exists (`ls wear/src/test/java/com/pomodoro/timer/wear/ui/`); if it does not, this step creates it fresh instead of appending.

Write the failing test — create or append to `WearTimerUiStateTest.kt`:

```kotlin
package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerUiStateTest {

    @Test
    fun `buildWearUiState carries colorTheme through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)

        val uiState = buildWearUiState(timer, pomodorosUntilLongBreak = 4, colorTheme = ColorTheme.LAVENDER)

        assertEquals(ColorTheme.LAVENDER, uiState.colorTheme)
    }
}
```

Run: `./gradlew :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerUiStateTest"`
Expected: compile failure — `colorTheme` parameter/field unresolved.

Update `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt` to:

```kotlin
package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.ColorTheme
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState

data class WearTimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val completedPomodoros: Int = 0,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT
)

fun buildWearUiState(
    timer: TimerState,
    pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    colorTheme: ColorTheme = ColorTheme.DEFAULT
): WearTimerUiState = WearTimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    completedPomodoros = timer.completedPomodoros,
    pomodorosUntilLongBreak = pomodorosUntilLongBreak,
    colorTheme = colorTheme
)
```

Run: `./gradlew :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerUiStateTest"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Combine the new flow in `WearTimerViewModel`**

In `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt`, change the constructor and `uiState`:

```kotlin
class WearTimerViewModel(
    timerState: StateFlow<TimerState>,
    pomodorosUntilLongBreak: StateFlow<Int>,
    colorTheme: StateFlow<ColorTheme>,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<WearTimerUiState> =
        combine(timerState, pomodorosUntilLongBreak, colorTheme, ::buildWearUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearTimerUiState())
```

Add the import `import com.pomodoro.timer.core.ColorTheme`.

Update `Factory.create`:

```kotlin
            return WearTimerViewModel(
                WearTimerService.state,
                WearTimerService.pomodorosUntilLongBreak,
                WearTimerService.colorTheme,
                onCommand
            ) as T
```

- [ ] **Step 5: Verify the module compiles and tests pass**

Run: `./gradlew :wear:compileDebugKotlin :wear:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt
git commit -m "feat: mirror color theme selection to the watch via sync"
```

---

## Task 9: Watch palettes, `WearTimerScreen` accent lookup, and `MainActivity` wiring

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`
- Test: `wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt` (new)

**Interfaces:**
- Consumes: `ColorTheme` (Task 1), `WearTimerUiState.colorTheme` (Task 8).
- Produces: nothing consumed elsewhere — the final watch-side task.

`DEFAULT`'s values must exactly match today's fixed `PomodoroWearColors`/`PomodoroWearLongBreakAccent` — an existing installed watch with no theme ever selected (cold start, before its first sync) renders `ColorTheme.DEFAULT` and must look identical to before this feature.

- [ ] **Step 1: Write the failing test**

Create `wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt`:

```kotlin
package com.pomodoro.timer.wear.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WearThemeTest {

    @Test
    fun `wearPaletteFor DEFAULT matches the original hardcoded colors`() {
        val palette = wearPaletteFor(ColorTheme.DEFAULT)

        assertEquals(Color(0xFFE85D4A), palette.primary)
        assertEquals(Color(0xFFB6432F), palette.primaryVariant)
        assertEquals(Color(0xFFFFFFFF), palette.onPrimary)
        assertEquals(Color(0xFF000000), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.onBackground)
        assertEquals(Color(0xFF5C6BC0), palette.longBreakAccent)
    }

    @Test
    fun `wearPaletteFor returns a distinct primary per theme`() {
        val primaries = ColorTheme.entries.map { wearPaletteFor(it).primary }

        assertEquals(ColorTheme.entries.size, primaries.distinct().size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.theme.WearThemeTest"`
Expected: compile failure — `wearPaletteFor` unresolved.

- [ ] **Step 3: Replace `WearTheme.kt`'s fixed colors with the palette map**

Replace the full contents of `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`:

```kotlin
package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.pomodoro.timer.core.ColorTheme

data class WearPalette(
    val primary: Color,
    val primaryVariant: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val longBreakAccent: Color
)

// The watch has no system dark-mode concept and its background is fixed at black regardless of
// theme (existing behavior) — so every theme here uses its phone dark-palette's brighter primary
// and accent (better contrast against black than the light-mode primary would give).
private val WearPalettes: Map<ColorTheme, WearPalette> = mapOf(
    ColorTheme.DEFAULT to WearPalette(
        primary = Color(0xFFE85D4A),
        primaryVariant = Color(0xFFB6432F),
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFF5C6BC0)
    ),
    ColorTheme.OCEAN to WearPalette(
        primary = Color(0xFF64B5F6),
        primaryVariant = Color(0xFF1E88E5),
        onPrimary = Color(0xFF00263D),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFF4DB6AC)
    ),
    ColorTheme.FOREST to WearPalette(
        primary = Color(0xFF81C784),
        primaryVariant = Color(0xFF43A047),
        onPrimary = Color(0xFF0B3B12),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFFFFB74D)
    ),
    ColorTheme.LAVENDER to WearPalette(
        primary = Color(0xFFB69CF0),
        primaryVariant = Color(0xFF8E5DE8),
        onPrimary = Color(0xFF2C0F5C),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        longBreakAccent = Color(0xFFF06292)
    )
)

fun wearPaletteFor(theme: ColorTheme): WearPalette = WearPalettes.getValue(theme)

@Composable
fun PomodoroWearTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val palette = wearPaletteFor(colorTheme)
    val colors = Colors(
        primary = palette.primary,
        primaryVariant = palette.primaryVariant,
        onPrimary = palette.onPrimary,
        background = palette.background,
        onBackground = palette.onBackground
    )
    MaterialTheme(colors = colors, content = content)
}
```

- [ ] **Step 4: Point `WearTimerScreen` at the palette lookup instead of the old constant**

In `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`, replace the import:

```kotlin
import com.pomodoro.timer.wear.theme.PomodoroWearLongBreakAccent
```

with:

```kotlin
import com.pomodoro.timer.wear.theme.wearPaletteFor
```

Change the `CircularProgressIndicator`'s `indicatorColor` argument from:

```kotlin
                indicatorColor = progressColorFor(uiState.phase, MaterialTheme.colors.primary, PomodoroWearLongBreakAccent)
```

to:

```kotlin
                indicatorColor = progressColorFor(
                    uiState.phase,
                    MaterialTheme.colors.primary,
                    wearPaletteFor(uiState.colorTheme).longBreakAccent
                )
```

`progressColorFor`'s own signature is unchanged, so `WearTimerScreenColorTest` needs no changes.

- [ ] **Step 5: Wire the selected theme into `PomodoroWearTheme` in `MainActivity`**

In `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`, move the `uiState` collection above `PomodoroWearTheme` and pass `colorTheme` through:

```kotlin
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PomodoroWearTheme(colorTheme = uiState.colorTheme) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
                    val ambient by isAmbient
                    WearTimerScreen(
                        uiState = uiState,
                        isAmbient = ambient,
                        onStart = viewModel::start,
                        onPause = viewModel::pause,
                        onReset = viewModel::reset
                    )
                }
            }
        }
```

- [ ] **Step 6: Run the tests and verify they pass, plus the full wear unit suite; then verify compile**

Run: `./gradlew :wear:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (including the existing `WearTimerScreenColorTest`, untouched).

Run: `./gradlew :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt
git commit -m "feat: add watch color-theme palettes and wire WearTimerScreen and MainActivity to them"
```

- [ ] **Step 8 (manual, on a real device pair — not part of this automated plan):** On the phone, open Settings, cycle through all 4 swatches; confirm `TimerScreen`'s progress ring, buttons, and background update immediately and correctly in both system light and dark mode, and that the selected swatch shows its border. Confirm the watch (left connected) picks up each change within the existing sync latency, including its progress-ring accent color. Confirm a fresh app install (or cleared app data) defaults to `DEFAULT` on both devices and looks pixel-identical to the pre-feature app. Confirm the app restart / process-death case: selecting a non-default theme, killing the app, and reopening it shows the persisted theme immediately (phone) and after the next sync (watch).

---

## Final Verification

- [ ] Run `./gradlew :core:compileKotlin :app:testDebugUnitTest :wear:testDebugUnitTest` once more from a clean state and confirm everything is green.
- [ ] Run `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin` and confirm both compile.
- [ ] Update `docs/feature-backlog.md`: mark item 6 done, following the same style used for items 1, 1a, 2, 3, 4, and 5.
