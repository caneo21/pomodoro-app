# Autostart Options Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user independently toggle whether the timer auto-continues FOCUS→break and break→FOCUS, defaulting both to off, across phone and watch.

**Architecture:** `TimerEngine` gains two boolean settings (`autoStartBreak`, `autoStartFocus`) that gate its existing phase-boundary-crossing loop — when a governed transition is off, the engine still flips to the new phase but stops running, exactly like an existing manual pause. The settings ride through `SyncPayload` like every other setting, so phone and watch agree. `TimerService`/`WearTimerService` learn to stop their ticker when a tick pauses itself. `SettingsScreen` gets two switches.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 on phone, Wear Compose Material on watch), AndroidX DataStore Preferences, Google Play Services Wearable Data Layer API, JUnit4 (local unit tests only — this environment has no attached Android device/emulator, so `androidTest` instrumented suites cannot be run here; verify those layers by compiling and defer to manual on-device testing).

**Spec:** `docs/superpowers/specs/2026-08-18-autostart-options-design.md`

## Global Constraints

- Both new settings default to `false` (agreed with user — matches the reference app, changes existing users' behavior on update).
- `autoStartBreak` governs leaving `FOCUS`; `autoStartFocus` governs leaving `SHORT_BREAK` **and** `LONG_BREAK`.
- No new UI/notification wording distinguishes "paused because a phase just ended" from "paused because the user tapped 일시정지" — reuse the existing not-running rendering everywhere.
- The watch has no settings screen; it only ever learns the two flags via `SyncPayload`.
- Settings changes apply to future phase transitions only, never retroactively to a phase already in progress (same rule the existing settings already follow).

---

## Task 1: Core engine — gate phase-boundary crossing on autostart settings

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`
- Test: `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`

**Interfaces:**
- Consumes: nothing new (pure `:core` module, no dependency on other tasks).
- Produces: `TimerEngine` constructor gains trailing params `autoStartBreak: Boolean = false, autoStartFocus: Boolean = false`. `TimerEngine.updateDurations(focusMillis, shortBreakMillis, longBreakMillis = ..., pomodorosUntilLongBreak = ..., autoStartBreak: Boolean = this.autoStartBreak, autoStartFocus: Boolean = this.autoStartFocus)`. `SyncPayload` gains `autoStartBreak: Boolean = false, autoStartFocus: Boolean = false`. `SyncKeys.KEY_AUTO_START_BREAK`, `SyncKeys.KEY_AUTO_START_FOCUS` (both `String` constants). Every later task that touches `SyncPayload`, `TimerEngine`, or `SyncKeys` depends on this task.

- [ ] **Step 1: Add the wire-format fields (no test needed — plain data holders exercised by this task's own engine tests)**

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`, add two constants:

```kotlin
    const val KEY_AUTO_START_BREAK = "autoStartBreak"
    const val KEY_AUTO_START_FOCUS = "autoStartFocus"
```

In `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`, add two fields to the data class, after `completedPomodoros`:

```kotlin
    val completedPomodoros: Int = 0,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false
```

- [ ] **Step 2: Write the failing tests for the new pause-at-boundary behavior**

Append to `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt` (inside the `TimerEngineTest` class, e.g. after the existing `` `adoptRemote coerces a zero long-break duration up to the shared minimum` `` test):

```kotlin
    @Test
    fun `tick past end of focus phase pauses at SHORT_BREAK when autoStartBreak is false`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = false)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertFalse(state.isRunning)
        assertEquals(breakMillis, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `tick past end of break phase pauses at FOCUS when autoStartFocus is false`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = false)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // FOCUS -> SHORT_BREAK, keeps running (autoStartBreak = true)

        val state = engine.tick(now = focusMillis + breakMillis + 2_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
        assertEquals(focusMillis, state.totalMillis)
    }

    @Test
    fun `autoStartFocus, not autoStartBreak, governs leaving LONG_BREAK`() {
        val engine = TimerEngine(
            focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 1,
            autoStartBreak = true, autoStartFocus = false
        )
        engine.start(now = 0L)
        val enteredLongBreak = engine.tick(now = focusMillis) // FOCUS -> LONG_BREAK, keeps running
        assertTrue(enteredLongBreak.isRunning)

        val state = engine.tick(now = focusMillis + 900_000L + 1_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
    }

    @Test
    fun `adoptRemote stops exactly at a boundary governed off, without fast-forwarding further`() {
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            autoStartBreak = false,
            autoStartFocus = true
        )
        // Enough elapsed time to have crossed FOCUS -> SHORT_BREAK -> FOCUS if fully auto-continuing;
        // autoStartBreak = false must stop it the instant it enters SHORT_BREAK.
        val now = 1_000_000L + 5_000L + breakMillis + 10_000L

        val state = TimerEngine(focusMillis, breakMillis).adoptRemote(payload, now)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertFalse(state.isRunning)
        assertEquals(breakMillis, state.remainingMillis)
    }

    @Test
    fun `updateDurations changes which future transitions auto-continue`() {
        val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = false)
        engine.updateDurations(focusMillis, breakMillis, autoStartBreak = true)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.SHORT_BREAK, state.phase)
        assertTrue(state.isRunning)
    }
```

Also **update these 7 existing tests**, which currently rely on the engine always auto-continuing across boundaries — now that the default is `false`, they must opt in explicitly to keep testing what they were testing:

1. `` `tick past end of focus phase transitions to break without drift` `` → rename to `` `tick past end of focus phase transitions to break without drift when autostart is enabled` `` and change the engine construction to:
   ```kotlin
   val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true)
   ```

2. `` `tick past end of break phase transitions back to focus` `` → rename to `` `tick past end of break phase transitions back to focus when autostart is enabled` `` and change to:
   ```kotlin
   val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true)
   ```

3. `` `adoptRemote crosses a phase boundary when the payload is older than its remaining time` `` → rename to `` `adoptRemote crosses a phase boundary when the payload is older than its remaining time and autostart is enabled` `` and add `autoStartBreak = true` to the `SyncPayload(...)` construction (after `lastActionAtMillis = 1_000_000L`).

4. `` `adoptRemote across multiple phase boundaries matches a tick-driven engine` `` → rename to `` `adoptRemote across multiple phase boundaries matches a tick-driven engine when autostart is enabled` `` and:
   - add `autoStartBreak = true, autoStartFocus = true` to the `SyncPayload(...)` construction
   - change `val reference = TimerEngine(focusMillis, breakMillis)` to `val reference = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true)`

5. `` `completing the Nth focus session enters LONG_BREAK and resets the count when it ends` `` → keep the name, change the engine construction to:
   ```kotlin
   val engine = TimerEngine(
       focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 2,
       autoStartBreak = true, autoStartFocus = true
   )
   ```

6. `` `the default threshold of 4 enters LONG_BREAK on the 4th focus completion, not the 3rd` `` → keep the name, change the engine construction to:
   ```kotlin
   val engine = TimerEngine(focusMillis, breakMillis, autoStartBreak = true, autoStartFocus = true)
   ```

7. `` `adoptRemote crossing into and out of a long break matches a tick-driven engine` `` → keep the name, and:
   - add `autoStartBreak = true, autoStartFocus = true` to the `SyncPayload(...)` construction (after `completedPomodoros = 0`)
   - change `val reference = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold)` to `val reference = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold, autoStartBreak = true, autoStartFocus = true)`

- [ ] **Step 3: Run the tests to verify the new ones fail and the 7 renamed ones still compile/fail correctly**

Run: `./gradlew :core:test --tests "com.pomodoro.timer.core.TimerEngineTest"`
Expected: compiles (the new tests reference only existing `TimerEngine`/`SyncPayload` symbols with new named params that don't exist yet, so this should actually be a **compile failure** — `autoStartBreak`/`autoStartFocus` are unresolved). Confirm the failures are "no parameter named autoStartBreak/autoStartFocus", not something else.

- [ ] **Step 4: Implement `TimerEngine`'s autostart behavior**

In `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`, change the constructor:

```kotlin
class TimerEngine(
    private var focusMillis: Long,
    private var shortBreakMillis: Long,
    private var longBreakMillis: Long = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L,
    private var pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    private var autoStartBreak: Boolean = false,
    private var autoStartFocus: Boolean = false
) {
```

Replace `advanceAcrossPhaseBoundaries`:

```kotlin
    /**
     * Flips [phase] forward (via [nextPhase]) until [endTimestamp] is in the future, unless a
     * governed-off transition is hit first — then it stops running at that phase's full duration,
     * exactly like a manual pause, even if [now] is far enough ahead to have crossed further
     * boundaries. Durations are floored at [MIN_PHASE_MILLIS] before they ever reach here, so a
     * fully auto-continuing run always terminates.
     */
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

Change `tick()`:

```kotlin
    fun tick(now: Long): TimerState {
        if (!isRunning) return currentState(now)

        advanceAcrossPhaseBoundaries(now)
        if (isRunning) {
            remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        }
        return currentState(now)
    }
```

Change `updateDurations`:

```kotlin
    fun updateDurations(
        focusMillis: Long,
        shortBreakMillis: Long,
        longBreakMillis: Long = this.longBreakMillis,
        pomodorosUntilLongBreak: Int = this.pomodorosUntilLongBreak,
        autoStartBreak: Boolean = this.autoStartBreak,
        autoStartFocus: Boolean = this.autoStartFocus
    ) {
        this.focusMillis = focusMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.shortBreakMillis = shortBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.longBreakMillis = longBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.pomodorosUntilLongBreak = pomodorosUntilLongBreak.coerceAtLeast(1)
        this.autoStartBreak = autoStartBreak
        this.autoStartFocus = autoStartFocus
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }
```

Change `adoptRemote` — add the two field assignments alongside the existing ones, and guard the post-loop `remainingWhenIdle` assignment:

```kotlin
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = (payload.focusMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        shortBreakMillis = (payload.breakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        longBreakMillis = (payload.longBreakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak.coerceAtLeast(1)
        autoStartBreak = payload.autoStartBreak
        autoStartFocus = payload.autoStartFocus
        phase = payload.phase
        completedPomodoros = payload.completedPomodoros
        currentPhaseDurationMillis = payload.totalMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        isRunning = payload.isRunning

        if (isRunning) {
            val elapsedSincePublish = (now - payload.lastActionAtMillis).coerceAtLeast(0)
            endTimestamp = now + (payload.remainingMillis - elapsedSincePublish)
            advanceAcrossPhaseBoundaries(now)
            if (isRunning) {
                remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
            }
        } else {
            remainingWhenIdle = payload.remainingMillis
        }
        return currentState(now)
    }
```

- [ ] **Step 5: Run the full `:core` test suite and verify everything passes**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests (old and new) green.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt
git commit -m "feat: gate phase-boundary crossing on autoStartBreak/autoStartFocus"
```

---

## Task 2: Sync wire format — carry the two flags between phone and watch

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`

**Interfaces:**
- Consumes: `SyncKeys.KEY_AUTO_START_BREAK`, `SyncKeys.KEY_AUTO_START_FOCUS`, `SyncPayload.autoStartBreak`/`autoStartFocus` (Task 1).
- Produces: both transports' `publish`/`decode` round-trip the two new fields. Tasks 4 and 5 depend on this (their `SyncPayload(...)` calls need the values to actually reach the paired device).

No local unit tests exist for these transports (they wrap the Play Services Wearable `DataClient`, which needs a real device/emulator); this environment has neither, so verification here is compile-check plus a manual on-device sync check called out in Task 5's follow-up. Keep the change purely mechanical and symmetric with the existing fields to minimize risk.

- [ ] **Step 1: Add the two fields to `PlayServicesSyncTransport`'s encode and decode**

In `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`, in `publish`, add after `dataMap.putInt(SyncKeys.KEY_COMPLETED_POMODOROS, payload.completedPomodoros)`:

```kotlin
            dataMap.putBoolean(SyncKeys.KEY_AUTO_START_BREAK, payload.autoStartBreak)
            dataMap.putBoolean(SyncKeys.KEY_AUTO_START_FOCUS, payload.autoStartFocus)
```

In `decode`, add after `completedPomodoros = map.getInt(SyncKeys.KEY_COMPLETED_POMODOROS)`:

```kotlin
        completedPomodoros = map.getInt(SyncKeys.KEY_COMPLETED_POMODOROS),
        autoStartBreak = map.getBoolean(SyncKeys.KEY_AUTO_START_BREAK),
        autoStartFocus = map.getBoolean(SyncKeys.KEY_AUTO_START_FOCUS)
```
(remove the trailing comma that was on the `completedPomodoros` line before, since it's now mid-list — check the closing `)` still lines up with `SyncPayload(`).

- [ ] **Step 2: Mirror the same change in `WearPlayServicesSyncTransport`**

Apply the identical edit to `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt` — same two lines in `publish`, same two lines in `decode`.

- [ ] **Step 3: Verify both modules compile**

Run: `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt
git commit -m "feat: sync autoStartBreak/autoStartFocus between phone and watch"
```

---

## Task 3: Phone settings persistence — `TimerSettings` / `TimerSettingsRepository`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Test: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks (DataStore-backed, independent of `:core`/`:wear`).
- Produces: `TimerSettings.autoStartBreak: Boolean`, `TimerSettings.autoStartFocus: Boolean` (both default `false`). `TimerSettingsRepository.updateAutoStartBreak(enabled: Boolean)`, `updateAutoStartFocus(enabled: Boolean)`. Tasks 6 and 7 depend on this.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`:

```kotlin
    @Test
    fun `settings default to autostart disabled`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(false, settings.autoStartBreak)
        assertEquals(false, settings.autoStartFocus)
    }

    @Test
    fun `updateAutoStartBreak and updateAutoStartFocus persist independently`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateAutoStartBreak(true)
        assertEquals(true, repository.settings.first().autoStartBreak)
        assertEquals(false, repository.settings.first().autoStartFocus)

        repository.updateAutoStartFocus(true)
        assertEquals(true, repository.settings.first().autoStartBreak)
        assertEquals(true, repository.settings.first().autoStartFocus)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: compile failure — `autoStartBreak`/`autoStartFocus`/`updateAutoStartBreak`/`updateAutoStartFocus` unresolved.

- [ ] **Step 3: Add the fields and methods**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`, add two fields:

```kotlin
data class TimerSettings(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false
)
```

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`, add two keys to the companion object:

```kotlin
        val AUTO_START_BREAK_KEY = booleanPreferencesKey("auto_start_break")
        val AUTO_START_FOCUS_KEY = booleanPreferencesKey("auto_start_focus")
```

Add the import: `import androidx.datastore.preferences.core.booleanPreferencesKey`

In the `settings` flow mapping, add:

```kotlin
    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES,
            longBreakMinutes = prefs[LONG_BREAK_MINUTES_KEY] ?: DEFAULT_LONG_BREAK_MINUTES,
            pomodorosUntilLongBreak = prefs[POMODOROS_UNTIL_LONG_BREAK_KEY] ?: DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
            autoStartBreak = prefs[AUTO_START_BREAK_KEY] ?: false,
            autoStartFocus = prefs[AUTO_START_FOCUS_KEY] ?: false
        )
    }
```

Add two methods after `updatePomodorosUntilLongBreak`:

```kotlin
    suspend fun updateAutoStartBreak(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_BREAK_KEY] = enabled }
    }

    suspend fun updateAutoStartFocus(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_FOCUS_KEY] = enabled }
    }
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt
git commit -m "feat: persist autoStartBreak/autoStartFocus settings"
```

---

## Task 4: `TimerService` (phone) — thread the settings through and stop the ticker at a boundary pause

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`

**Interfaces:**
- Consumes: `TimerEngine(..., autoStartBreak, autoStartFocus)` and `TimerEngine.updateDurations(..., autoStartBreak, autoStartFocus)` (Task 1); `SyncPayload.autoStartBreak`/`autoStartFocus` (Task 1, wired by Task 2).
- Produces: `TimerService.EXTRA_AUTO_START_BREAK`, `TimerService.EXTRA_AUTO_START_FOCUS` (both `String` constants). Task 7 (`TimerViewModel.Factory`) depends on these.

No local unit test exists for `TimerService` (it's an Android `Service`, instrumented-test territory only, and this environment has no attached device). Verify by compiling; a manual on-device check is called out at the end of Task 5.

- [ ] **Step 1: Add the new intent extras and static settings fields**

In `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`, add to the `companion object`:

```kotlin
        const val EXTRA_AUTO_START_BREAK = "extra_auto_start_break"
        const val EXTRA_AUTO_START_FOCUS = "extra_auto_start_focus"
```

Add two static fields alongside the existing ones:

```kotlin
        private var autoStartBreak: Boolean = false
        private var autoStartFocus: Boolean = false
```

- [ ] **Step 2: Apply the settings in `applyRemoteState` and include them in `publishSyncState`**

In `applyRemoteState`, add after `pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak`:

```kotlin
        autoStartBreak = payload.autoStartBreak
        autoStartFocus = payload.autoStartFocus
```

In `publishSyncState`, add to the `SyncPayload(...)` construction, after `completedPomodoros = state.completedPomodoros`:

```kotlin
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = autoStartBreak,
                autoStartFocus = autoStartFocus
```

- [ ] **Step 3: Read the new extras in `ACTION_UPDATE_DURATIONS` and pass them to the engine**

In `handleAction`'s `ACTION_UPDATE_DURATIONS` branch, add after the existing `pomodorosUntilLongBreak = intent.getIntExtra(...)` line:

```kotlin
                autoStartBreak = intent.getBooleanExtra(EXTRA_AUTO_START_BREAK, false)
                autoStartFocus = intent.getBooleanExtra(EXTRA_AUTO_START_FOCUS, false)
```

(as two new statements assigning the static `var`s declared in Step 1 — not `val` extras, since the existing four lines above them follow the same "read into the static var" pattern.)

Then update the `engine.updateDurations(...)` call in that same branch to pass them through:

```kotlin
                engine.updateDurations(
                    focusMinutes * 60_000L,
                    breakMinutes * 60_000L,
                    longBreakMinutes * 60_000L,
                    pomodorosUntilLongBreak,
                    autoStartBreak,
                    autoStartFocus
                )
```

- [ ] **Step 4: Stop the ticker when a tick pauses itself at a phase boundary**

Change the loop body inside `startTicker()`:

```kotlin
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
                if (newState.phase != previousPhase) {
                    showSessionEndNotification(newState.phase)
                    publishSyncState(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
                if (!newState.isRunning) {
                    tickerJob = null
                    return@launch
                }
            }
        }
```

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerService.kt
git commit -m "feat: apply autostart settings in TimerService and stop the ticker at a boundary pause"
```

---

## Task 5: `WearTimerService` (watch) — same ticker-stop behavior

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`

**Interfaces:**
- Consumes: `TimerEngine` (Task 1), `SyncPayload.autoStartBreak`/`autoStartFocus` (Task 1, wired by Task 2).
- Produces: nothing new consumed elsewhere — the watch has no settings screen, so it never originates an `ACTION_UPDATE_DURATIONS`-equivalent; it only receives the two flags via `applyRemoteState`.

Same testing caveat as Task 4 — instrumented-only, verify by compiling.

- [ ] **Step 1: Apply the settings in `applyRemoteState` and include them when publishing local state**

In `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`, `applyRemoteState` calls `engine.adoptRemote(payload, ...)`, which already reads `autoStartBreak`/`autoStartFocus` straight off the payload (Task 1) — the engine itself needs no extra assignment there.

But the watch has no local settings of its own to echo back when *it* is the one that publishes a state change (e.g. after a local start/pause/reset action): it only ever forwards what it last learned from the phone. Since `TimerEngine` doesn't expose its private `autoStartBreak`/`autoStartFocus` fields as getters (by design — `TimerState` intentionally carries only phase/timing/count, not settings), `onLocalStateChanged` cannot read them back off the engine. Handle this the same way the watch already handles `pomodorosUntilLongBreak`: track it in its own `StateFlow` populated from `applyRemoteState`, then use `.value` when constructing outgoing payloads. Add a companion `StateFlow` pair matching the existing `_pomodorosUntilLongBreak` pattern:

```kotlin
        private val _autoStartBreak = MutableStateFlow(false)
        private val _autoStartFocus = MutableStateFlow(false)
```

(placed next to `_pomodorosUntilLongBreak` in the companion object; no public `val ...: StateFlow<Boolean>` exposure is needed since, unlike `pomodorosUntilLongBreak`, nothing in `:wear`'s UI currently reads these back out — they exist purely to echo the value when the watch itself publishes a state change, e.g. after a local start/pause/reset action.)

In `applyRemoteState`, add:

```kotlin
        _autoStartBreak.value = payload.autoStartBreak
        _autoStartFocus.value = payload.autoStartFocus
```

(alongside the existing `_pomodorosUntilLongBreak.value = payload.pomodorosUntilLongBreak` line.)

In `onLocalStateChanged`, add to the `SyncPayload(...)` construction, after `completedPomodoros = state.completedPomodoros`:

```kotlin
                completedPomodoros = state.completedPomodoros,
                autoStartBreak = _autoStartBreak.value,
                autoStartFocus = _autoStartFocus.value
```

- [ ] **Step 2: Stop the ticker when a tick pauses itself at a phase boundary**

Change the loop body inside `startTicker()`:

```kotlin
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                val previousPhase = _state.value.phase
                val newState = engine.tick(System.currentTimeMillis())
                _state.value = newState
                if (newState.phase != previousPhase) {
                    onSessionEnded(newState)
                    onLocalStateChanged(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
                if (!newState.isRunning) {
                    tickerJob = null
                    return@launch
                }
            }
        }
```

- [ ] **Step 3: Verify the module compiles**

Run: `./gradlew :wear:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt
git commit -m "feat: echo autostart settings from the watch and stop its ticker at a boundary pause"
```

- [ ] **Step 5 (manual, on a real device pair — not part of this automated plan):** With both toggles off, run a full FOCUS→SHORT_BREAK→FOCUS cycle on the phone and confirm the watch also stops at each boundary. Turn `autoStartBreak` on in phone Settings (once Task 8 lands) and confirm both devices auto-continue past FOCUS after the next sync.

---

## Task 6: `TimerUiState` — carry the two settings to the UI layer

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`

**Interfaces:**
- Consumes: `TimerSettings.autoStartBreak`/`autoStartFocus` (Task 3).
- Produces: `TimerUiState.autoStartBreak: Boolean`, `TimerUiState.autoStartFocus: Boolean`. Tasks 7 and 8 depend on this.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt` (inside the existing test class — check the file for its exact closing brace and insert before it):

```kotlin
    @Test
    fun `buildUiState carries autostart settings through`() {
        val timer = TimerState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        val settings = TimerSettings(focusMinutes = 25, breakMinutes = 5, autoStartBreak = true, autoStartFocus = false)

        val uiState = buildUiState(timer, settings)

        assertEquals(true, uiState.autoStartBreak)
        assertEquals(false, uiState.autoStartFocus)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest"`
Expected: compile failure — `autoStartBreak`/`autoStartFocus` unresolved on `TimerUiState`/`TimerSettings` constructor call, or unresolved on `uiState`.

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
    val autoStartFocus: Boolean = false
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
    autoStartFocus = settings.autoStartFocus
)
```

- [ ] **Step 4: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (this also re-verifies Task 3's tests and the existing `` `uiState reflects the injected timer state and settings` `` style tests still pass, since `TimerUiState`'s new fields default to `false` and don't disturb existing equality-based assertions).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt
git commit -m "feat: carry autostart settings through TimerUiState"
```

---

## Task 7: `TimerViewModel` — expose update methods and wire the service intent extras

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerSettingsRepository.updateAutoStartBreak`/`updateAutoStartFocus` (Task 3), `TimerService.EXTRA_AUTO_START_BREAK`/`EXTRA_AUTO_START_FOCUS` (Task 4).
- Produces: `TimerViewModel.updateAutoStartBreak(enabled: Boolean)`, `TimerViewModel.updateAutoStartFocus(enabled: Boolean)`. Task 8 depends on these.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt` (inside the class, after `` `updateLongBreakMinutes and updatePomodorosUntilLongBreak persist through the repository` ``):

```kotlin
    @Test
    fun `updateAutoStartBreak and updateAutoStartFocus persist through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateAutoStartBreak(true)
        viewModel.updateAutoStartFocus(true)

        val uiState = viewModel.uiState.first { it.autoStartBreak && it.autoStartFocus }

        assertEquals(true, uiState.autoStartBreak)
        assertEquals(true, uiState.autoStartFocus)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: compile failure — `updateAutoStartBreak`/`updateAutoStartFocus` unresolved on `TimerViewModel`.

- [ ] **Step 3: Add the methods and wire the intent extras**

In `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`, add after `updatePomodorosUntilLongBreak`:

```kotlin
    fun updateAutoStartBreak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoStartBreak(enabled) }
    }

    fun updateAutoStartFocus(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoStartFocus(enabled) }
    }
```

In `Factory.create`'s `onUpdateDurations` lambda, add two more `.putExtra` calls:

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
                ContextCompat.startForegroundService(appContext, intent)
            }
```

- [ ] **Step 4: Run the test and verify it passes, plus the full app unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: expose autostart update methods from TimerViewModel"
```

---

## Task 8: `SettingsScreen` — two switches, wired up in `PomodoroApp`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `TimerUiState.autoStartBreak`/`autoStartFocus` (Task 6), `TimerViewModel.updateAutoStartBreak`/`updateAutoStartFocus` (Task 7).
- Produces: nothing consumed elsewhere — this is the final, user-facing task.

This is the one instrumented (`androidTest`) UI change in the plan. This environment has no attached emulator/device to run it (confirmed during the 1a accent-color work), so this task's test step is written but verified only by compiling; run it manually on a device before considering the feature done end-to-end.

- [ ] **Step 1: Write the (unrun) instrumented test**

Append to `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`:

```kotlin
    @Test
    fun autoStartSwitches_areDisplayed_andInvokeCallbacks() {
        var breakToggled: Boolean? = null
        var focusToggled: Boolean? = null
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    autoStartBreak = false,
                    autoStartFocus = false,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onAutoStartBreakChange = { breakToggled = it },
                    onAutoStartFocusChange = { focusToggled = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("autoStartBreakSwitch").performClick()
        composeRule.onNodeWithTag("autoStartFocusSwitch").performClick()

        assertTrue(breakToggled == true)
        assertTrue(focusToggled == true)
    }
```

- [ ] **Step 2: Add the switches to `SettingsScreen`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`, add imports:

```kotlin
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
```

Add two new parameters to the function signature, after `onPomodorosUntilLongBreakChange`:

```kotlin
    autoStartBreak: Boolean = false,
    autoStartFocus: Boolean = false,
    onAutoStartBreakChange: (Boolean) -> Unit = {},
    onAutoStartFocusChange: (Boolean) -> Unit = {},
```

Add two switch rows after the `pomodorosUntilLongBreakSlider` block and before the final `Spacer`/`Button`:

```kotlin
        Spacer(Modifier.height(24.dp))
        Row {
            Text("자동으로 휴식 시작", modifier = Modifier.testTag("autoStartBreakLabel"))
            Switch(
                checked = autoStartBreak,
                onCheckedChange = onAutoStartBreakChange,
                modifier = Modifier.testTag("autoStartBreakSwitch")
            )
        }

        Spacer(Modifier.height(16.dp))
        Row {
            Text("자동으로 다음 뽀모도로 시작", modifier = Modifier.testTag("autoStartFocusLabel"))
            Switch(
                checked = autoStartFocus,
                onCheckedChange = onAutoStartFocusChange,
                modifier = Modifier.testTag("autoStartFocusSwitch")
            )
        }
```

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
                onBack = { navController.popBackStack() }
            )
```

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt app/src/main/java/com/pomodoro/timer/PomodoroApp.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
git commit -m "feat: add autostart switches to SettingsScreen"
```

- [ ] **Step 6 (manual, on a real device — not part of this automated plan):** Open Settings, confirm both switches render off by default, toggle each, confirm the timer screen's transition behavior changes accordingly, and re-run the phone/watch cycle check from Task 5's Step 5.

---

## Final Verification

- [ ] Run `./gradlew :core:test :app:testDebugUnitTest :wear:testDebugUnitTest` once more from a clean state and confirm everything is green.
- [ ] Run `./gradlew :app:compileDebugKotlin :wear:compileDebugKotlin` and confirm both compile.
- [ ] Update `docs/feature-backlog.md`: mark item 2 done, following the same style used for items 1 and 1a.
