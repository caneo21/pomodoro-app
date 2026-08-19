# Daily Goal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user set a target number of completed pomodoros per day and get notified once, on the day they reach it, matching the reference app's daily-goal feature. This is feature 4 of the feature-gap backlog (`docs/feature-backlog.md`).

**Architecture:** A new pure `DailyGoalTracker` object in `:core` handles date-rollover and increment logic with no dependency on `TimerEngine` (dates are orthogonal to phase transitions). `TimerSettingsRepository` persists both the two settings (`dailyGoalEnabled`, `dailyGoalTarget`) and today's running count, the latter in its own DataStore keys separate from settings. `SyncPayload` carries all four values so phone and watch agree on the same running count. `TimerService`/`WearTimerService` detect "a FOCUS phase just ended" in their existing ticker loop, record a completion, and fire a one-time celebratory notification the instant the count crosses the target. `SettingsScreen` gets one switch and one slider.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 on phone, Wear Compose Material on watch), AndroidX DataStore Preferences, Google Play Services Wearable Data Layer API, `java.time.LocalDate` (available natively — minSdk 26 phone / 30 watch, no desugaring needed), JUnit4 (local unit tests only — this environment has no attached Android device/emulator, so `androidTest` instrumented suites cannot be run here; verify those layers by compiling and defer to manual on-device testing).

**Spec:** `docs/superpowers/specs/2026-08-19-daily-goal-design.md`

## Global Constraints

- Counts **completed FOCUS sessions only** — the count increments the instant a FOCUS phase ends (SHORT_BREAK or LONG_BREAK begins), never on other transitions.
- `dailyGoalEnabled` defaults to `false`, `dailyGoalTarget` defaults to `8`, clamped to `1..20`.
- The day boundary is each device's local midnight (calendar date), represented as an ISO string (`"2026-08-19"`) from `java.time.LocalDate.now().toString()` — directly comparable and DataStore/`SyncPayload`-friendly, same pattern `KEY_PHASE` already uses for an enum.
- The celebratory notification fires **once**, the exact instant the count first reaches the target, **in addition to** (not instead of) the normal session-end notification for that same transition. It never repeats later the same day.
- Applies to both phone and watch: each device counts from its own ticker and notifies independently, but the running count itself is synced via `SyncPayload` so both agree, the same way `completedPomodoros` already does.
- The watch has no settings screen (existing constraint) — it only ever learns `dailyGoalEnabled`/`dailyGoalTarget` via `SyncPayload`, and keeps its running count in memory only (no local DataStore persistence, same pattern as `autoStartBreak`/`autoStartFocus` on the watch).
- No changes to `TimerEngine` — it has no notion of dates, and the day boundary is orthogonal to phase transitions.
- Notification action buttons, on/off/vibrate/sound-theme toggles, a history/calendar view, manually editing today's count, watch-side settings UI, color themes, and onboarding are all out of scope.

---

## Task 1: Core — daily goal pure tracking logic

**Files:**
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/DailyGoalProgress.kt`
- Test: `core/src/test/kotlin/com/pomodoro/timer/core/DailyGoalTrackerTest.kt`

**Interfaces:**
- Consumes: nothing (pure `:core` module, no dependency on other tasks).
- Produces: `data class DailyGoalProgress(val date: String, val count: Int)`; `object DailyGoalTracker` with `fun forToday(stored: DailyGoalProgress?, today: String): DailyGoalProgress`, `fun recordCompletion(current: DailyGoalProgress?, today: String): DailyGoalProgress`, `fun justReachedGoal(previousCount: Int, count: Int, target: Int): Boolean`. Every later task depends on this.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/kotlin/com/pomodoro/timer/core/DailyGoalTrackerTest.kt`:

```kotlin
package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalTrackerTest {

    @Test
    fun `forToday returns stored progress unchanged when dates match`() {
        val stored = DailyGoalProgress(date = "2026-08-19", count = 3)

        val result = DailyGoalTracker.forToday(stored, today = "2026-08-19")

        assertEquals(stored, result)
    }

    @Test
    fun `forToday zeroes out on a date change`() {
        val stored = DailyGoalProgress(date = "2026-08-18", count = 5)

        val result = DailyGoalTracker.forToday(stored, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 0), result)
    }

    @Test
    fun `forToday zeroes out when stored is null`() {
        val result = DailyGoalTracker.forToday(null, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 0), result)
    }

    @Test
    fun `recordCompletion increments within a day`() {
        val current = DailyGoalProgress(date = "2026-08-19", count = 3)

        val result = DailyGoalTracker.recordCompletion(current, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 4), result)
    }

    @Test
    fun `recordCompletion restarts at 1 across a date change`() {
        val current = DailyGoalProgress(date = "2026-08-18", count = 7)

        val result = DailyGoalTracker.recordCompletion(current, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 1), result)
    }

    @Test
    fun `recordCompletion starts at 1 when current is null`() {
        val result = DailyGoalTracker.recordCompletion(null, today = "2026-08-19")

        assertEquals(DailyGoalProgress(date = "2026-08-19", count = 1), result)
    }

    @Test
    fun `justReachedGoal is true only on the exact crossing`() {
        assertTrue(DailyGoalTracker.justReachedGoal(previousCount = 7, count = 8, target = 8))
    }

    @Test
    fun `justReachedGoal is false below the target`() {
        assertFalse(DailyGoalTracker.justReachedGoal(previousCount = 6, count = 7, target = 8))
    }

    @Test
    fun `justReachedGoal is false once already past the target`() {
        assertFalse(DailyGoalTracker.justReachedGoal(previousCount = 8, count = 9, target = 8))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests "com.pomodoro.timer.core.DailyGoalTrackerTest"`
Expected: compile failure — `DailyGoalProgress`/`DailyGoalTracker` unresolved.

- [ ] **Step 3: Implement `DailyGoalProgress.kt`**

Create `core/src/main/kotlin/com/pomodoro/timer/core/DailyGoalProgress.kt`:

```kotlin
package com.pomodoro.timer.core

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

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :core:test --tests "com.pomodoro.timer.core.DailyGoalTrackerTest"`
Expected: BUILD SUCCESSFUL, all 9 tests green.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/pomodoro/timer/core/DailyGoalProgress.kt core/src/test/kotlin/com/pomodoro/timer/core/DailyGoalTrackerTest.kt
git commit -m "feat: add DailyGoalTracker date-rollover and goal-crossing logic"
```

---

## Task 2: Core wire format — defaults, sync keys, payload fields, notification copy

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`
- Test: `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `TimerDefaults.DEFAULT_DAILY_GOAL_TARGET/MIN_DAILY_GOAL_TARGET/MAX_DAILY_GOAL_TARGET`; `SyncKeys.KEY_DAILY_GOAL_ENABLED/KEY_DAILY_GOAL_TARGET/KEY_DAILY_GOAL_COUNT/KEY_DAILY_GOAL_DATE`; `SyncPayload.dailyGoalEnabled/dailyGoalTarget/dailyGoalCount/dailyGoalDate`; `NotificationContent.dailyGoalReachedTitle(target: Int): String`. Every later task depends on this.

- [ ] **Step 1: Add the defaults, keys, and payload fields (mechanical, no new test — plain data holders exercised by later tasks)**

In `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt`, add after `MAX_POMODOROS_UNTIL_LONG_BREAK`:

```kotlin
    const val DEFAULT_DAILY_GOAL_TARGET = 8
    const val MIN_DAILY_GOAL_TARGET = 1
    const val MAX_DAILY_GOAL_TARGET = 20
```

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`, add after `KEY_AUTO_START_FOCUS`:

```kotlin
    const val KEY_DAILY_GOAL_ENABLED = "dailyGoalEnabled"
    const val KEY_DAILY_GOAL_TARGET = "dailyGoalTarget"
    const val KEY_DAILY_GOAL_COUNT = "dailyGoalCount"
    const val KEY_DAILY_GOAL_DATE = "dailyGoalDate"
```

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`, add four fields to the data class, after `autoStartFocus`:

```kotlin
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET,
    val dailyGoalCount: Int = 0,
    val dailyGoalDate: String = ""
```

- [ ] **Step 2: Write the failing test for the new notification title**

Append to `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt`, inside the class:

```kotlin
    @Test
    fun `daily goal reached title includes the target count`() {
        assertEquals("오늘의 목표 8개를 달성했어요!", NotificationContent.dailyGoalReachedTitle(8))
        assertEquals("오늘의 목표 1개를 달성했어요!", NotificationContent.dailyGoalReachedTitle(1))
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:test --tests "com.pomodoro.timer.core.NotificationContentTest"`
Expected: compile failure — `dailyGoalReachedTitle` unresolved.

- [ ] **Step 4: Implement `dailyGoalReachedTitle`**

In `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`, add after `sessionEndTitle`:

```kotlin
    fun dailyGoalReachedTitle(target: Int): String = "오늘의 목표 ${target}개를 달성했어요!"
```

- [ ] **Step 5: Run the full `:core` test suite and verify everything passes**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests (Task 1's and this task's) green.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt
git commit -m "feat: add daily goal wire-format fields and notification copy"
```

---

## Task 3: Sync wire format — carry the four daily-goal fields between phone and watch

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`

**Interfaces:**
- Consumes: `SyncKeys.KEY_DAILY_GOAL_*`, `SyncPayload.dailyGoal*` (Task 2).
- Produces: both transports' `publish`/`decode` round-trip the four new fields. Tasks 5 and 6 depend on this (their `SyncPayload(...)` calls need the values to actually reach the paired device).

No local unit tests exist for these transports (they wrap the Play Services Wearable `DataClient`, which needs a real device/emulator); verification here is compile-check plus the manual on-device check called out in Task 6. Keep the change purely mechanical and symmetric with the existing fields.

- [ ] **Step 1: Add the four fields to `PlayServicesSyncTransport`'s encode and decode**

In `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`, in `publish`, add after `dataMap.putBoolean(SyncKeys.KEY_AUTO_START_FOCUS, payload.autoStartFocus)`:

```kotlin
            dataMap.putBoolean(SyncKeys.KEY_DAILY_GOAL_ENABLED, payload.dailyGoalEnabled)
            dataMap.putInt(SyncKeys.KEY_DAILY_GOAL_TARGET, payload.dailyGoalTarget)
            dataMap.putInt(SyncKeys.KEY_DAILY_GOAL_COUNT, payload.dailyGoalCount)
            dataMap.putString(SyncKeys.KEY_DAILY_GOAL_DATE, payload.dailyGoalDate)
```

In `decode`, change the tail from:

```kotlin
        autoStartBreak = map.getBoolean(SyncKeys.KEY_AUTO_START_BREAK, false),
        autoStartFocus = map.getBoolean(SyncKeys.KEY_AUTO_START_FOCUS, false)
    )
```

to:

```kotlin
        autoStartBreak = map.getBoolean(SyncKeys.KEY_AUTO_START_BREAK, false),
        autoStartFocus = map.getBoolean(SyncKeys.KEY_AUTO_START_FOCUS, false),
        dailyGoalEnabled = map.getBoolean(SyncKeys.KEY_DAILY_GOAL_ENABLED, false),
        dailyGoalTarget = map.getInt(SyncKeys.KEY_DAILY_GOAL_TARGET, TimerDefaults.DEFAULT_DAILY_GOAL_TARGET),
        dailyGoalCount = map.getInt(SyncKeys.KEY_DAILY_GOAL_COUNT, 0),
        dailyGoalDate = map.getString(SyncKeys.KEY_DAILY_GOAL_DATE, "")
    )
```

- [ ] **Step 2: Mirror the identical change in `WearPlayServicesSyncTransport`**

Apply the same two edits to `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt` — same four lines in `publish`, same four lines in `decode`.

- [ ] **Step 3: Verify both modules compile**

Run: `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt
git commit -m "feat: sync daily goal settings and progress between phone and watch"
```

---

## Task 4: Phone settings & progress persistence — `TimerSettings` / `TimerSettingsRepository`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Test: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `DailyGoalProgress`, `DailyGoalTracker` (Task 1); `TimerDefaults.DEFAULT/MIN/MAX_DAILY_GOAL_TARGET` (Task 2).
- Produces: `TimerSettings.dailyGoalEnabled: Boolean`, `TimerSettings.dailyGoalTarget: Int`. `TimerSettingsRepository.updateDailyGoalEnabled(enabled: Boolean)`, `updateDailyGoalTarget(target: Int)`, `dailyGoalProgress: Flow<DailyGoalProgress>`, `data class DailyGoalCompletionResult(val previous: DailyGoalProgress, val updated: DailyGoalProgress)`, `suspend fun recordDailyGoalCompletion(today: String): DailyGoalCompletionResult`, `suspend fun setDailyGoalProgress(date: String, count: Int)`. Tasks 5, 6, 7, and 8 depend on these.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`, inside the class (add the import `import com.pomodoro.timer.core.DailyGoalProgress` at the top alongside the existing imports):

```kotlin
    @Test
    fun `settings default to daily goal disabled with target 8`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(false, settings.dailyGoalEnabled)
        assertEquals(8, settings.dailyGoalTarget)
    }

    @Test
    fun `updateDailyGoalEnabled and updateDailyGoalTarget persist and clamp to 1 to 20 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateDailyGoalEnabled(true)
        assertEquals(true, repository.settings.first().dailyGoalEnabled)

        repository.updateDailyGoalTarget(12)
        assertEquals(12, repository.settings.first().dailyGoalTarget)

        repository.updateDailyGoalTarget(99)
        assertEquals(20, repository.settings.first().dailyGoalTarget)

        repository.updateDailyGoalTarget(0)
        assertEquals(1, repository.settings.first().dailyGoalTarget)
    }

    @Test
    fun `dailyGoalProgress defaults to an empty date and zero count`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val progress = repository.dailyGoalProgress.first()

        assertEquals("", progress.date)
        assertEquals(0, progress.count)
    }

    @Test
    fun `recordDailyGoalCompletion increments within the same day and persists`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())
        repository.setDailyGoalProgress("2026-08-19", 3)

        val result = repository.recordDailyGoalCompletion("2026-08-19")

        assertEquals(DailyGoalProgress("2026-08-19", 3), result.previous)
        assertEquals(DailyGoalProgress("2026-08-19", 4), result.updated)
        assertEquals(DailyGoalProgress("2026-08-19", 4), repository.dailyGoalProgress.first())
    }

    @Test
    fun `recordDailyGoalCompletion rolls over to today and restarts at 1 across a date change`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())
        repository.setDailyGoalProgress("2026-08-18", 7)

        val result = repository.recordDailyGoalCompletion("2026-08-19")

        assertEquals(DailyGoalProgress("2026-08-19", 0), result.previous)
        assertEquals(DailyGoalProgress("2026-08-19", 1), result.updated)
        assertEquals(DailyGoalProgress("2026-08-19", 1), repository.dailyGoalProgress.first())
    }

    @Test
    fun `setDailyGoalProgress overwrites both keys directly`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())
        repository.recordDailyGoalCompletion("2026-08-19")

        repository.setDailyGoalProgress("2026-08-20", 5)

        assertEquals(DailyGoalProgress("2026-08-20", 5), repository.dailyGoalProgress.first())
    }
```

Note the `` `recordDailyGoalCompletion rolls over...` `` case: `previous` is `DailyGoalProgress("2026-08-19", 0)`, not the raw stored `("2026-08-18", 7)` — this is what makes `DailyGoalTracker.justReachedGoal(previous.count, updated.count, target)` behave correctly across a midnight rollover in Task 5/6 (a stale high count from yesterday must never suppress or wrongly trigger today's first crossing).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: compile failure — `dailyGoalEnabled`/`dailyGoalTarget`/`dailyGoalProgress`/`recordDailyGoalCompletion`/`setDailyGoalProgress` unresolved.

- [ ] **Step 3: Add the settings fields**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`, add two fields:

```kotlin
data class TimerSettings(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val keepScreenOn: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
)
```

- [ ] **Step 4: Add the repository keys, flows, and methods**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`, add the import:

```kotlin
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
```

Add to the companion object, after `KEEP_SCREEN_ON_KEY`:

```kotlin
        val DAILY_GOAL_ENABLED_KEY = booleanPreferencesKey("daily_goal_enabled")
        val DAILY_GOAL_TARGET_KEY = intPreferencesKey("daily_goal_target")
        val DAILY_GOAL_DATE_KEY = stringPreferencesKey("daily_goal_date")
        val DAILY_GOAL_COUNT_KEY = intPreferencesKey("daily_goal_count")
```

and after `MAX_POMODOROS_UNTIL_LONG_BREAK`:

```kotlin
        const val DEFAULT_DAILY_GOAL_TARGET = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
        const val MIN_DAILY_GOAL_TARGET = TimerDefaults.MIN_DAILY_GOAL_TARGET
        const val MAX_DAILY_GOAL_TARGET = TimerDefaults.MAX_DAILY_GOAL_TARGET
```

In the `settings` flow mapping, add to the `TimerSettings(...)` construction, after `keepScreenOn`:

```kotlin
            keepScreenOn = prefs[KEEP_SCREEN_ON_KEY] ?: false,
            dailyGoalEnabled = prefs[DAILY_GOAL_ENABLED_KEY] ?: false,
            dailyGoalTarget = prefs[DAILY_GOAL_TARGET_KEY] ?: DEFAULT_DAILY_GOAL_TARGET
```

Add a second top-level class in the same file, above `TimerSettingsRepository`:

```kotlin
data class DailyGoalCompletionResult(val previous: DailyGoalProgress, val updated: DailyGoalProgress)
```

Add methods after `updateKeepScreenOn`:

```kotlin
    suspend fun updateDailyGoalEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_GOAL_ENABLED_KEY] = enabled }
    }

    suspend fun updateDailyGoalTarget(target: Int) {
        dataStore.edit { it[DAILY_GOAL_TARGET_KEY] = target.coerceIn(MIN_DAILY_GOAL_TARGET, MAX_DAILY_GOAL_TARGET) }
    }

    val dailyGoalProgress: Flow<DailyGoalProgress> = dataStore.data.map { prefs ->
        DailyGoalProgress(
            date = prefs[DAILY_GOAL_DATE_KEY] ?: "",
            count = prefs[DAILY_GOAL_COUNT_KEY] ?: 0
        )
    }

    suspend fun recordDailyGoalCompletion(today: String): DailyGoalCompletionResult {
        var previous = DailyGoalProgress(today, 0)
        var updated = previous
        dataStore.edit { prefs ->
            val stored = DailyGoalProgress(
                date = prefs[DAILY_GOAL_DATE_KEY] ?: "",
                count = prefs[DAILY_GOAL_COUNT_KEY] ?: 0
            )
            previous = DailyGoalTracker.forToday(stored, today)
            updated = DailyGoalTracker.recordCompletion(stored, today)
            prefs[DAILY_GOAL_DATE_KEY] = updated.date
            prefs[DAILY_GOAL_COUNT_KEY] = updated.count
        }
        return DailyGoalCompletionResult(previous, updated)
    }

    suspend fun setDailyGoalProgress(date: String, count: Int) {
        dataStore.edit {
            it[DAILY_GOAL_DATE_KEY] = date
            it[DAILY_GOAL_COUNT_KEY] = count
        }
    }
```

(`dailyGoalProgress` is declared as a `val` property, same shape as the existing `settings: Flow<TimerSettings>` property — place it in the class body wherever reads naturally, e.g. right before `recordDailyGoalCompletion` as shown above; Kotlin doesn't require class members to be declared in call order.)

- [ ] **Step 5: Run the tests and verify they pass, plus the full settings test suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: BUILD SUCCESSFUL, all tests green (existing and new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt
git commit -m "feat: persist daily goal settings and today's completion count"
```

---

## Task 5: `TimerService` (phone) — record completions and fire the goal-reached notification

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`

**Interfaces:**
- Consumes: `DailyGoalProgress`, `DailyGoalTracker.justReachedGoal` (Task 1); `SyncPayload.dailyGoal*` (Task 2, wired by Task 3); `TimerSettingsRepository`, `DailyGoalCompletionResult`, `recordDailyGoalCompletion`, `setDailyGoalProgress` (Task 4); `NotificationContent.dailyGoalReachedTitle` (Task 2).
- Produces: `TimerService.EXTRA_DAILY_GOAL_ENABLED`, `TimerService.EXTRA_DAILY_GOAL_TARGET` (both `String` constants). Task 8 (`TimerViewModel.Factory`) depends on these.

No local unit test exists for `TimerService` (it's an Android `Service`, instrumented-test territory only, and this environment has no attached device). Verify by compiling; a manual on-device check is called out at the end of Task 6.

- [ ] **Step 1: Add imports, intent extras, notification ID, and static fields**

In `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`, add imports:

```kotlin
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import java.time.LocalDate
```

Add to the `companion object`, after `EXTRA_AUTO_START_FOCUS`:

```kotlin
        const val EXTRA_DAILY_GOAL_ENABLED = "extra_daily_goal_enabled"
        const val EXTRA_DAILY_GOAL_TARGET = "extra_daily_goal_target"
```

Add after `SESSION_END_NOTIFICATION_ID`:

```kotlin
        private const val DAILY_GOAL_NOTIFICATION_ID = 3
```

Add after `autoStartFocus`:

```kotlin
        private var dailyGoalEnabled: Boolean = false
        private var dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
        private var dailyGoalProgress: DailyGoalProgress = DailyGoalProgress("", 0)
```

Add a new instance field alongside `syncCoordinator`:

```kotlin
    private lateinit var settingsRepository: TimerSettingsRepository
```

- [ ] **Step 2: Construct the repository in `onCreate`**

Change `onCreate()`:

```kotlin
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        settingsRepository = TimerSettingsRepository(applicationContext.dataStore)
        val transport = PlayServicesSyncTransport(applicationContext)
        syncCoordinator = SyncCoordinator(
            transport = transport,
            scope = serviceScope,
            onRemoteStateIsNewer = ::applyRemoteState
        )
        syncCoordinator.start()
    }
```

- [ ] **Step 3: Adopt daily-goal fields in `applyRemoteState`, with a write-through to DataStore**

Change `applyRemoteState`:

```kotlin
    private fun applyRemoteState(payload: SyncPayload) {
        focusMinutes = payload.focusMinutes
        breakMinutes = payload.breakMinutes
        longBreakMinutes = payload.longBreakMinutes
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak
        autoStartBreak = payload.autoStartBreak
        autoStartFocus = payload.autoStartFocus
        dailyGoalEnabled = payload.dailyGoalEnabled
        dailyGoalTarget = payload.dailyGoalTarget
        dailyGoalProgress = DailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount)
        serviceScope.launch { settingsRepository.setDailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount) }
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
        updateOngoingNotification(_state.value)
    }
```

The DataStore write-through is required: without it, a later local completion would call `recordDailyGoalCompletion` against the stale pre-sync DataStore value and silently regress the count.

- [ ] **Step 4: Include the daily-goal fields in `publishSyncState`**

Change `publishSyncState`:

```kotlin
    private fun publishSyncState(state: TimerState, actionAtMillis: Long) {
        syncCoordinator.publishLocalState(
            SyncPayload(
                phase = state.phase,
                isRunning = state.isRunning,
                remainingMillis = state.remainingMillis,
                totalMillis = state.totalMillis,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                lastActionAtMillis = actionAtMillis,
                longBreakMinutes = longBreakMinutes,
                pomodorosUntilLongBreak = pomodorosUntilLongBreak,
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = autoStartBreak,
                autoStartFocus = autoStartFocus,
                dailyGoalEnabled = dailyGoalEnabled,
                dailyGoalTarget = dailyGoalTarget,
                dailyGoalCount = dailyGoalProgress.count,
                dailyGoalDate = dailyGoalProgress.date
            )
        )
    }
```

- [ ] **Step 5: Read the new extras in `ACTION_UPDATE_DURATIONS`**

In `handleAction`'s `ACTION_UPDATE_DURATIONS` branch, add after the existing `autoStartFocus = intent.getBooleanExtra(...)` line, before the `engine.updateDurations(...)` call:

```kotlin
                dailyGoalEnabled = intent.getBooleanExtra(EXTRA_DAILY_GOAL_ENABLED, false)
                dailyGoalTarget = intent.getIntExtra(EXTRA_DAILY_GOAL_TARGET, TimerDefaults.DEFAULT_DAILY_GOAL_TARGET)
```

`engine.updateDurations(...)` itself is unchanged — `TimerEngine` has no daily-goal parameters (Global Constraints).

- [ ] **Step 6: Record a completion and check the goal on every FOCUS-ending tick**

Replace the `if (newState.phase != previousPhase)` block inside `startTicker()`:

```kotlin
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
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
                updateOngoingNotification(newState)
                if (!newState.isRunning) {
                    tickerJob = null
                    return@launch
                }
            }
        }
    }
```

Only the FOCUS-ending branch defers the publish until after the DataStore write resolves, so the outgoing payload carries the freshly-written count; every other transition publishes exactly as it does today.

- [ ] **Step 7: Add `showDailyGoalReachedNotification`**

Add after `showSessionEndNotification`:

```kotlin
    private fun showDailyGoalReachedNotification(target: Int) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.dailyGoalReachedTitle(target))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(DAILY_GOAL_NOTIFICATION_ID, notification)
    }
```

It reuses `SESSION_END_CHANNEL_ID` (already `IMPORTANCE_HIGH` with sound+vibration) but its own notification ID, so it doesn't overwrite the session-end notification — both are visible in the shade.

- [ ] **Step 8: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerService.kt
git commit -m "feat: record daily goal completions and notify on crossing in TimerService"
```

---

## Task 6: `WearTimerService` (watch) — same completion tracking, in-memory only

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`

**Interfaces:**
- Consumes: `DailyGoalProgress`, `DailyGoalTracker` (Task 1); `SyncPayload.dailyGoal*` (Task 2, wired by Task 3); `NotificationContent.dailyGoalReachedTitle` (Task 2).
- Produces: nothing consumed elsewhere — the watch has no settings screen, so it never originates an `ACTION_UPDATE_DURATIONS`-equivalent; it only receives the two settings via `applyRemoteState` and tracks progress purely in memory.

Same testing caveat as Task 5 — instrumented-only, verify by compiling.

- [ ] **Step 1: Add imports, notification ID, and in-memory state**

In `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`, add imports:

```kotlin
import com.pomodoro.timer.core.DailyGoalProgress
import com.pomodoro.timer.core.DailyGoalTracker
import com.pomodoro.timer.core.TimerPhase
import java.time.LocalDate
```

Add after `SESSION_END_NOTIFICATION_ID`:

```kotlin
        private const val DAILY_GOAL_NOTIFICATION_ID = 3
```

Add to the companion object, after `_autoStartFocus`:

```kotlin
        // Same "not exposed" reasoning as _autoStartBreak/_autoStartFocus above: nothing in
        // :wear's UI reads these back out in this iteration.
        private val _dailyGoalEnabled = MutableStateFlow(false)
        private val _dailyGoalTarget = MutableStateFlow(TimerDefaults.DEFAULT_DAILY_GOAL_TARGET)

        // Plain var, not a repository-backed value: the watch has no local settings persistence
        // (existing constraint), so this is hydrated purely from sync and resets to zero on a
        // fresh install or process restart, same cold-start gap every other watch setting has.
        private var dailyGoalProgress: DailyGoalProgress = DailyGoalProgress("", 0)
```

- [ ] **Step 2: Adopt daily-goal fields in `applyRemoteState`**

Change `applyRemoteState`:

```kotlin
    private fun applyRemoteState(payload: SyncPayload) {
        focusMinutes = payload.focusMinutes
        breakMinutes = payload.breakMinutes
        longBreakMinutes = payload.longBreakMinutes
        _pomodorosUntilLongBreak.value = payload.pomodorosUntilLongBreak
        _autoStartBreak.value = payload.autoStartBreak
        _autoStartFocus.value = payload.autoStartFocus
        _dailyGoalEnabled.value = payload.dailyGoalEnabled
        _dailyGoalTarget.value = payload.dailyGoalTarget
        dailyGoalProgress = DailyGoalProgress(payload.dailyGoalDate, payload.dailyGoalCount)
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
        updateOngoingNotification(_state.value)
    }
```

- [ ] **Step 3: Include the daily-goal fields in `onLocalStateChanged`**

Change `onLocalStateChanged`:

```kotlin
    private fun onLocalStateChanged(state: TimerState, actionAtMillis: Long) {
        syncCoordinator.publishLocalState(
            SyncPayload(
                phase = state.phase,
                isRunning = state.isRunning,
                remainingMillis = state.remainingMillis,
                totalMillis = state.totalMillis,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                lastActionAtMillis = actionAtMillis,
                longBreakMinutes = longBreakMinutes,
                pomodorosUntilLongBreak = _pomodorosUntilLongBreak.value,
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = _autoStartBreak.value,
                autoStartFocus = _autoStartFocus.value,
                dailyGoalEnabled = _dailyGoalEnabled.value,
                dailyGoalTarget = _dailyGoalTarget.value,
                dailyGoalCount = dailyGoalProgress.count,
                dailyGoalDate = dailyGoalProgress.date
            )
        )
    }
```

- [ ] **Step 4: Record a completion and check the goal on every FOCUS-ending tick**

Replace the `if (newState.phase != previousPhase)` block inside `startTicker()`:

```kotlin
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
                if (newState.phase != previousPhase) {
                    onSessionEnded(newState)
                    if (previousPhase == TimerPhase.FOCUS) {
                        val today = LocalDate.now().toString()
                        val previous = DailyGoalTracker.forToday(dailyGoalProgress, today)
                        val updated = DailyGoalTracker.recordCompletion(dailyGoalProgress, today)
                        dailyGoalProgress = updated
                        if (_dailyGoalEnabled.value &&
                            DailyGoalTracker.justReachedGoal(previous.count, updated.count, _dailyGoalTarget.value)
                        ) {
                            showDailyGoalReachedNotification(_dailyGoalTarget.value)
                        }
                    }
                    onLocalStateChanged(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
                if (!newState.isRunning) {
                    tickerJob = null
                    return@launch
                }
            }
        }
    }
```

Unlike the phone, this needs no nested `launch` — there's no DataStore write to await, so `onLocalStateChanged` runs unconditionally, immediately after the (synchronous) in-memory update.

- [ ] **Step 5: Add `showDailyGoalReachedNotification`**

Add after `onSessionEnded`:

```kotlin
    private fun showDailyGoalReachedNotification(target: Int) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.dailyGoalReachedTitle(target))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(DAILY_GOAL_NOTIFICATION_ID, notification)
    }
```

- [ ] **Step 6: Verify the module compiles**

Run: `./gradlew :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt
git commit -m "feat: record daily goal completions and notify on crossing in WearTimerService"
```

- [ ] **Step 8 (manual, on a real device pair — not part of this automated plan):** Enable the daily goal at a low target (e.g. 2) with a short focus duration on the phone (once Task 9 lands), run through enough cycles to cross it, confirm exactly one extra notification fires at the crossing and none after. Confirm the watch, left connected throughout, shows the same running count via sync and also fires its own notification at the same crossing. Confirm a fresh day (or a manually-advanced device clock) resets the visible count to 0 on both devices without any user action.

---

## Task 7: `TimerUiState` — carry the two settings to the UI layer

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`

**Interfaces:**
- Consumes: `TimerSettings.dailyGoalEnabled`/`dailyGoalTarget` (Task 4).
- Produces: `TimerUiState.dailyGoalEnabled: Boolean`, `TimerUiState.dailyGoalTarget: Int`. Tasks 8 and 9 depend on this.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`, inside the class:

```kotlin
    @Test
    fun `buildUiState carries dailyGoalEnabled and dailyGoalTarget through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, dailyGoalEnabled = true, dailyGoalTarget = 12)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.dailyGoalEnabled)
        assertEquals(12, uiState.dailyGoalTarget)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest"`
Expected: compile failure — `dailyGoalEnabled`/`dailyGoalTarget` unresolved on `TimerUiState`/`TimerSettings`.

- [ ] **Step 3: Add the fields**

In `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`:

```kotlin
data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val completedPomodoros: Int = 0,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val keepScreenOn: Boolean = false,
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET
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
    dailyGoalTarget = settings.dailyGoalTarget
)
```

- [ ] **Step 4: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt
git commit -m "feat: carry daily goal settings through TimerUiState"
```

---

## Task 8: `TimerViewModel` — expose update methods and wire the service intent extras

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerSettingsRepository.updateDailyGoalEnabled`/`updateDailyGoalTarget` (Task 4), `TimerService.EXTRA_DAILY_GOAL_ENABLED`/`EXTRA_DAILY_GOAL_TARGET` (Task 5).
- Produces: `TimerViewModel.updateDailyGoalEnabled(enabled: Boolean)`, `TimerViewModel.updateDailyGoalTarget(target: Int)`. Task 9 depends on these.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`, inside the class, after `` `updateKeepScreenOn persists through the repository` ``:

```kotlin
    @Test
    fun `updateDailyGoalEnabled and updateDailyGoalTarget persist through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateDailyGoalEnabled(true)
        viewModel.updateDailyGoalTarget(12)

        val uiState = viewModel.uiState.first { it.dailyGoalEnabled && it.dailyGoalTarget == 12 }

        assertEquals(true, uiState.dailyGoalEnabled)
        assertEquals(12, uiState.dailyGoalTarget)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: compile failure — `updateDailyGoalEnabled`/`updateDailyGoalTarget` unresolved on `TimerViewModel`.

- [ ] **Step 3: Add the methods and wire the intent extras**

In `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`, add after `updateKeepScreenOn`:

```kotlin
    fun updateDailyGoalEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateDailyGoalEnabled(enabled) }
    }

    fun updateDailyGoalTarget(target: Int) {
        viewModelScope.launch { settingsRepository.updateDailyGoalTarget(target) }
    }
```

In `Factory.create`'s `onUpdateDurations` lambda, add two more `.putExtra` calls after `EXTRA_AUTO_START_FOCUS`:

```kotlin
            val onUpdateDurations: (TimerSettings) -> Unit = { settings ->
                val intent = Intent(appContext, TimerService::class.java)
                    .setAction(TimerService.ACTION_UPDATE_DURATIONS)
                    .putExtra(TimerService.EXTRA_FOCUS_MINUTES, settings.focusMinutes)
                    .putExtra(TimerService.EXTRA_BREAK_MINUTES, settings.breakMinutes)
                    .putExtra(TimerService.EXTRA_LONG_BREAK_MINUTES, settings.longBreakMinutes)
                    .putExtra(TimerService.EXTRA_POMODOROS_UNTIL_LONG_BREAK, settings.pomodorosUntilLongBreak)
                    .putExtra(TimerService.EXTRA_AUTO_START_BREAK, settings.autoStartBreak)
                    .putExtra(TimerService.EXTRA_AUTO_START_FOCUS, settings.autoStartFocus)
                    .putExtra(TimerService.EXTRA_DAILY_GOAL_ENABLED, settings.dailyGoalEnabled)
                    .putExtra(TimerService.EXTRA_DAILY_GOAL_TARGET, settings.dailyGoalTarget)
                ContextCompat.startForegroundService(appContext, intent)
            }
```

- [ ] **Step 4: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: expose daily goal update methods from TimerViewModel"
```

---

## Task 9: `SettingsScreen` — switch and slider, wired up in `PomodoroApp`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `TimerUiState.dailyGoalEnabled`/`dailyGoalTarget` (Task 7), `TimerViewModel.updateDailyGoalEnabled`/`updateDailyGoalTarget` (Task 8).
- Produces: nothing consumed elsewhere — the final, user-facing task.

This is the one instrumented (`androidTest`) UI change in the plan. This environment has no attached emulator/device to run it, so this task's test step is written but verified only by compiling; run it manually on a device before considering the feature done end-to-end.

- [ ] **Step 1: Write the (unrun) instrumented test**

Append to `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`, inside the class:

```kotlin
    @Test
    fun dailyGoalControls_areDisplayed_andInvokeCallbacks() {
        var enabledToggled: Boolean? = null
        var targetChanged: Int? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    dailyGoalEnabled = false,
                    dailyGoalTarget = 8,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onDailyGoalEnabledChange = { enabledToggled = it },
                    onDailyGoalTargetChange = { targetChanged = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("dailyGoalEnabledLabel").assertExists()
        composeRule.onNodeWithTag("dailyGoalTargetSlider").assertExists()

        composeRule.onNodeWithTag("dailyGoalEnabledSwitch").performClick()

        assertTrue(enabledToggled == true)
    }
```

- [ ] **Step 2: Add the switch and slider to `SettingsScreen`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`, add two new parameters to the function signature, after `onKeepScreenOnChange`:

```kotlin
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    dailyGoalEnabled: Boolean = false,
    dailyGoalTarget: Int = TimerDefaults.DEFAULT_DAILY_GOAL_TARGET,
    onDailyGoalEnabledChange: (Boolean) -> Unit = {},
    onDailyGoalTargetChange: (Int) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
```

Add a local drag-state variable alongside the others, after `localPomodorosUntilLongBreak`:

```kotlin
    var localDailyGoalTarget by remember(dailyGoalTarget) { mutableFloatStateOf(dailyGoalTarget.toFloat()) }
```

Add a switch row and a slider after the `keepScreenOn` Row block and before the final `Spacer`/`Button`:

```kotlin
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "일일 목표 사용",
                modifier = Modifier.weight(1f).testTag("dailyGoalEnabledLabel")
            )
            Switch(
                checked = dailyGoalEnabled,
                onCheckedChange = onDailyGoalEnabledChange,
                modifier = Modifier.testTag("dailyGoalEnabledSwitch")
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("일일 목표: ${localDailyGoalTarget.roundToInt()}개")
        Slider(
            value = localDailyGoalTarget,
            onValueChange = { localDailyGoalTarget = it },
            onValueChangeFinished = { onDailyGoalTargetChange(localDailyGoalTarget.roundToInt()) },
            valueRange = TimerDefaults.MIN_DAILY_GOAL_TARGET.toFloat()..TimerDefaults.MAX_DAILY_GOAL_TARGET.toFloat(),
            steps = TimerDefaults.MAX_DAILY_GOAL_TARGET - TimerDefaults.MIN_DAILY_GOAL_TARGET - 1,
            modifier = Modifier.testTag("dailyGoalTargetSlider")
        )
```

Both controls always render (consistent with every other setting in this screen); the slider being usable while the switch is off is harmless.

- [ ] **Step 3: Wire it up in `PomodoroApp`**

In `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`, extend the `SettingsScreen(...)` call:

```kotlin
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
                onBack = { navController.popBackStack() }
            )
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt app/src/main/java/com/pomodoro/timer/PomodoroApp.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
git commit -m "feat: add daily goal switch and target slider to SettingsScreen"
```

- [ ] **Step 6 (manual, on a real device — not part of this automated plan):** Open Settings, confirm the switch renders off and the slider shows 8개 by default, toggle the switch and drag the slider, confirm persistence across an app restart, then re-run the phone/watch cycle check from Task 6's Step 8.

---

## Final Verification

- [ ] Run `./gradlew :core:test :app:testDebugUnitTest :wear:testDebugUnitTest` once more from a clean state and confirm everything is green.
- [ ] Run `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin` and confirm both compile.
- [ ] Update `docs/feature-backlog.md`: mark item 4 done, following the same style used for items 1, 1a, 2, and 5.
