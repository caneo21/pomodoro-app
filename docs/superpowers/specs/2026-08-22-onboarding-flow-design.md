# Onboarding Flow — Design Spec

**Goal:** A first-run, multi-screen introduction explaining the Pomodoro technique and how to use the app, matching the reference app `com.pomodrone.app` (6 screens). This is feature 7 (the last item) of the feature-gap backlog (`docs/feature-backlog.md`); features 1, 1a, 2, 3, 4, 5, and 6 are already done.

**Non-goals:** watch-side onboarding (Wear has no settings UI and no equivalent flow today), user-editable/localizable onboarding content, analytics/tracking of onboarding completion, animated illustrations (text + simple icons only, matching the app's existing plain Material3 style).

## Behavior Summary (agreed with user)

- **6 screens**, mirroring the reference app's structure, covering: welcome, what the Pomodoro technique is, the focus/break/long-break cycle, how to use the timer controls, an overview of the settings the app offers, and a final "get started" screen.
- Shown automatically **once**, on first launch after install, then never again automatically.
- Replayable any time from `SettingsScreen` via a "앱 사용법 다시보기" entry.
- A "건너뛰기" (skip) affordance is available on every screen except the last, jumping straight to the timer.
- **Phone-only.** The watch (`:wear`) has no settings screen and no equivalent flow in the reference app; this is out of scope there, same as autostart/notifications/daily-goal/color-theme, which are all phone-only today.

## Section A: Content

New file `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingPage.kt`:

```kotlin
data class OnboardingPage(val title: String, val body: String)

val onboardingPages = listOf(
    OnboardingPage("뽀모도로 타이머에 오신 것을 환영합니다", "집중력을 높이고 번아웃을 막아주는 뽀모도로 타이머와 함께 시작해볼까요?"),
    OnboardingPage("뽀모도로 기법이란?", "25분 집중하고 5분 쉬는 것을 반복하는 시간 관리법이에요. 짧은 집중과 휴식을 번갈아가며 몰입도를 유지할 수 있어요."),
    OnboardingPage("집중 · 휴식 · 긴 휴식", "뽀모도로 4회마다 긴 휴식이 주어져요. 진행 표시 색상으로 지금이 집중인지, 휴식인지, 긴 휴식인지 한눈에 알 수 있어요."),
    OnboardingPage("타이머 사용법", "시작 · 일시정지 · 리셋 버튼으로 세션을 자유롭게 조작하세요. 진행 중인 세션은 알림으로도 확인할 수 있어요."),
    OnboardingPage("나에게 맞게 설정하기", "집중/휴식 시간, 자동 시작, 알림, 화면 꺼짐 방지, 테마, 일일 목표까지 설정 화면에서 나에게 맞게 바꿀 수 있어요."),
    OnboardingPage("시작할 준비 완료!", "지금 바로 첫 뽀모도로를 시작해보세요!")
)
```

Strings are hardcoded Korean, consistent with every existing screen (`SettingsScreen`, `TimerScreen`) — no string-resource layer exists in this codebase today and this feature shouldn't introduce one.

## Section B: UI

New file `app/src/main/java/com/pomodoro/timer/ui/screens/onboarding/OnboardingScreen.kt`:

```kotlin
@Composable
fun OnboardingScreen(onFinish: () -> Unit) { ... }
```

- Uses `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState(pageCount = { onboardingPages.size })` (available in the project's existing Compose BOM `2024.12.01`; no new dependency needed).
- Each page renders its `title`/`body` via `Text`, vertically centered.
- A dot-row page indicator below the pager (`Box` per page, filled vs. outline depending on `pagerState.currentPage`), `testTag("onboardingIndicator")`.
- Bottom row: a "건너뛰기" `TextButton` (hidden — `Spacer` in its place — on the last page) that calls `onFinish()` directly, and a primary `Button` that either advances the pager (`pagerState.animateScrollToPage`) or, on the last page, reads "시작하기" and calls `onFinish()`.
- `testTag`s: `"onboardingSkipButton"`, `"onboardingNextButton"`, `"onboardingPage_$index"` on each page's root, following the `SettingsScreen` convention of tagging interactive/verifiable elements.
- Single `onFinish` callback — the caller (see Section D) decides what finishing means (persist completion vs. just pop back), keeping this composable agnostic of first-run vs. replay.

## Section C: Persistence

**`TimerSettings`** (`app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`): add `val onboardingCompleted: Boolean = false`.

**`TimerSettingsRepository`**: add `ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")`, read with `prefs[ONBOARDING_COMPLETED_KEY] ?: false` in the `settings` flow, and:

```kotlin
suspend fun updateOnboardingCompleted(completed: Boolean) {
    dataStore.edit { it[ONBOARDING_COMPLETED_KEY] = completed }
}
```

This follows the exact shape of every other boolean setting (`keepScreenOn`, `notificationsEnabled`, etc.) — no sync to the watch, since onboarding is phone-only.

## Section D: Navigation & First-Run Detection

**Avoiding a flash on cold start:** `TimerUiState`'s `stateIn` starts from the default `TimerUiState()` before the first real `DataStore` emission arrives (this gap already exists today for `colorTheme` etc., but it matters more here because it would otherwise flip which *screen* is shown, not just a color). `TimerUiState` gains `val settingsLoaded: Boolean = false`; `buildUiState(timer, settings)` always sets it `true` (it only runs once both source flows have emitted at least once). `PomodoroApp` renders an empty `Surface` (background-only, no content) while `!uiState.settingsLoaded`, and only composes the `NavHost` once it's `true` — at that point `uiState.onboardingCompleted` is the real persisted value, so `startDestination` is decided correctly on the first (and only) `NavHost` composition. This gap is a single DataStore read, effectively instant in practice.

**`PomodoroApp.kt`** — add a route:

```kotlin
private object Routes {
    const val TIMER = "timer"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}
```

`NavHost(startDestination = if (uiState.onboardingCompleted) Routes.TIMER else Routes.ONBOARDING)`. The `ONBOARDING` composable's `onFinish`:
- If reached as the start destination (first run): calls `viewModel.completeOnboarding()` (persists the flag) then `navController.navigate(Routes.TIMER) { popUpTo(Routes.ONBOARDING) { inclusive = true } }`.
- If reached via replay from Settings: just `navController.popBackStack()`, no flag write.

Both cases are expressible as a single `onFinish` lambda built at the call site per navigation entry (the composable itself doesn't need to know which case it's in).

**`SettingsScreen`**: add a new row/button near the bottom (before "뒤로"), `Text`/`Button("앱 사용법 다시보기")` with `testTag("replayOnboardingButton")`, wired to a new `onReplayOnboarding: () -> Unit = {}` parameter that `PomodoroApp` binds to `navController.navigate(Routes.ONBOARDING)`.

**`TimerViewModel`**: add `fun completeOnboarding() = viewModelScope.launch { settingsRepository.updateOnboardingCompleted(true) }`. `buildUiState` passes `onboardingCompleted = settings.onboardingCompleted` through, same pattern as every other settings-backed field.

## Testing Strategy

- **`TimerSettingsRepositoryTest`**: default `onboardingCompleted` is `false`; `updateOnboardingCompleted` round-trips `true`/`false`.
- **`TimerUiStateTest`**: `buildUiState` carries `onboardingCompleted` through from `TimerSettings`, and `settingsLoaded` defaults to `false` on a bare `TimerUiState()` but is always `true` for anything `buildUiState` produces.
- **`TimerViewModelTest`**: `completeOnboarding()` calls through to the repository.
- **`OnboardingScreen`** (new `androidTest`, alongside `SettingsScreenTest`/`TimerScreenTest`): tapping "건너뛰기" invokes `onFinish` immediately; tapping "다음" through all pages then "시작하기" on the last page invokes `onFinish`; the skip control is absent (or a no-op spacer) on the last page. As with prior features, this environment has no attached emulator/device, so this is called out for manual/CI verification rather than run locally.
- **Manual real-device verification**: fresh install → onboarding shows automatically, all 6 pages readable, skip works, finishing lands on the timer screen and the flag persists across app restarts (onboarding does not reappear); "앱 사용법 다시보기" from Settings replays it without re-persisting or otherwise disturbing the completed flag.

## Out of Scope

Watch-side onboarding, localized/multi-language content, onboarding analytics, animated illustrations, more than 6 screens.
