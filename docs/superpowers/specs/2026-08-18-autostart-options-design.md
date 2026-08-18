# Autostart Options — Design Spec

**Goal:** Let the user independently control whether the timer auto-continues into the next phase — "자동으로 휴식 시작" (FOCUS → SHORT_BREAK/LONG_BREAK) and "자동으로 다음 뽀모도로 시작" (SHORT_BREAK/LONG_BREAK → FOCUS) — matching the reference app `com.pomodrone.app`'s per-transition toggles. This is feature 2 of the feature-gap backlog (`docs/feature-backlog.md`); feature 1 (long-break cycle) and 1a (LONG_BREAK accent color) are already done.

**Non-goals:** notification action buttons, notification on/off/vibrate/sound-theme toggles, a "waiting to start" banner or other special UI for the paused-at-boundary state, watch-side settings UI — all deferred (backlog items 3+, or their own later work).

## Behavior Summary (agreed with user)

- Two independent settings, phone-edited only (the watch has no settings screen — existing constraint): `autoStartBreak` and `autoStartFocus`, each a boolean.
- Both **default to `false`** — matching the reference app's default, not this app's current always-auto-continue behavior. Existing users will see a behavior change after the update: every phase transition pauses at the new phase's full duration until the user taps 시작, unless they turn a toggle on in Settings.
- When a governed transition happens and its toggle is off, the timer **still switches to the new phase** (label, remaining time, total time all update to the new phase) but does not keep running — it behaves exactly like an existing manual pause: `isRunning = false`, full duration showing, 시작 button visible.
- No wording or UI changes distinguish "paused because a phase just ended" from "paused because the user tapped 일시정지" — same state, same screen, same session-end notification as today.
- Settings sync to the watch like every other setting, so both devices agree on whether a transition should auto-continue.

## Rejected Approaches

- **Stop the ticker from the service layer instead of the engine**: cheaper to write, but `TimerEngine.adoptRemote()`'s boundary-catch-up loop (`advanceAcrossPhaseBoundaries`) is the only place that correctly fast-forwards through multiple phase boundaries crossed while a device was backgrounded or cold-starting (the exact scenario `46bf96a` fixed for sync reconciliation). A service-layer check on `tick()` results would miss boundaries silently skipped during that catch-up.
- **Store the toggles as local, unsynced settings**: the watch has no settings screen, so it would keep auto-continuing even after the user turns a toggle off on the phone — breaks the phone/watch consistency every other setting in this app already has.

## Section A: Core Engine Model (`:core`)

**`TimerEngine`** (`core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`):
- New private fields: `autoStartBreak: Boolean = false`, `autoStartFocus: Boolean = false`, set via constructor and updated via `updateDurations(..., autoStartBreak, autoStartFocus)` (extending the existing parameter list, same pattern as `pomodorosUntilLongBreak`).
- `advanceAcrossPhaseBoundaries(now)` changes from an unconditional flip to a guarded one:
  ```kotlin
  private fun advanceAcrossPhaseBoundaries(now: Long) {
      while (isRunning && now >= endTimestamp) {
          val departingPhase = phase
          phase = nextPhase()
          currentPhaseDurationMillis = durationFor(phase)
          if (autoStartAllowed(departingPhase)) {
              endTimestamp += currentPhaseDurationMillis
          } else {
              isRunning = false
              remainingWhenIdle = currentPhaseDurationMillis
          }
      }
  }

  private fun autoStartAllowed(departingPhase: TimerPhase): Boolean =
      if (departingPhase == TimerPhase.FOCUS) autoStartBreak else autoStartFocus
  ```
  The loop condition gaining `isRunning &&` is what makes it stop advancing the instant a governed transition pauses — even if `now` is already far past further boundaries, none of them are crossed until the user manually starts again.
- `tick()`: the line that recomputes `remainingWhenIdle` from `endTimestamp - now` after calling `advanceAcrossPhaseBoundaries` only runs `if (isRunning)` — when the loop paused mid-call, `remainingWhenIdle` was already set to the new phase's full duration inside the loop and must not be overwritten.
- `adoptRemote(payload, now)`: same guard — the existing `if (isRunning) { ...; advanceAcrossPhaseBoundaries(now); remainingWhenIdle = ... } else { remainingWhenIdle = payload.remainingMillis }` block wraps the post-loop `remainingWhenIdle` assignment in an additional `if (isRunning)` check, since the loop may have flipped it to `false` during reconciliation. `autoStartBreak`/`autoStartFocus` are read directly off the incoming `SyncPayload`, same trust model as `longBreakMinutes`.
- `nextPhase()` and `reset()` are unchanged.

## Section B: Settings & Sync (`:core`, `:app`, `:wear`)

**`TimerSettings` / `TimerSettingsRepository`** (`app/src/main/java/com/pomodoro/timer/settings/`): add `autoStartBreak: Boolean = false` and `autoStartFocus: Boolean = false` to `TimerSettings`; add matching `booleanPreferencesKey` DataStore keys, defaults, and `updateAutoStartBreak()` / `updateAutoStartFocus()` methods on the repository, following the exact pattern of the existing methods.

**`SyncPayload`** (`core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`): add `autoStartBreak: Boolean = false`, `autoStartFocus: Boolean = false`.

**`PlayServicesSyncTransport` / `WearPlayServicesSyncTransport`**: add `SyncKeys.KEY_AUTO_START_BREAK`, `SyncKeys.KEY_AUTO_START_FOCUS` and thread them through `encode`/`decode` on both transports identically, defaulting to `false` when absent (older payloads from a not-yet-updated paired device).

**`TimerService` / `WearTimerService`**: extend the existing static settings fields with `autoStartBreak`/`autoStartFocus`, include them in every `publishSyncState`/`onLocalStateChanged` call, and apply them in `applyRemoteState` exactly as the existing fields are handled today. `TimerService.ACTION_UPDATE_DURATIONS` gains `EXTRA_AUTO_START_BREAK`/`EXTRA_AUTO_START_FOCUS` intent extras.

**Ticker stop on boundary-pause**: both services' `startTicker()` loop currently assumes `tick()` never turns `isRunning` false on its own. After this change it can — when it does, the loop must stop itself instead of ticking a idle engine every second:
```kotlin
if (newState.phase != previousPhase) {
    showSessionEndNotification(newState.phase) // or onSessionEnded on wear
    publishSyncState(newState, System.currentTimeMillis()) // or onLocalStateChanged on wear
}
updateOngoingNotification(newState)
if (!newState.isRunning) {
    tickerJob = null
    return@launch
}
```
Session-end notification content is unchanged (`NotificationContent.sessionEndTitle`) — no wording distinguishes an auto-continuing transition from a paused-and-waiting one.

**Settings propagation path**: `TimerViewModel` gains `updateAutoStartBreak()`/`updateAutoStartFocus()` methods calling the repository, following the exact pattern of `updateLongBreakMinutes()`. `TimerViewModel.Factory`'s `onUpdateDurations` intent-building lambda adds the two new extras.

## Section C: UI

**Phone**
- `SettingsScreen`: two `Switch` controls below the existing sliders — "자동으로 휴식 시작" bound to `autoStartBreak`, "자동으로 다음 뽀모도로 시작" bound to `autoStartFocus` — each with an `onXChange` callback and a `testTag`, following the existing control pattern (though these are switches, not sliders).
- `TimerUiState` / `buildUiState`: add `autoStartBreak`/`autoStartFocus` fields so `SettingsScreen` can show the persisted state.
- `TimerScreen`: no changes — the paused-at-boundary state reuses the existing not-running rendering (시작 button, full duration) with no new visual treatment.

**Watch**
- No changes. `WearTimerScreen` already renders whatever `isRunning`/`phase`/`remainingMillis` the synced state carries; a boundary-pause looks identical to a manual pause, which it already handles.

## Testing Strategy

- **`TimerEngineTest`**: with `autoStartBreak = false`, ticking past the end of FOCUS lands on `SHORT_BREAK` with `isRunning = false` and `remainingMillis = totalMillis` (full break duration); with `autoStartBreak = true`, current always-continue behavior is preserved (regression coverage for the default that existing tests already assert). Same pair of cases for `autoStartFocus` governing the break→FOCUS transition. A `LONG_BREAK`-boundary case confirms `autoStartFocus` (not `autoStartBreak`) governs leaving `LONG_BREAK`. An `adoptRemote()` case reconciles a multi-boundary gap that crosses a governed-off transition partway through, confirming the engine stops exactly at that boundary rather than fast-forwarding past it. `updateDurations` mid-session doesn't affect the in-flight phase, only the next transition (same pattern as `pomodorosUntilLongBreak`).
- **`TimerSettingsRepositoryTest`**: round-trip persistence and default (`false`) for both new keys, if such a test file exists (checking during implementation).
- **`TimerUiStateTest`**: `buildUiState` carries the two new fields through from `TimerSettings`.
- All of the above are local JUnit tests in `core`/`app` `src/test`, runnable in this environment without a connected device. `TimerService`/`WearTimerService` ticker-stop behavior and the `SettingsScreen` switches are instrumented-test territory (`androidTest`) — as with the 1a color change, this environment has no attached emulator/device to run them, so that logic is kept as thin as possible around the already-unit-tested `TimerEngine` behavior, and manual verification on a real device is called out separately.
- **Manual real-device verification**: with both toggles off (the new default), confirm a full FOCUS→SHORT_BREAK→FOCUS cycle actually stops at each boundary on both phone and watch, and that turning a toggle on in phone Settings changes behavior on both devices after the next sync.

## Out of Scope

Notification action buttons, notification on/off/vibrate/sound-theme toggles, a distinct "waiting to start" visual treatment or notification wording, watch-side settings UI, daily goals, keep-awake, color themes, onboarding flow.
