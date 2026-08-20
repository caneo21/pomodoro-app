# Color Themes — Design Spec

**Goal:** Let the user pick one of several color themes for the app's UI, matching the reference app `com.pomodrone.app` (which offers ~20). This is feature 6 of the feature-gap backlog (`docs/feature-backlog.md`); features 1, 1a, 2, 3, 4, and 5 are already done.

**Non-goals:** onboarding flow (item 7), user-created/custom themes, per-element theme editing, watch-side theme selection UI, more than 4 themes at launch (structure should make adding more cheap later, but this iteration ships 4).

## Behavior Summary (agreed with user)

- A theme controls the **full palette**: `primary`, `onPrimary`, `background`, and the long-break accent color (`PomodoroLongBreakAccent`/`PomodoroWearLongBreakAccent`) — not just the primary accent.
- Each theme defines **both a light and a dark variant**; the app still auto-switches between them via `isSystemInDarkTheme()`, exactly as today. Picking a theme does not override system dark mode — it only changes which palette pair is active.
- **4 themes at launch**: `DEFAULT` (current orange, unchanged), `OCEAN` (blue/teal), `FOREST` (green), `LAVENDER` (purple). Structured as an open enum so a 5th theme is a small addition (see Section A), not a redesign.
- **Phone-only selection UI**, in the existing `SettingsScreen` (a new "테마" section with 4 tappable color swatches) — consistent with every other setting so far. The **watch has no theme picker**; it only renders whatever theme is currently synced from the phone, the same relationship autostart/daily-goal/notification settings already have.

## Section A: Theme Identity (`:core`)

New file `core/src/main/kotlin/com/pomodoro/timer/core/ColorTheme.kt`:

```kotlin
enum class ColorTheme {
    DEFAULT, OCEAN, FOREST, LAVENDER
}
```

`:core` holds only the identifier, never actual color values — it has no Compose dependency today (see `TimerPhase`) and this should not be the feature that adds one. Precedent: `PomodoroLongBreakAccent` (app) and `PomodoroWearLongBreakAccent` (wear) already duplicate the same hex value in each UI module rather than sharing it through `:core`; this feature extends that same duplication to a 4-theme catalog instead of introducing a shared palette data type. `TimerDefaults` gains `val DEFAULT_COLOR_THEME = ColorTheme.DEFAULT`.

## Section B: Palettes (`:app`, `:wear`)

**`app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt`**: replace the flat `Pomodoro*` constants with one palette per theme, light and dark:

```kotlin
data class PomodoroPalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val longBreakAccent: Color
)

val LightPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFFFFFBF7), Color(0xFF5C6BC0)),
    ColorTheme.OCEAN   to PomodoroPalette(Color(0xFF1E88E5), Color(0xFFFFFFFF), Color(0xFFF5FAFF), Color(0xFF26A69A)),
    ColorTheme.FOREST  to PomodoroPalette(Color(0xFF43A047), Color(0xFFFFFFFF), Color(0xFFF6FBF4), Color(0xFFFB8C00)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFF8E5DE8), Color(0xFFFFFFFF), Color(0xFFFAF7FF), Color(0xFFEC407A))
)

val DarkPalettes: Map<ColorTheme, PomodoroPalette> = mapOf(
    ColorTheme.DEFAULT to PomodoroPalette(Color(0xFFE85D4A), Color(0xFFFFFFFF), Color(0xFF1C1614), Color(0xFF7986CB)),
    ColorTheme.OCEAN   to PomodoroPalette(Color(0xFF64B5F6), Color(0xFF00263D), Color(0xFF0D1B24), Color(0xFF4DB6AC)),
    ColorTheme.FOREST  to PomodoroPalette(Color(0xFF81C784), Color(0xFF0B3B12), Color(0xFF10190F), Color(0xFFFFB74D)),
    ColorTheme.LAVENDER to PomodoroPalette(Color(0xFFB69CF0), Color(0xFF2C0F5C), Color(0xFF16101F), Color(0xFFF06292))
)
```

**`Theme.kt`**: `PomodoroTheme(colorTheme: ColorTheme, content: ...)` looks up the palette for `colorTheme` and `isSystemInDarkTheme()`, builds `lightColorScheme`/`darkColorScheme` from it (as today), and exposes the palette's `longBreakAccent` via a small `LocalLongBreakAccent` composition local (or a second parameter threaded down to `TimerScreen`/`WearTimerScreen`) so `progressColorFor` no longer reads the single global `PomodoroLongBreakAccent` constant — it reads the active theme's accent instead. `MainActivity` passes the current `colorTheme` (from `TimerUiState`) into `PomodoroTheme`.

**`wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`**: same shape, using Wear Compose's `Colors` instead of Material3 `ColorScheme` — a `WearPalettes: Map<ColorTheme, WearPalette>` (light only; Wear OS has no system dark-mode concept here, matching the existing single fixed `PomodoroWearColors`) with `primary`, `primaryVariant` (derive from primary, as today), `onPrimary`, `background`, `onBackground`, and `longBreakAccent`. `PomodoroWearTheme(colorTheme: ColorTheme, content: ...)`.

## Section C: Settings & Sync (`:core`, `:app`, `:wear`)

**`TimerSettings` / `TimerSettingsRepository`** (`app/src/main/java/com/pomodoro/timer/settings/`):
- `TimerSettings` gains `colorTheme: ColorTheme = ColorTheme.DEFAULT`.
- `TimerSettingsRepository` gains `COLOR_THEME_KEY = stringPreferencesKey("color_theme")`, read as `ColorTheme.valueOf(prefs[COLOR_THEME_KEY] ?: ColorTheme.DEFAULT.name)` (mirrors `TimerPhase` decode via `enumValues<>()`/`valueOf` elsewhere in the codebase), and `suspend fun updateColorTheme(theme: ColorTheme)` following `updateNotificationsEnabled()` exactly.

**`SyncPayload`**: add `colorTheme: ColorTheme = ColorTheme.DEFAULT`.

**`SyncKeys`**: add `const val KEY_COLOR_THEME = "colorTheme"`.

**`PlayServicesSyncTransport` / `WearPlayServicesSyncTransport`**: `encode` writes `dataMap.putString(SyncKeys.KEY_COLOR_THEME, payload.colorTheme.name)`; `decode` reads it back with a safe fallback to `ColorTheme.DEFAULT` if absent or unrecognized (forward-compatible with a peer on an older build that never sent this key) — same shape as the existing `phase` decode.

**`TimerService` (phone)**:
- `EXTRA_COLOR_THEME = "extra_color_theme"`; static `private var colorTheme: ColorTheme = ColorTheme.DEFAULT`.
- `ACTION_UPDATE_DURATIONS` parses it via `ColorTheme.valueOf(intent.getStringExtra(EXTRA_COLOR_THEME) ?: ColorTheme.DEFAULT.name)`, same as the other new settings extras.
- `applyRemoteState` adopts `payload.colorTheme` into the static field.
- `publishSyncState` includes `colorTheme = colorTheme`.

**`WearTimerService`**: same shape as `notificationsEnabled`/etc — an in-memory `_colorTheme: MutableStateFlow<ColorTheme>`, hydrated purely from sync (no local persistence, consistent with the watch's existing settings), included in outgoing `SyncPayload`, adopted in `applyRemoteState`.

**Settings propagation path**: `TimerViewModel.updateColorTheme(theme: ColorTheme)` calls `settingsRepository.updateColorTheme(theme)`; `TimerViewModel.Factory`'s `onUpdateDurations` lambda adds `.putExtra(TimerService.EXTRA_COLOR_THEME, settings.colorTheme.name)`. `TimerUiState` / `buildUiState` gain `colorTheme: ColorTheme = ColorTheme.DEFAULT`.

## Section D: UI

**Phone `SettingsScreen`**: new "테마" section (placed after the existing toggles, before the back button) — a `Text("테마")` label followed by a `Row` of 4 tappable circular swatches, one per `ColorTheme`, filled with that theme's **light-mode `primary`** color as its preview. The selected theme's swatch gets a distinguishing border/checkmark. Each swatch: `Modifier.testTag("colorThemeSwatch_${theme.name}")`, `onClick` invokes `onColorThemeChange(theme)`. `SettingsScreen` gains `colorTheme: ColorTheme = ColorTheme.DEFAULT` and `onColorThemeChange: (ColorTheme) -> Unit = {}` parameters, following the existing prop pattern exactly.

**Watch**: no UI change. `WearTimerScreen`/`WearTheme` just render whatever `colorTheme` arrived via sync.

## Testing Strategy

- **`TimerSettingsRepositoryTest`**: default is `ColorTheme.DEFAULT`; `updateColorTheme` round-trips each of the 4 values.
- **Sync round-trip test** (wherever `SyncPayload` encode/decode is currently covered, e.g. alongside the existing `phase`/`notificationsEnabled` cases): `colorTheme` survives encode→decode for all 4 values, and decode falls back to `DEFAULT` when the key is absent.
- **`TimerScreenColorTest` / `WearTimerScreenColorTest`**: extend (or add a sibling test for) the palette lookup — each `ColorTheme` resolves to its expected light/dark `primary`/`longBreakAccent`, and `progressColorFor` still picks the accent over primary for `LONG_BREAK` regardless of which theme is active.
- **`TimerUiStateTest`**: `buildUiState` carries `colorTheme` through from `TimerSettings`.
- **`SettingsScreen`**: instrumented test — tapping a swatch invokes `onColorThemeChange` with the right theme, and the currently-selected swatch renders its selected-state marker. As with prior features, this environment has no attached emulator/device for `androidTest`, so this is called out for manual/CI verification rather than run locally.
- **Manual real-device verification**: cycle through all 4 themes on the phone, confirming `TimerScreen`'s progress ring, buttons, and background update immediately and correctly in both system light and dark mode; confirm the watch (left connected) picks up each change within the existing sync latency; confirm a fresh app install defaults to `DEFAULT` on both devices.

## Out of Scope

Onboarding flow, custom/user-defined themes, more than 4 themes, watch-side theme picker UI, per-element theme customization beyond the 4-color palette.
