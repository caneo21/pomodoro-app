# Daily Goal — Design Spec

**Goal:** Let the user set a target number of completed pomodoros per day and get notified once that target is reached, matching the reference app `com.pomodrone.app`. This is feature 4 of the feature-gap backlog (`docs/feature-backlog.md`); features 1, 1a, 2, and 5 are already done.

**Non-goals:** notification action buttons, notification on/off/vibrate/sound-theme toggles (backlog item 3), color themes (item 6), onboarding flow (item 7), a history/calendar view of past days' counts, editing today's count manually.

## Behavior Summary (agreed with user)

- Counts **completed FOCUS sessions** (not minutes). A day's count increases by 1 each time a FOCUS phase ends (SHORT_BREAK or LONG_BREAK begins).
- Two settings, phone-edited only (watch has no settings screen — existing constraint): `dailyGoalEnabled: Boolean` (default `false`) and `dailyGoalTarget: Int` (default `8`, range `1..20`).
- Applies to **both phone and watch**: each device counts today's completions from its own ticker and shows/notifies independently, but the count itself is synced between devices (see Section B) so both agree on the running total, the same way `completedPomodoros` already does.
- When the count reaches the target, a **separate celebratory notification** fires once, in addition to (not instead of) the normal session-end notification for that transition. Further completions the same day do not repeat it.
- The day boundary is each device's local midnight (calendar date). Phone and watch are assumed to be in the same timezone, matching how the rest of the app already treats wall-clock time.

## Section A: Daily Count Model (`:core`)

New pure logic in `core/src/main/kotlin/com/pomodoro/timer/core/DailyGoalProgress.kt`:

```kotlin
data class DailyGoalProgress(val date: String, val count: Int)

object DailyGoalTracker {
    /** Returns [stored] as-is if it's for [today], otherwise a fresh zero count for [today]. */
    fun forToday(stored: DailyGoalProgress?, today: String): DailyGoalProgress =
        if (stored != null && stored.date == today) stored else DailyGoalProgress(today, 0)

    /** [current] with one more completion recorded, rolling over to [today] first if needed. */
    fun recordCompletion(current: DailyGoalProgress?, today: String): DailyGoalProgress {
        val base = forToday(current, today)
        return base.copy(count = base.count + 1)
    }

    /** True the instant [count] first reaches [target] (i.e. [previousCount] was still under it). */
    fun justReachedGoal(previousCount: Int, count: Int, target: Int): Boolean =
        previousCount < target && count >= target
}
```

`date` is an ISO string (`"2026-08-19"`, from `java.time.LocalDate.now().toString()`) so it's directly comparable and trivially serializable through `SyncPayload`/DataStore/Wearable `DataMap` — all string-friendly already (`KEY_PHASE` does the same for an enum).

No changes to `TimerEngine`: it has no notion of dates, and the day boundary is orthogonal to phase transitions, so this stays a separate concept from `completedPomodoros` (which resets on long break, not at midnight).

## Section B: Settings & Sync (`:core`, `:app`, `:wear`)

**`TimerSettings` / `TimerSettingsRepository`** (`app/src/main/java/com/pomodoro/timer/settings/`):
- `TimerSettings` gains `dailyGoalEnabled: Boolean = false`, `dailyGoalTarget: Int = 8`.
- `TimerSettingsRepository` gains `DAILY_GOAL_ENABLED_KEY` (`booleanPreferencesKey`), `DAILY_GOAL_TARGET_KEY` (`intPreferencesKey`), `DEFAULT_DAILY_GOAL_TARGET = 8`, `MIN_DAILY_GOAL_TARGET = 1`, `MAX_DAILY_GOAL_TARGET = 20` (mirroring `TimerDefaults`' min/max/default constant style — add these three to `TimerDefaults` too, same as every other tunable), and `updateDailyGoalEnabled()` / `updateDailyGoalTarget()` following the exact pattern of `updateAutoStartBreak()` / `updatePomodorosUntilLongBreak()`.
- **Today's progress is persisted separately from settings**, in the same DataStore instance but its own keys: `DAILY_GOAL_DATE_KEY` (`stringPreferencesKey`, ISO date) and `DAILY_GOAL_COUNT_KEY` (`intPreferencesKey`). New repository methods:
  - `dailyGoalProgress: Flow<DailyGoalProgress>` — reads the two keys, empty string date defaults to a sentinel that never matches "today" so a fresh install starts at 0.
  - `data class DailyGoalCompletionResult(val previous: DailyGoalProgress, val updated: DailyGoalProgress)`.
  - `suspend fun recordDailyGoalCompletion(today: String): DailyGoalCompletionResult` — inside one `dataStore.edit {}` (so the read-modify-write is atomic against concurrent writers), reads the current progress, calls `DailyGoalTracker.recordCompletion`, writes both keys, and returns both the pre- and post-completion `DailyGoalProgress` so the caller (Section C) can check `justReachedGoal` without a separate, non-atomic pre-read.
  - `suspend fun setDailyGoalProgress(date: String, count: Int)` — unconditionally overwrites both keys. Used only when adopting a remote device's state (Section C), so a later local completion increments from the correct base instead of a stale local value.

**`SyncPayload`** (`core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`): add `dailyGoalEnabled: Boolean = false`, `dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET`, `dailyGoalCount: Int = 0`, `dailyGoalDate: String = ""`.

**`PlayServicesSyncTransport` / `WearPlayServicesSyncTransport`**: add matching `SyncKeys` constants and thread all four fields through `encode`/`decode` exactly like `autoStartBreak`/`autoStartFocus` (booleans/ints default `false`/`0`/`""` when absent, for forward compatibility with an older paired device).

**`TimerService` (phone)**:
- Static fields `dailyGoalEnabled`, `dailyGoalTarget` (mirroring `autoStartBreak`/`autoStartFocus`), set from `ACTION_UPDATE_DURATIONS` extras (`EXTRA_DAILY_GOAL_ENABLED`, `EXTRA_DAILY_GOAL_TARGET`) and included in every `publishSyncState` call.
- A static `dailyGoalProgress: DailyGoalProgress` mirrors the persisted value, kept in sync with DataStore at every write (see below) so it never goes stale relative to what's actually stored.
- `settingsRepository: TimerSettingsRepository` becomes an **instance field**, constructed in `onCreate()` from `applicationContext.dataStore` — same lifetime and construction pattern as the existing `syncCoordinator` instance field, not a companion-object static (the static `dailyGoalProgress` mirror is what's shared across service instances, same role `focusMinutes` etc. already play).
- `startTicker()`'s existing block becomes (full replacement, showing the surrounding structure so the nesting is unambiguous):
  ```kotlin
  if (newState.phase != previousPhase) {
      showSessionEndNotification(newState.phase)
      if (previousPhase == TimerPhase.FOCUS) {
          val today = LocalDate.now().toString()
          serviceScope.launch {
              val (previous, updated) = settingsRepository.recordDailyGoalCompletion(today)
              dailyGoalProgress = updated
              if (dailyGoalEnabled && DailyGoalTracker.justReachedGoal(previous.count, updated.count, dailyGoalTarget)) {
                  showDailyGoalReachedNotification(dailyGoalTarget)
              }
              publishSyncState(newState, System.currentTimeMillis())
          }
      } else {
          publishSyncState(newState, System.currentTimeMillis())
      }
  }
  ```
  The `else` branch is today's existing (unchanged) call — every non-FOCUS-ending transition publishes exactly as it does now. Only the FOCUS-ending branch defers the publish until after the DataStore write resolves, so the outgoing payload carries the freshly-written count.
- `applyRemoteState`: adopts `dailyGoalEnabled`, `dailyGoalTarget` into the static fields, sets `dailyGoalProgress = DailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount)`, **and** persists that same value via `serviceScope.launch { settingsRepository.setDailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount) }`. Without this write-through, a later local completion would call `recordDailyGoalCompletion` against the stale pre-sync DataStore value and silently regress the count. No notification check here, matching how `showSessionEndNotification` is already skipped on this path.
- `ACTION_UPDATE_DURATIONS` also updates `dailyGoalEnabled`/`dailyGoalTarget` (the target's own value, not progress) exactly like the other settings extras.

**`WearTimerService` (watch)**: same shape as the phone, but true to its existing "no local settings persistence" pattern:
- `_dailyGoalEnabled`, `_dailyGoalTarget` as in-memory `MutableStateFlow`s (like `_autoStartBreak`), and a plain `var dailyGoalProgress: DailyGoalProgress` — all hydrated purely from sync, never from local storage, so a freshly-installed or -restarted watch shows `0` until the next sync arrives (same cold-start gap the watch already has for every other setting).
- Same FOCUS-ending branch in its `startTicker()`, incrementing the in-memory `dailyGoalProgress` directly (no repository — nothing to persist to) and firing its own `showDailyGoalReachedNotification` when it locally crosses the threshold.
- `onLocalStateChanged` includes the four new fields in the outgoing `SyncPayload`; `applyRemoteState` adopts them the same way as the phone.

**Settings propagation path**: `TimerViewModel` gains `updateDailyGoalEnabled()` / `updateDailyGoalTarget()`; `TimerViewModel.Factory`'s `onUpdateDurations` lambda adds the two extras, following `updateAutoStartBreak()` exactly.

## Section C: Notification Content & UI

**`NotificationContent`** (`core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`): add `fun dailyGoalReachedTitle(target: Int): String = "오늘의 목표 ${target}개를 달성했어요!"`.

**`TimerService` / `WearTimerService`**: new `showDailyGoalReachedNotification(target: Int)`, built the same way as `showSessionEndNotification`/`onSessionEnded` (reuses `SESSION_END_CHANNEL_ID` for the high-importance sound+vibration profile) but with its own notification ID (`DAILY_GOAL_NOTIFICATION_ID = 3`) so it doesn't overwrite the session-end notification — both are visible in the shade.

**Phone `SettingsScreen`**: below the existing "화면 꺼짐 방지" row, add:
- `Switch` — "일일 목표 사용" (`testTag("dailyGoalEnabledSwitch")`) bound to `dailyGoalEnabled`.
- `Slider` — "일일 목표: N개" (`testTag("dailyGoalTargetSlider")`), range `1..20`, same local-drag-state pattern (`localDailyGoalTarget`) as the other sliders, committed via `onValueChangeFinished`.

Both controls always render (consistent with how every other setting in this screen is always visible); the slider being usable while the switch is off is harmless — it just won't do anything until the switch is on, same relationship as the other settings have to each other today. `TimerUiState` / `buildUiState` gain `dailyGoalEnabled`/`dailyGoalTarget` fields so `SettingsScreen` reflects persisted state.

**Watch**: no settings UI change (no settings screen exists). No changes to `WearTimerScreen`'s rendering — the daily goal has no in-timer visual element in this iteration (matching non-goals: no history/progress display beyond the notification itself).

## Testing Strategy

- **`DailyGoalTrackerTest`** (new, `core/src/test`): `forToday` returns stored progress unchanged when dates match, zeroes out on a date change or `null`; `recordCompletion` increments within a day and restarts at `1` across a date change; `justReachedGoal` is `true` only on the exact crossing (`7→8` with target `8`), `false` below it (`6→7`), and `false` once already past it (`8→9`).
- **`TimerSettingsRepositoryTest`**: round-trip persistence and defaults for `dailyGoalEnabled`/`dailyGoalTarget`; `recordDailyGoalCompletion` persists the incremented count and returns matching `previous`/`updated` values (including the cross-midnight-reset case); `setDailyGoalProgress` overwrites both keys directly.
- **`TimerUiStateTest`**: `buildUiState` carries `dailyGoalEnabled`/`dailyGoalTarget` through from `TimerSettings`.
- All of the above are local JUnit tests, runnable in this environment without a connected device. `TimerService`/`WearTimerService`'s notification-on-crossing behavior and the new `SettingsScreen` controls are instrumented-test territory (`androidTest`) — as with prior features, this environment has no attached emulator/device, so that logic is kept as thin as possible around the already-unit-tested `DailyGoalTracker`, with manual verification called out separately.
- **Manual real-device verification**: enable the goal at a low target (e.g. 2) with a short focus duration, run through enough cycles on the phone to cross it, confirm exactly one extra notification fires at the crossing and none after; confirm the watch, left connected throughout, shows the same count and also fires its own notification at the same crossing; confirm a fresh day (or a manually-advanced device clock) resets the visible count to 0 without any user action.

## Out of Scope

Notification action buttons, notification on/off/vibrate/sound-theme toggles, a history/calendar view of past days, manually editing today's count, watch-side settings UI, color themes, onboarding flow.
