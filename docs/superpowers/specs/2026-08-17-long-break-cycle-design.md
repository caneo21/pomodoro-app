# Long-Break Pomodoro Cycle — Design Spec

**Goal:** Extend the timer from a simple FOCUS/BREAK toggle into the classic three-phase Pomodoro cycle — FOCUS → SHORT_BREAK, repeating, with a LONG_BREAK every N completed focus sessions (N configurable) — while keeping the phone and Galaxy Watch 6 companion in sync, per the feature-gap analysis against the reference app `com.pomodrone.app`. This is the first (highest-priority) of seven planned feature additions; the other six (autostart, notification customization, daily goal, keep-awake, color themes, onboarding) are separate, later specs.

**Non-goals:** notification action buttons, autostart-into-next-phase, daily goals, color themes, onboarding — all explicitly deferred to their own specs.

## Behavior Summary (agreed with user)

- Long break triggers every `pomodorosUntilLongBreak` completed FOCUS sessions (default 4, range 2–8, user-configurable on the phone).
- The completed-FOCUS counter resets to 0 only when a LONG_BREAK finishes naturally (phase flips back to FOCUS). It is **not** reset by the RESET button — RESET only returns the current session to an idle FOCUS state at full duration, exactly as it does today.
- Both phone and watch display cycle progress (e.g. "2/4") alongside the phase label.
- Long-break duration is a separate configurable value (default 15 min, range 5–45), edited on the phone only — the watch has no settings screen (existing constraint, unchanged).

## Section A: Core Engine Model (`:core`)

**`TimerPhase`** (`core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt`): becomes a 3-value enum: `FOCUS`, `SHORT_BREAK`, `LONG_BREAK`. The existing `BREAK` value is renamed to `SHORT_BREAK` — every exhaustive `when` over `TimerPhase` (there are several: `NotificationContent`, UI mappers) will fail to compile until updated, which is the intended compiler-guided migration path.

**`TimerEngine`** (`core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`):
- New private fields: `longBreakMillis: Long`, `pomodorosUntilLongBreak: Int`, `completedPomodoros: Int` (starts at 0).
- `TimerState` gains one new field: `completedPomodoros: Int`. (The threshold `pomodorosUntilLongBreak` is a setting, not engine state, so it is not duplicated here — UI combines `TimerState.completedPomodoros` with the settings value it already observes to render "2/4".)
- `durationFor(phase)` gains a `LONG_BREAK -> longBreakMillis` branch.
- The phase-transition rule — currently inlined in `advanceAcrossPhaseBoundaries()` as an unconditional FOCUS↔BREAK flip — becomes:
  - Leaving `FOCUS`: `completedPomodoros += 1`; next phase is `LONG_BREAK` if `completedPomodoros >= pomodorosUntilLongBreak`, else `SHORT_BREAK`.
  - Leaving `SHORT_BREAK`: next phase is `FOCUS`; `completedPomodoros` unchanged.
  - Leaving `LONG_BREAK`: next phase is `FOCUS`; `completedPomodoros` reset to 0.
  - This lives in one place inside `advanceAcrossPhaseBoundaries()`, so both `tick()` and `adoptRemote()`'s boundary-catch-up loop apply it identically and stay consistent with each other (this reuses the shared-loop fix already committed for the earlier phase-boundary bug).
- `reset()`: unchanged behavior — always returns to idle `FOCUS` at full duration. `completedPomodoros` is explicitly left untouched.
- `updateDurations(focusMillis, breakMillis)` → `updateDurations(focusMillis, breakMillis, longBreakMillis, pomodorosUntilLongBreak)`. All four durations continue to be floored at `MIN_PHASE_MILLIS`; `pomodorosUntilLongBreak` is floored at 1 (a 0 would make every FOCUS completion an immediate long break, which is a valid if unusual configuration, but 0 or negative would break the comparison — floor at 1 defensively).
- `adoptRemote(payload, now)`: reads `longBreakMinutes`, `pomodorosUntilLongBreak`, and `completedPomodoros` directly off the incoming `SyncPayload` (no local recomputation — the remote device's counter is trusted, same trust model as phase/remainingMillis today). The existing boundary-catch-up loop then evolves `completedPomodoros` forward correctly across any boundaries crossed while reconciling elapsed time, using the same rule as `tick()`.

## Section B: Settings & Sync (`:core`, `:app`, `:wear`)

**`TimerDefaults`**: add `DEFAULT_LONG_BREAK_MINUTES = 15`, `MIN_LONG_BREAK_MINUTES = 5`, `MAX_LONG_BREAK_MINUTES = 45`, `DEFAULT_POMODOROS_UNTIL_LONG_BREAK = 4`, `MIN_POMODOROS_UNTIL_LONG_BREAK = 2`, `MAX_POMODOROS_UNTIL_LONG_BREAK = 8`.

**`TimerSettings` / `TimerSettingsRepository`** (`app/src/main/java/com/pomodoro/timer/settings/`): add `longBreakMinutes` and `pomodorosUntilLongBreak` to the `TimerSettings` data class; add matching DataStore keys, defaults, and `updateLongBreakMinutes()` / `updatePomodorosUntilLongBreak()` methods on the repository, following the exact pattern of the existing focus/break methods (coerced to the new min/max constants above).

**`SyncPayload`** (`core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`): add `longBreakMinutes: Int`, `pomodorosUntilLongBreak: Int`, `completedPomodoros: Int`.

**`PlayServicesSyncTransport` / `WearPlayServicesSyncTransport`**: add corresponding `SyncKeys` constants (`KEY_LONG_BREAK_MINUTES`, `KEY_POMODOROS_UNTIL_LONG_BREAK`, `KEY_COMPLETED_POMODOROS`) and thread them through `encode`/`decode` on both transports identically.

**`TimerService` / `WearTimerService`**: extend the existing static `focusMinutes`/`breakMinutes` fields with `longBreakMinutes`/`pomodorosUntilLongBreak`, include them in every `publishSyncState` call, and apply them in `applyRemoteState`/`onCreate`'s cold-start reconciliation exactly as the existing two fields are handled today.

**Settings propagation path**: `TimerViewModel`'s `onUpdateDurations: (Int, Int) -> Unit` constructor parameter becomes `onUpdateDurations: (TimerSettings) -> Unit`, passing the whole settings snapshot instead of two loose ints (cleaner now that there are four values). `TimerService.ACTION_UPDATE_DURATIONS` gains `EXTRA_LONG_BREAK_MINUTES` and `EXTRA_POMODOROS_UNTIL_LONG_BREAK` intent extras alongside the existing two, following the same pattern.

## Section C: UI & Notifications

**Phone**
- `NotificationContent.ongoingTitle` / `sessionEndTitle`: add a `LONG_BREAK` branch (e.g. "긴 휴식 중" / "긴 휴식 시간입니다!").
- `TimerUiState`: add a progress field (e.g. `cycleProgressLabel: String`, formatted as `"$completedPomodoros/$pomodorosUntilLongBreak"`) and a `LONG_BREAK` case in whatever produces the phase label today.
- `TimerScreen`: render the progress label near the phase name; give `LONG_BREAK` a visually distinct accent color from `SHORT_BREAK`.
- `SettingsScreen`: add two new controls (long-break duration, pomodoros-until-long-break) using the same slider/stepper pattern as the existing focus/break controls.

**Watch**
- `WearTimerUiState` mapper: same progress field and `LONG_BREAK` label addition as the phone.
- `WearTimerScreen`: compact progress text (e.g. small "2/4" under the phase label) sized for the round display.
- No settings UI added to `:wear` — unchanged constraint; long-break duration and cycle length are phone-edited and arrive on the watch purely via sync.

## Testing Strategy

- **`TimerEngineTest`**: FOCUS completions 1–3 enter `SHORT_BREAK`; the 4th (default threshold) enters `LONG_BREAK`; `LONG_BREAK` completion resets `completedPomodoros` to 0 and re-enters `FOCUS`; `reset()` preserves `completedPomodoros` across a call; `updateDurations` floors `pomodorosUntilLongBreak` at 1; `adoptRemote()` crossing multiple phase boundaries (including at least one long-break boundary) produces the same `completedPomodoros`/`phase`/`remainingMillis` as a reference engine driven forward by plain `tick()` calls over the same wall-clock span (extending the existing multi-boundary comparison test pattern).
- **`TimerSettingsRepositoryTest`**: round-trip persistence and min/max coercion for the two new settings.
- **`TimerUiStateTest`** / **`WearTimerUiStateTest`**: `LONG_BREAK` phase label and cycle-progress-label formatting, including edge cases (0/N at a fresh start; N/N while `LONG_BREAK` is in progress, since the counter increments and the phase flips to `LONG_BREAK` atomically in the same transition).
- **Manual real-device verification**: run a full 4-session cycle on the phone through the watch pairing set up previously, confirming (a) the long-break transition fires at the right count on whichever device is actively ticking, (b) the other device's cold-start reconciliation picks up the correct `completedPomodoros` and phase, matching the phone↔watch bidirectional sync test already performed for the FOCUS/BREAK case.

## Out of Scope

Notification action buttons, autostart-into-next-phase, notification on/off/vibrate/sound-theme toggles, daily goals, keep-awake, color themes, onboarding flow — each is its own later spec per the agreed feature breakdown.
