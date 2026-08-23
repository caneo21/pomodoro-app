# Wear OS Modern Design Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the shared minimal-monochrome design tokens (color, typography, shape, motion) to the Wear OS watch app's timer screen — the first of three platforms to receive the redesign.

**Architecture:** This is a token/styling pass over two existing files (`WearTheme.kt`, `WearTimerScreen.kt`) that were already migrated to Wear Compose Material 3 Expressive this session — no new screens, no new state, no changes to `WearTimerViewModel`/`WearTimerUiState`/sync. `WearPalette` collapses from a 2-tone (`primary`+`primaryVariant`) per-theme shape to a 1-tone (`accent`) shape, the long-break accent becomes one shared constant instead of 4 per-theme values, a bundled variable font (Manrope) renders the countdown digits only, the progress ring gets thinner/larger, and progress/color changes get spring-based animation. Button press motion needs no new code — Wear Compose Material 3's `IconButton`/`FilledIconButton` already animate their shape on press via `rememberAnimatedPressedButtonShape` (confirmed by reading the library's source, `RoundButton.kt`).

**Tech Stack:** Kotlin, Jetpack Compose for Wear OS, `androidx.wear.compose:compose-material3:1.6.2`, JUnit (unit tests), `androidx.compose.ui.test` (instrumented tests, run via `./gradlew :wear:connectedDebugAndroidTest` against a real device — no emulator available with a working Wearable Data Layer in this environment, confirmed this session).

**Spec:** `docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md`

## Global Constraints

- Timer countdown digits (`MM:SS`) use **Manrope**, weight 800, tabular figures — everywhere else keeps the platform's native font.
- Neutrals (dark, the watch's only mode): `background = #0D0D0F`, `onBackground` (text) = `#F2F2F4`.
- Per-theme accent (single tone, dark-bg column from the spec): Default `#FF6B57`, Ocean `#5AA9FF`, Forest `#6FCB7A`, Lavender `#B08CF0`.
- Long-break accent is **one shared value** across all themes: `#6B7BFF` (was 4 different per-theme values).
- Shapes are already fully round (Wear M3 `IconButton`/`FilledIconButton` defaults) — no change needed there. Progress ring goes thinner (`CircularProgressIndicatorDefaults.smallStrokeWidth` instead of the default `largeStrokeWidth`) and larger (150.dp, up from 130.dp).
- Motion is spring-based, not linear — applies to progress ring value and ring/phase color changes. Ambient mode stays motion-free (unchanged, battery-driven).
- No changes to `WearTimerViewModel`, `WearTimerUiState`, `SyncPayload`, or any sync/service code — this plan is presentation-layer only.

---

## Task 1: Bundle the Manrope variable font resource

**Files:**
- Create: `wear/src/main/res/font/manrope_variable.ttf` (binary, downloaded — not written as text)
- Test: none (a font resource has no unit-testable behavior); verified by a successful resource compile

**Interfaces:**
- Produces: the Android resource `R.font.manrope_variable`, consumed by Task 3.

- [ ] **Step 1: Create the font resource directory**

```bash
mkdir -p wear/src/main/res/font
```

- [ ] **Step 2: Download the Manrope variable font**

This is Google's official variable-font build of Manrope (OFL-licensed), verified reachable this session:

```bash
curl -sL "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf" -o wear/src/main/res/font/manrope_variable.ttf
```

Android resource filenames must be lowercase `[a-z0-9_]+` — that's why the file is renamed from `Manrope[wght].ttf` to `manrope_variable.ttf` on download.

- [ ] **Step 3: Verify the download**

```bash
ls -la wear/src/main/res/font/manrope_variable.ttf
```

Expected: a file of roughly 165KB (the exact size fetched this session was 165,420 bytes). If the file is 0 bytes or a few KB of HTML, the download failed — check network access and retry.

- [ ] **Step 4: Verify the resource compiles**

```bash
./gradlew :wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. A malformed or misnamed font resource fails resource merging at this step, not later.

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/res/font/manrope_variable.ttf
git commit -m "feat(wear): bundle Manrope variable font for timer digits"
```

---

## Task 2: Redefine `WearPalette` to the monochrome token set

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`
- Modify: `wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt`

**Interfaces:**
- Produces: `data class WearPalette(val accent: Color, val onAccent: Color, val background: Color, val onBackground: Color)`, `val WearLongBreakAccent: Color`, `fun wearPaletteFor(theme: ColorTheme): WearPalette`, `fun wearColorScheme(palette: WearPalette): ColorScheme` — the `wearPaletteFor`/`wearColorScheme` function names and `PomodoroWearTheme` composable are unchanged from today; only `WearPalette`'s fields and the removal of the per-palette `longBreakAccent` field are new. Consumed by Task 3.

The per-theme accent hex values below reuse each theme's existing dark-mode `onPrimary` value as the new `onAccent` — the new accent hexes are near-identical in hue/lightness to the old dark-mode `primary` values they replace (e.g. Ocean: old `#64B5F6` → new `#5AA9FF`), so the existing contrast pairing still holds without picking new values from scratch.

- [ ] **Step 1: Write the failing tests**

Replace the full contents of `wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt`:

```kotlin
package com.pomodoro.timer.wear.theme

import androidx.compose.ui.graphics.Color
import com.pomodoro.timer.core.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WearThemeTest {

    @Test
    fun `wearPaletteFor DEFAULT matches the monochrome token values`() {
        val palette = wearPaletteFor(ColorTheme.DEFAULT)

        assertEquals(Color(0xFFFF6B57), palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.onAccent)
        assertEquals(Color(0xFF0D0D0F), palette.background)
        assertEquals(Color(0xFFF2F2F4), palette.onBackground)
    }

    @Test
    fun `wearPaletteFor returns a distinct accent per theme`() {
        val accents = ColorTheme.entries.map { wearPaletteFor(it).accent }

        assertEquals(ColorTheme.entries.size, accents.distinct().size)
    }

    @Test
    fun `every theme shares the same monochrome background and a single shared long-break accent`() {
        val backgrounds = ColorTheme.entries.map { wearPaletteFor(it).background }.distinct()
        val onBackgrounds = ColorTheme.entries.map { wearPaletteFor(it).onBackground }.distinct()

        assertEquals(listOf(Color(0xFF0D0D0F)), backgrounds)
        assertEquals(listOf(Color(0xFFF2F2F4)), onBackgrounds)
        assertEquals(Color(0xFF6B7BFF), WearLongBreakAccent)
    }

    @Test
    fun `wearColorScheme carries the palette's accent and neutrals into the M3 scheme`() {
        val palette = wearPaletteFor(ColorTheme.OCEAN)

        val scheme = wearColorScheme(palette)

        assertEquals(palette.accent, scheme.primary)
        assertEquals(palette.onAccent, scheme.onPrimary)
        assertEquals(palette.background, scheme.background)
        assertEquals(palette.onBackground, scheme.onBackground)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.theme.WearThemeTest"
```

Expected: **compile failure** — `Unresolved reference 'accent'`, `'onAccent'`, and `'WearLongBreakAccent'` (the old `WearPalette` still has `primary`/`primaryVariant`/`longBreakAccent`). A compile error here is the correct RED state — it fails because the new shape doesn't exist yet, not because of a typo.

- [ ] **Step 3: Replace `WearTheme.kt`**

Replace the full contents of `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`:

```kotlin
package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.pomodoro.timer.core.ColorTheme

data class WearPalette(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val onBackground: Color
)

// Minimal-monochrome tokens (docs/superpowers/specs/2026-08-23-modern-design-tokens-design.md,
// Section A). The watch is always dark (unchanged, battery-driven), so only the dark-bg accent
// column from the spec applies here — there's no light variant. Background/onBackground are
// identical across every theme; only the accent differs, which is the whole point of moving from
// a 2-tone (primary + primaryVariant) palette to a single restrained accent per theme.
private val WearNeutralBackground = Color(0xFF0D0D0F)
private val WearNeutralOnBackground = Color(0xFFF2F2F4)
val WearLongBreakAccent = Color(0xFF6B7BFF)

private val WearPalettes: Map<ColorTheme, WearPalette> = mapOf(
    ColorTheme.DEFAULT to WearPalette(
        accent = Color(0xFFFF6B57),
        onAccent = Color(0xFFFFFFFF),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.OCEAN to WearPalette(
        accent = Color(0xFF5AA9FF),
        onAccent = Color(0xFF00263D),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.FOREST to WearPalette(
        accent = Color(0xFF6FCB7A),
        onAccent = Color(0xFF0B3B12),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    ),
    ColorTheme.LAVENDER to WearPalette(
        accent = Color(0xFFB08CF0),
        onAccent = Color(0xFF2C0F5C),
        background = WearNeutralBackground,
        onBackground = WearNeutralOnBackground
    )
)

fun wearPaletteFor(theme: ColorTheme): WearPalette = WearPalettes.getValue(theme)

fun wearColorScheme(palette: WearPalette): ColorScheme = ColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    background = palette.background,
    onBackground = palette.onBackground
)

@Composable
fun PomodoroWearTheme(colorTheme: ColorTheme = ColorTheme.DEFAULT, content: @Composable () -> Unit) {
    val palette = wearPaletteFor(colorTheme)
    MaterialTheme(colorScheme = wearColorScheme(palette), content = content)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.theme.WearThemeTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Confirm the rest of the module still compiles**

`WearTimerScreen.kt` still references `wearPaletteFor(...).longBreakAccent` at this point, which no longer exists — this is expected and gets fixed in Task 3, not here. Confirm the *test* module compiles in isolation:

```bash
./gradlew :wear:compileDebugKotlin
```

Expected: **FAILS** with `Unresolved reference 'longBreakAccent'` in `WearTimerScreen.kt` — this is the correct, expected state between Task 2 and Task 3. Do not attempt to fix it here.

- [ ] **Step 6: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt wear/src/test/java/com/pomodoro/timer/wear/theme/WearThemeTest.kt
git commit -m "feat(wear): collapse WearPalette to a single monochrome accent per theme"
```

---

## Task 3: Apply the new palette, Manrope digits, and a thinner/larger progress ring in `WearTimerScreen.kt`

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`
- Test: `wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt` (existing instrumented test — no changes needed, it's the regression check for this task)

**Interfaces:**
- Consumes: `WearPalette(accent, onAccent, background, onBackground)`, `WearLongBreakAccent: Color`, `wearPaletteFor(ColorTheme): WearPalette` from Task 2.
- Produces: no new public symbols — `WearTimerScreen`, `progressColorFor`, `formatWearMillis` keep their existing signatures (Task 4 builds on this file's current state).

`progressColorFor(phase, primary, longBreakAccent): Color` (unchanged, pure function) now gets called with `MaterialTheme.colorScheme.primary` (still correct — it now resolves to the theme's `accent`, via `wearColorScheme`) and `WearLongBreakAccent` instead of a per-palette field. `WearTimerScreenColorTest.kt` needs no changes since the function signature is untouched.

- [ ] **Step 1: Update imports and add the Manrope font family**

In `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`, replace the import block and add the font declaration right after it:

```kotlin
package com.pomodoro.timer.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.wear.R
import com.pomodoro.timer.wear.theme.WearLongBreakAccent
import com.pomodoro.timer.wear.theme.wearPaletteFor

@OptIn(ExperimentalTextApi::class)
private val ManropeExtraBold = FontFamily(
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.W800,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)
```

`FontVariation.Settings`/`FontVariation.weight` and the 5-parameter `Font(resId, weight, style, loadingStrategy, variationSettings)` overload are `@ExperimentalTextApi` in this Compose UI version (confirmed against the `ui-text` 1.8.2 sources this session) — the `@OptIn` is required or this won't compile.

- [ ] **Step 2: Update the ring color computation to use the shared long-break accent**

Replace:

```kotlin
    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        wearPaletteFor(uiState.colorTheme).longBreakAccent
    )
```

with:

```kotlin
    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        WearLongBreakAccent
    )
```

- [ ] **Step 3: Make the progress ring thinner and larger, and apply Manrope to the countdown digits**

Replace the progress ring `Box`:

```kotlin
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(130.dp),
                colors = ProgressIndicatorDefaults.colors(indicatorColor = ringColor)
            )
            Text(text = remainingText, style = MaterialTheme.typography.displaySmall)
        }
```

with:

```kotlin
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(150.dp),
                strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = ringColor)
            )
            Text(
                text = remainingText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = ManropeExtraBold,
                    fontWeight = FontWeight.W800,
                    fontFeatureSettings = "tnum"
                )
            )
        }
```

`fontFeatureSettings = "tnum"` turns on OpenType tabular figures, so the digits never shift width as they change. `CircularProgressIndicatorDefaults.smallStrokeWidth` is the library's own "thin" stroke token (the default is `largeStrokeWidth`) — using the token instead of a hand-picked `Dp` value keeps it consistent with whatever the library considers "small" as the design system evolves.

- [ ] **Step 4: Apply the same digit treatment to the ambient-mode text**

Replace:

```kotlin
    if (isAmbient) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = remainingText, color = Color.White)
        }
        return
    }
```

with:

```kotlin
    if (isAmbient) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = remainingText,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = ManropeExtraBold,
                    fontWeight = FontWeight.W800,
                    fontFeatureSettings = "tnum"
                )
            )
        }
        return
    }
```

Ambient mode keeps its plain white color and zero motion (unchanged, battery-driven) — only the digit typography changes, since it's the same countdown the user reads either way.

- [ ] **Step 5: Build and confirm the module compiles**

```bash
./gradlew :wear:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. This is the first successful compile since Task 2's Step 5 — confirms `WearLongBreakAccent`, the font resource, and the new `CircularProgressIndicatorDefaults`/`FontVariation` APIs all resolve correctly together.

- [ ] **Step 6: Run the unit tests**

```bash
./gradlew :wear:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass, including the untouched `WearTimerScreenColorTest` (`progressColorFor` is a pure function and its signature didn't change).

- [ ] **Step 7: Run the instrumented UI test on a real device**

This project has no working Wear OS emulator (Google Play services' Wearable API isn't available on the local AVD image, confirmed this session) — run against a real connected watch:

```bash
adb devices -l
./gradlew :wear:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pomodoro.timer.wear.ui.WearTimerScreenTest
```

Expected: 5/5 tests pass on the watch device (`startButton_isShownWhenIdle_andInvokesCallback`, `pauseButton_isShownWhenRunning_andInvokesCallback`, `resetButton_invokesCallback`, `remainingTime_isFormattedAsMinutesAndSeconds`, `cycleProgress_showsCompletedOverThreshold`). If a non-Wear device is also attached (e.g. a companion phone used for phone-side testing), it's fine if it runs too — only the watch device's result matters here. If `remainingTime_isFormattedAsMinutesAndSeconds` fails, check that `fontFeatureSettings = "tnum"` didn't change the rendered text content (it shouldn't — it's a rendering hint, not a text transform) — if it did, that's a real bug, not a flaky test.

- [ ] **Step 8: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt
git commit -m "feat(wear): render timer digits in Manrope, thin the progress ring, unify long-break accent"
```

---

## Task 4: Spring-based motion for the progress ring and phase-color changes

**Files:**
- Modify: `wear/build.gradle.kts`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`

**Interfaces:**
- Consumes: `progress: Float` and `ringColor: Color` as already computed in `WearTimerScreen` (Task 3's version).
- Produces: no new public symbols — this task only wraps two existing local values in animated state.

Button-press motion needs no code here: Wear Compose Material 3's `IconButton`/`FilledIconButton` already animate their shape on press via an internal `rememberAnimatedPressedButtonShape` (confirmed by reading `androidx.wear.compose.material3` `RoundButton.kt` this session) — it's part of the M3 Expressive redesign these components already ship. Task 5's manual pass confirms this is visible on-device; no extra work is needed to get it.

- [ ] **Step 1: Add the animation dependency**

In `wear/build.gradle.kts`, in the `dependencies` block, add this line near the other `androidx.compose.*` entries (right after `implementation("androidx.compose.ui:ui-tooling-preview")` is a natural spot):

```kotlin
    implementation("androidx.compose.animation:animation")
```

The module already applies `platform("androidx.compose:compose-bom:2025.08.01")`, so no version is needed on this line — the BOM pins it. This artifact is what provides `animateColorAsState`; `animateFloatAsState`/`spring()` are already transitively available via `animation-core`, but declaring the artifact you call directly (`animateColorAsState`) explicitly is the correct practice rather than relying on a transitive dependency that could disappear in a future library bump.

- [ ] **Step 2: Animate the progress value and ring color**

In `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`, add these imports:

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
```

Replace:

```kotlin
    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        WearLongBreakAccent
    )
```

with:

```kotlin
    val ringColor = progressColorFor(
        uiState.phase,
        MaterialTheme.colorScheme.primary,
        WearLongBreakAccent
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "wearTimerProgress"
    )
    val animatedRingColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "wearTimerRingColor"
    )
```

Then update the `CircularProgressIndicator` call to consume the animated values instead of the raw ones:

```kotlin
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(150.dp),
                strokeWidth = CircularProgressIndicatorDefaults.smallStrokeWidth,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = animatedRingColor)
            )
```

The progress value gets a bouncier spring (it moves continuously every second, so a touch of overshoot reads as "alive" rather than jittery); the color gets a stiffer, non-bouncy spring (a bouncy color cross-fade — e.g. overshooting into a color past the target — looks like a rendering glitch, not motion, so this one should settle cleanly).

- [ ] **Step 3: Build and confirm the module compiles**

```bash
./gradlew :wear:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the instrumented UI test again**

Compose's animated state still reports its *target* value synchronously to semantics in test environments that call `waitForIdle()` (which `performClick()` and the text assertions already do), so this should not regress Task 3's Step 7 result — but confirm on the real device:

```bash
./gradlew :wear:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pomodoro.timer.wear.ui.WearTimerScreenTest
```

Expected: 5/5 pass on the watch, same as Task 3. If `remainingTime_isFormattedAsMinutesAndSeconds` or `cycleProgress_showsCompletedOverThreshold` newly fail here, the animated progress/color introduced a timing issue — check whether the test needs `composeRule.mainClock.autoAdvance = true` (should already be the default) rather than reverting the animation.

- [ ] **Step 5: Commit**

```bash
git add wear/build.gradle.kts wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt
git commit -m "feat(wear): spring-animate the progress ring value and phase-color change"
```

---

## Task 5: Manual on-device verification

Visual and motion quality aren't meaningfully unit-testable — this task is the human check, matching the pattern already established for this project's other visual features (see `color-themes-design.md`'s Testing Strategy).

**Files:** none — this task is verification only.

- [ ] **Step 1: Install the latest debug build on the real watch**

```bash
./gradlew :wear:assembleDebug
adb -s <watch-serial> install -r wear/build/outputs/apk/debug/wear-debug.apk
adb -s <watch-serial> shell am start -n com.pomodoro.timer/androidx.activity.ComponentActivity
```

- [ ] **Step 2: Check color and typography across all 4 themes**

On the phone app (or via the watch, if a theme switcher is reachable there — otherwise change it on the phone and let it sync), cycle through Default/Ocean/Forest/Lavender. Confirm for each: the background is the new near-black (not pure black), the countdown digits render in the bolder Manrope face (visibly different from the phase-label/cycle-count text, which stays in the system font), and the accent color matches the new hex from Global Constraints.

- [ ] **Step 3: Check the progress ring and long-break accent**

Confirm the ring reads thinner with more surrounding space than before, and that triggering a long-break phase shows the same indigo (`#6B7BFF`) regardless of which of the 4 themes is active (previously this varied per theme).

- [ ] **Step 4: Check motion feel**

Watch the ring update over a few seconds of a running timer — it should read as a smooth, slightly organic movement, not a linear tick. Trigger a phase change (or force one via a short focus duration in settings) and confirm the ring color cross-fades rather than snapping. Press start/pause/reset and confirm the buttons visibly morph/react on press (this is the built-in M3 Expressive press animation from Task 4 — if it's not visible, flag this as a finding, not something to silently patch over).

- [ ] **Step 5: Check ambient mode is unaffected**

Let the watch enter ambient mode (or trigger it via `adb shell am broadcast` if the device supports simulating it) and confirm: still just the plain white countdown text, still zero motion, still no ring — this task must not have changed ambient behavior.

- [ ] **Step 6: Report findings**

If everything in Steps 2–5 checks out, this plan is done. If anything looks wrong, note exactly which step and what was observed — do not silently adjust values without going back to a numbered step above and re-running its verification.
