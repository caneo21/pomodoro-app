# Onboarding Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A first-run, 6-screen introduction explaining the Pomodoro technique and how to use the app, shown automatically once and replayable from Settings. This is feature 7 — the last item — of the feature-gap backlog (`docs/feature-backlog.md`).

**Architecture:** A new `onboarding` sub-package under `ui/screens/` holds a static list of 6 `OnboardingPage(title, body)` entries and a stateless `OnboardingScreen(onFinish)` composable built on `HorizontalPager` (already available via the project's existing Compose BOM — no new dependency). Completion is tracked the same way every other toggle in this app is: a `Boolean` field on `TimerSettings`/`TimerSettingsRepository`, persisted via DataStore, with no watch sync (this feature is phone-only, like autostart/notifications/daily-goal/color-theme). `TimerUiState` gains that field plus a `settingsLoaded` flag so `PomodoroApp` can wait for the first real DataStore read before deciding whether to start on the onboarding screen or the timer, avoiding a one-frame flash of the wrong screen. `SettingsScreen` gets a new button to replay onboarding on demand; the replay path pops back to Settings on finish instead of writing the completion flag again.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), `androidx.compose.foundation.pager.HorizontalPager`, AndroidX DataStore Preferences, AndroidX Navigation Compose, JUnit4 (local unit tests) + Compose `androidTest` (this environment has no attached Android device/emulator, so instrumented suites cannot be run here; verify those by compiling and defer to manual on-device testing, same as every prior UI feature in this backlog).

**Spec:** `docs/superpowers/specs/2026-08-22-onboarding-flow-design.md`

## Global Constraints

- **Phone-only.** No changes to `:wear` — the watch has no settings screen and no equivalent flow in the reference app, same relationship every other phone-only setting already has.
- **6 screens**, content fixed by the spec (Section A) — do not invent different copy.
- Strings are **hardcoded Korean**, matching every existing screen (`SettingsScreen`, `TimerScreen`) — this codebase has no string-resource layer and this feature must not introduce one.
- **No new Gradle dependency** — `HorizontalPager` ships in the project's current Compose BOM (`2024.12.01`).
- **No sync to the watch** for the onboarding-completed flag — it never touches `SyncPayload`, `TimerService`'s duration-update extras, or any transport.
- Shown automatically **once** (first launch after install), never again automatically; replayable from Settings at any time without re-writing the completion flag.
- A skip affordance is available on every page except the last.
- Follow existing conventions exactly: DataStore key/update-method shape in `TimerSettingsRepository`, `testTag` naming for interactive/verifiable elements, `Modifier` trailing parameter with a default on every screen-level composable.

---

## Task 1: Persistence — `onboardingCompleted` in `TimerSettings` / `TimerSettingsRepository`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Test: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing (self-contained, mirrors every other boolean setting already in this file).
- Produces: `TimerSettings.onboardingCompleted: Boolean = false`; `TimerSettingsRepository.updateOnboardingCompleted(completed: Boolean)`. Tasks 2 and 4 depend on this.

- [ ] **Step 1: Write the failing repository tests**

Append to `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`, inside the class body (before the final closing `}`):

```kotlin
    @Test
    fun `settings default to onboarding not completed`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(false, settings.onboardingCompleted)
    }

    @Test
    fun `updateOnboardingCompleted persists`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateOnboardingCompleted(true)
        assertEquals(true, repository.settings.first().onboardingCompleted)

        repository.updateOnboardingCompleted(false)
        assertEquals(false, repository.settings.first().onboardingCompleted)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: FAIL — `TimerSettings` has no `onboardingCompleted` property and `TimerSettingsRepository` has no `updateOnboardingCompleted` method (compile error).

- [ ] **Step 3: Add the field to `TimerSettings`**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`, add after `colorTheme`:

```kotlin
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val onboardingCompleted: Boolean = false
```

- [ ] **Step 4: Add the key, read, and update method to `TimerSettingsRepository`**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`, add to the `companion object` after `COLOR_THEME_KEY`:

```kotlin
        val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
```

In the `settings` flow's `TimerSettings(...)` construction, add after `colorTheme`:

```kotlin
            colorTheme = ColorTheme.entries.firstOrNull { it.name == prefs[COLOR_THEME_KEY] } ?: ColorTheme.DEFAULT,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED_KEY] ?: false
```

Add a new method after `updateColorTheme`:

```kotlin
    suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED_KEY] = completed }
    }
```

- [ ] **Step 5: Run the tests and verify they pass, plus the full settings suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt
git commit -m "feat: persist onboarding-completed flag"
```

---

## Task 2: `TimerUiState.settingsLoaded` / `onboardingCompleted` and `TimerViewModel.completeOnboarding()`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerSettings.onboardingCompleted`, `TimerSettingsRepository.updateOnboardingCompleted` (Task 1).
- Produces: `TimerUiState.onboardingCompleted: Boolean = false`, `TimerUiState.settingsLoaded: Boolean = false` (true on every `TimerUiState` that `buildUiState` produces), `TimerViewModel.completeOnboarding()`. Task 4 depends on all three.

`settingsLoaded` exists solely so `PomodoroApp` (Task 4) can tell "the bare `TimerUiState()` default before the first DataStore emission" apart from "a real, loaded value" — without it, a returning user's `onboardingCompleted = true` would be indistinguishable, for one frame, from a brand-new user's `false`.

- [ ] **Step 1: Write the failing `TimerUiState` tests**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`, inside the class body:

```kotlin
    @Test
    fun `buildUiState carries onboardingCompleted through and marks settingsLoaded true`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, onboardingCompleted = true)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.onboardingCompleted)
        assertEquals(true, uiState.settingsLoaded)
    }

    @Test
    fun `a bare TimerUiState defaults settingsLoaded to false`() {
        assertEquals(false, TimerUiState().settingsLoaded)
    }
```

- [ ] **Step 2: Write the failing `TimerViewModel` test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`, inside the class body:

```kotlin
    @Test
    fun `completeOnboarding persists through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.completeOnboarding()

        val uiState = viewModel.uiState.first { it.onboardingCompleted }

        assertEquals(true, uiState.onboardingCompleted)
    }
```

- [ ] **Step 3: Run both test files to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest" --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: FAIL — `TimerUiState` has no `onboardingCompleted`/`settingsLoaded` properties and `TimerViewModel` has no `completeOnboarding` method (compile error).

- [ ] **Step 4: Add the fields to `TimerUiState` and thread them through `buildUiState`**

In `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`, add to the `data class TimerUiState` after `colorTheme`:

```kotlin
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val onboardingCompleted: Boolean = false,
    val settingsLoaded: Boolean = false
)
```

And in `buildUiState`, add after `colorTheme = settings.colorTheme`:

```kotlin
    colorTheme = settings.colorTheme,
    onboardingCompleted = settings.onboardingCompleted,
    settingsLoaded = true
)
```

- [ ] **Step 5: Add `completeOnboarding()` to `TimerViewModel`**

In `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`, add after `updateColorTheme`:

```kotlin
    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.updateOnboardingCompleted(true) }
    }
```

- [ ] **Step 6: Run the tests and verify they pass, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: track onboarding completion and settings-loaded state in TimerUiState"
```

---

## Task 3: Onboarding content and `OnboardingScreen`

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingPage.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreen.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreenTest.kt`

**Interfaces:**
- Consumes: nothing (a self-contained, stateless composable — it does not know about `TimerUiState` or the repository).
- Produces: `data class OnboardingPage(val title: String, val body: String)`, `val onboardingPages: List<OnboardingPage>` (size 6), `@Composable fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier)`. Task 4 depends on this.

This is an instrumented (`androidTest`) UI change. This environment has no attached emulator/device to run it, so this task's test step is written but verified only by compiling; run it manually on a device before considering onboarding done (see the Task 4 manual-verification step, which exercises this screen too).

- [ ] **Step 1: Create the page content**

Create `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingPage.kt`:

```kotlin
package com.pomodoro.timer.ui.screens.onboarding

data class OnboardingPage(val title: String, val body: String)

val onboardingPages = listOf(
    OnboardingPage(
        title = "뽀모도로 타이머에 오신 것을 환영합니다",
        body = "집중력을 높이고 번아웃을 막아주는 뽀모도로 타이머와 함께 시작해볼까요?"
    ),
    OnboardingPage(
        title = "뽀모도로 기법이란?",
        body = "25분 집중하고 5분 쉬는 것을 반복하는 시간 관리법이에요. 짧은 집중과 휴식을 번갈아가며 몰입도를 유지할 수 있어요."
    ),
    OnboardingPage(
        title = "집중 · 휴식 · 긴 휴식",
        body = "뽀모도로 4회마다 긴 휴식이 주어져요. 진행 표시 색상으로 지금이 집중인지, 휴식인지, 긴 휴식인지 한눈에 알 수 있어요."
    ),
    OnboardingPage(
        title = "타이머 사용법",
        body = "시작 · 일시정지 · 리셋 버튼으로 세션을 자유롭게 조작하세요. 진행 중인 세션은 알림으로도 확인할 수 있어요."
    ),
    OnboardingPage(
        title = "나에게 맞게 설정하기",
        body = "집중/휴식 시간, 자동 시작, 알림, 화면 꺼짐 방지, 테마, 일일 목표까지 설정 화면에서 나에게 맞게 바꿀 수 있어요."
    ),
    OnboardingPage(
        title = "시작할 준비 완료!",
        body = "지금 바로 첫 뽀모도로를 시작해보세요!"
    )
)
```

- [ ] **Step 2: Write the (unrun) instrumented test**

Create `app/src/androidTest/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreenTest.kt`:

```kotlin
package com.pomodoro.timer.ui.screens.onboarding

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skipButton_onFirstPage_invokesOnFinishImmediately() {
        var finished = false
        composeRule.setContent {
            PomodoroTheme {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        composeRule.onNodeWithTag("onboardingSkipButton").performClick()

        assertTrue(finished)
    }

    @Test
    fun nextButton_advancesThroughAllPages_thenFinishesOnLastPage() {
        var finished = false
        composeRule.setContent {
            PomodoroTheme {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        composeRule.onNodeWithTag("onboardingPage_0").assertExists()

        repeat(onboardingPages.lastIndex) {
            composeRule.onNodeWithTag("onboardingNextButton").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("onboardingPage_${onboardingPages.lastIndex}").assertExists()
        composeRule.onNodeWithTag("onboardingSkipButton").assertDoesNotExist()

        composeRule.onNodeWithTag("onboardingNextButton").performClick()

        assertTrue(finished)
    }
}
```

- [ ] **Step 3: Implement `OnboardingScreen`**

Create `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreen.kt`:

```kotlin
package com.pomodoro.timer.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            val page = onboardingPages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("onboardingPage_$index"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = page.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(16.dp))
                Text(text = page.body, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("onboardingIndicator"),
            horizontalArrangement = Arrangement.Center
        ) {
            for (index in onboardingPages.indices) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isLastPage) {
                Spacer(Modifier)
            } else {
                TextButton(onClick = onFinish, modifier = Modifier.testTag("onboardingSkipButton")) {
                    Text("건너뛰기")
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.testTag("onboardingNextButton")
            ) {
                Text(if (isLastPage) "시작하기" else "다음")
            }
        }
    }
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingPage.kt app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreen.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreenTest.kt
git commit -m "feat: add 6-page OnboardingScreen"
```

---

## Task 4: Navigation wiring and Settings replay entry

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `TimerUiState.onboardingCompleted`/`settingsLoaded` (Task 2), `TimerViewModel.completeOnboarding()` (Task 2), `OnboardingScreen` (Task 3).
- Produces: nothing consumed elsewhere — the final task; this is what makes the feature reachable in the running app.

This task's `SettingsScreenTest` addition is another instrumented test not runnable in this environment (same caveat as Task 3) — written and verified by compiling, then exercised in the manual pass at the end.

- [ ] **Step 1: Write the (unrun) instrumented test for the replay button**

Append to `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`, inside the class body:

```kotlin
    @Test
    fun replayOnboardingButton_invokesCallback() {
        var replayClicked = false
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onReplayOnboarding = { replayClicked = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("replayOnboardingButton").performScrollTo().performClick()
        assertTrue(replayClicked)
    }
```

- [ ] **Step 2: Add the replay button to `SettingsScreen`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`, add a parameter after `onColorThemeChange`:

```kotlin
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    onColorThemeChange: (ColorTheme) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
```

Change the trailing back-button block from:

```kotlin
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
```

to:

```kotlin
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReplayOnboarding, modifier = Modifier.testTag("replayOnboardingButton")) {
            Text("앱 사용법 다시보기")
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
```

- [ ] **Step 3: Wire the `ONBOARDING` route into `PomodoroApp`**

Replace the full contents of `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt` with:

```kotlin
package com.pomodoro.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.screens.SettingsScreen
import com.pomodoro.timer.ui.screens.TimerScreen
import com.pomodoro.timer.ui.screens.onboarding.OnboardingScreen

private object Routes {
    const val TIMER = "timer"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}

@Composable
fun PomodoroApp(viewModel: TimerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // uiState starts as the TimerUiState() default before the first DataStore emission
    // arrives. Rendering nothing here (the caller's Surface already paints the themed
    // background) avoids a one-frame flash of the wrong start destination while the real
    // onboardingCompleted value is still unknown.
    if (!uiState.settingsLoaded) return

    // Captured once, when settingsLoaded first becomes true, so it reflects the real
    // persisted value and then stays fixed for the rest of this composable's lifetime --
    // it must not flip mid-session just because completeOnboarding() changes uiState.
    val startedWithoutOnboarding = remember { !uiState.onboardingCompleted }

    val navController = rememberNavController()
    val startDestination = if (startedWithoutOnboarding) Routes.ONBOARDING else Routes.TIMER

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.TIMER) {
            TimerScreen(
                uiState = uiState,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onReset = viewModel::reset,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                focusMinutes = uiState.focusMinutes,
                breakMinutes = uiState.breakMinutes,
                longBreakMinutes = uiState.longBreakMinutes,
                pomodorosUntilLongBreak = uiState.pomodorosUntilLongBreak,
                autoStartBreak = uiState.autoStartBreak,
                autoStartFocus = uiState.autoStartFocus,
                onFocusMinutesChange = viewModel::updateFocusMinutes,
                onBreakMinutesChange = viewModel::updateBreakMinutes,
                onLongBreakMinutesChange = viewModel::updateLongBreakMinutes,
                onPomodorosUntilLongBreakChange = viewModel::updatePomodorosUntilLongBreak,
                onAutoStartBreakChange = viewModel::updateAutoStartBreak,
                onAutoStartFocusChange = viewModel::updateAutoStartFocus,
                keepScreenOn = uiState.keepScreenOn,
                onKeepScreenOnChange = viewModel::updateKeepScreenOn,
                dailyGoalEnabled = uiState.dailyGoalEnabled,
                dailyGoalTarget = uiState.dailyGoalTarget,
                onDailyGoalEnabledChange = viewModel::updateDailyGoalEnabled,
                onDailyGoalTargetChange = viewModel::updateDailyGoalTarget,
                notificationsEnabled = uiState.notificationsEnabled,
                vibrationEnabled = uiState.vibrationEnabled,
                useAlarmSound = uiState.useAlarmSound,
                onNotificationsEnabledChange = viewModel::updateNotificationsEnabled,
                onVibrationEnabledChange = viewModel::updateVibrationEnabled,
                onUseAlarmSoundChange = viewModel::updateUseAlarmSound,
                colorTheme = uiState.colorTheme,
                onColorThemeChange = viewModel::updateColorTheme,
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    if (startedWithoutOnboarding) {
                        viewModel.completeOnboarding()
                        navController.navigate(Routes.TIMER) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full app unit suite once more**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/PomodoroApp.kt app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
git commit -m "feat: wire onboarding into app navigation with a Settings replay entry"
```

- [ ] **Step 7 (manual, on a real device — not part of this automated plan):** Fresh install (or clear app data) → onboarding shows automatically starting on page 1 of 6; swipe and "다음" both advance pages; "건너뛰기" jumps straight to the timer from any page but the last; finishing the last page ("시작하기") lands on the timer screen. Restart the app — onboarding does not reappear. Open Settings → "앱 사용법 다시보기" replays all 6 pages; finishing (or skipping) it returns to Settings, not the timer, and the completed flag is undisturbed (confirm by restarting the app again and seeing the timer, not onboarding).

---

## Final Verification

- [ ] Run `./gradlew :app:testDebugUnitTest` once more from a clean state and confirm everything is green.
- [ ] Run `./gradlew :app:compileDebugKotlin` and confirm it compiles.
- [ ] Update `docs/feature-backlog.md`: mark item 7 done, following the same style used for items 1, 1a, 2, 3, 4, 5, and 6.
