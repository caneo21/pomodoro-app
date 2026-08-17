# Long-Break Pomodoro Cycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the timer from a two-phase FOCUS/BREAK toggle into the classic three-phase Pomodoro cycle (FOCUS → SHORT_BREAK, repeating, with a LONG_BREAK every N completed focus sessions), synced between the phone and the Galaxy Watch 6 companion, with cycle progress ("2/4") visible on both.

**Architecture:** `TimerPhase` grows a `LONG_BREAK` value; `TimerEngine` (in `:core`) tracks a `completedPomodoros` counter and decides SHORT_BREAK vs LONG_BREAK on every FOCUS completion, resetting the counter only when a LONG_BREAK itself completes. The counter and the two new settings (long-break duration, pomodoros-until-long-break) travel through the existing `SyncPayload`/`SyncCoordinator` machinery exactly like `focusMinutes`/`breakMinutes` do today, so the watch's cold-start reconciliation and live sync both carry the new fields automatically.

**Tech Stack:** Kotlin, Jetpack Compose (phone) / Compose for Wear OS (watch), DataStore Preferences, Wearable `DataClient` — all pre-existing; no new dependencies.

**Spec:** [docs/superpowers/specs/2026-08-17-long-break-cycle-design.md](../specs/2026-08-17-long-break-cycle-design.md)

## Global Constraints

- Long break triggers every `pomodorosUntilLongBreak` completed FOCUS sessions. Default 4, range 2–8, phone-configurable.
- The completed-FOCUS counter (`completedPomodoros`) resets to 0 only when a LONG_BREAK finishes naturally. The RESET action never resets it.
- Both phone and watch display cycle progress as `completedPomodoros/pomodorosUntilLongBreak`.
- Long-break duration is a separate configurable value. Default 15 min, range 5–45, phone-configurable.
- The watch has no settings screen (unchanged constraint from the original wear-os-companion plan) — long-break duration and cycle length are edited on the phone only and reach the watch purely via `SyncPayload`.
- Every task that touches Kotlin source must leave its module(s) compiling. Baseline sanity checks after any file edit: `./gradlew.bat :core:test`, `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`, `./gradlew.bat :wear:assembleDebug :wear:testDebugUnitTest`.
- New fields added to existing data classes (`TimerState`, `SyncPayload`, `TimerSettings`, `TimerUiState`, `WearTimerUiState`) and new parameters added to existing functions/constructors (`TimerEngine`, `buildWearUiState`, `SettingsScreen`) all get sensible default values, so pre-existing call sites and tests keep compiling unchanged unless a task explicitly says to modify them. Only call sites that need the new behavior are touched.

---

## File Structure

```
core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt              (modify)
core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt           (modify)
core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt              (modify)
core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt     (modify)
core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt             (modify)
core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt             (modify)
core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt                (modify)
core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt         (modify)
core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt (modify)

app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt                       (modify)
app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt             (modify)
app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt         (modify)
app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt               (modify)
app/src/main/java/com/pomodoro/timer/timer/TimerService.kt                           (modify)
app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt                              (modify)
app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt                          (modify)
app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt                            (modify)
app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt                        (modify)
app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt                       (modify)
app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt            (modify)
app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt                    (modify)
app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt         (modify)
app/src/main/java/com/pomodoro/timer/PomodoroApp.kt                                  (modify)

wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt     (modify)
wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt                 (modify)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt                    (modify)
wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt                (modify)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt                  (modify)
wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt              (modify)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt                     (modify)
wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt          (modify)
```

---

### Task 1: `TimerPhase` gains `LONG_BREAK`; `TimerDefaults`, `TimerState`, `NotificationContent` updated (mechanical, no cycle logic yet)

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt` (rename only, no new params)
- Modify: `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt` (rename references only)
- Modify: `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt`

**Interfaces:**
- Produces: `TimerPhase.LONG_BREAK` (new enum value); `TimerDefaults.DEFAULT_LONG_BREAK_MINUTES/MIN_LONG_BREAK_MINUTES/MAX_LONG_BREAK_MINUTES/DEFAULT_POMODOROS_UNTIL_LONG_BREAK/MIN_POMODOROS_UNTIL_LONG_BREAK/MAX_POMODOROS_UNTIL_LONG_BREAK`; `TimerState.completedPomodoros: Int = 0`. Used by every later task.
- This task does not yet make anything transition into `LONG_BREAK` — `TimerEngine` still only flips FOCUS↔SHORT_BREAK. That behavior is added in Task 2.

This task is a rename plus additive constants. `TimerPhase.BREAK` is renamed to `TimerPhase.SHORT_BREAK` everywhere it's referenced in `:core`; the compiler will flag any occurrence this task misses.

- [x] **Step 1: Rename `TimerPhase.BREAK` to `SHORT_BREAK` and add `LONG_BREAK`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt` with:

```kotlin
package com.pomodoro.timer.core

enum class TimerPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK
}
```

- [x] **Step 2: Add long-break constants to `TimerDefaults`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt` with:

```kotlin
package com.pomodoro.timer.core

object TimerDefaults {
    const val DEFAULT_FOCUS_MINUTES = 25
    const val DEFAULT_BREAK_MINUTES = 5
    const val MIN_FOCUS_MINUTES = 5
    const val MAX_FOCUS_MINUTES = 60
    const val MIN_BREAK_MINUTES = 1
    const val MAX_BREAK_MINUTES = 30
    const val DEFAULT_LONG_BREAK_MINUTES = 15
    const val MIN_LONG_BREAK_MINUTES = 5
    const val MAX_LONG_BREAK_MINUTES = 45
    const val DEFAULT_POMODOROS_UNTIL_LONG_BREAK = 4
    const val MIN_POMODOROS_UNTIL_LONG_BREAK = 2
    const val MAX_POMODOROS_UNTIL_LONG_BREAK = 8
}
```

- [x] **Step 3: Add `completedPomodoros` to `TimerState`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt` with:

```kotlin
package com.pomodoro.timer.core

data class TimerState(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val completedPomodoros: Int = 0
)
```

- [x] **Step 4: Add the `LONG_BREAK` branch to `NotificationContent`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt` with:

```kotlin
package com.pomodoro.timer.core

object NotificationContent {

    fun ongoingTitle(phase: TimerPhase): String = when (phase) {
        TimerPhase.FOCUS -> "집중 중"
        TimerPhase.SHORT_BREAK -> "휴식 중"
        TimerPhase.LONG_BREAK -> "긴 휴식 중"
    }

    fun ongoingText(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d 남음".format(minutes, seconds)
    }

    fun sessionEndTitle(newPhase: TimerPhase): String = when (newPhase) {
        TimerPhase.SHORT_BREAK -> "휴식 시간입니다!"
        TimerPhase.LONG_BREAK -> "긴 휴식 시간입니다!"
        TimerPhase.FOCUS -> "다시 집중할 시간입니다!"
    }
}
```

- [x] **Step 5: Update `NotificationContentTest`**

Replace `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt` with:

```kotlin
package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `ongoing title reflects phase`() {
        assertEquals("집중 중", NotificationContent.ongoingTitle(TimerPhase.FOCUS))
        assertEquals("휴식 중", NotificationContent.ongoingTitle(TimerPhase.SHORT_BREAK))
        assertEquals("긴 휴식 중", NotificationContent.ongoingTitle(TimerPhase.LONG_BREAK))
    }

    @Test
    fun `ongoing text formats remaining time as mm colon ss`() {
        assertEquals("12:34 남음", NotificationContent.ongoingText(754_000L))
        assertEquals("00:05 남음", NotificationContent.ongoingText(5_000L))
        assertEquals("00:00 남음", NotificationContent.ongoingText(0L))
    }

    @Test
    fun `session end title reflects the phase just entered`() {
        assertEquals("휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.SHORT_BREAK))
        assertEquals("긴 휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.LONG_BREAK))
        assertEquals("다시 집중할 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.FOCUS))
    }
}
```

- [x] **Step 6: Rename the one `TimerPhase.BREAK` reference in `TimerEngine.kt`**

`durationFor` doesn't reference `BREAK` by name (it's an `if`/`else`), so it needs no change yet — that comes in Task 2. In `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`, replace:

```kotlin
    private fun advanceAcrossPhaseBoundaries(now: Long) {
        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }
```

with:

```kotlin
    private fun advanceAcrossPhaseBoundaries(now: Long) {
        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.SHORT_BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }
```

(This whole function is rewritten with real long-break logic in Task 2 — this step just keeps `:core` compiling and passing in the meantime.)

- [x] **Step 7: Rename `TimerPhase.BREAK` in `TimerEngineTest.kt`**

In `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`, replace every occurrence of `TimerPhase.BREAK` with `TimerPhase.SHORT_BREAK`. There are 4 occurrences:
- Line ~76: `assertEquals(TimerPhase.BREAK, state.phase)` in `tick past end of focus phase transitions to break without drift`
- Line ~137: `phase = TimerPhase.BREAK,` in `adoptRemote applied immediately reproduces the remote running state`
- Line ~148: `assertEquals(TimerPhase.BREAK, state.phase)` in the same test
- Line ~190: `assertEquals(TimerPhase.BREAK, state.phase)` in `adoptRemote crosses a phase boundary when the payload is older than its remaining time`

- [x] **Step 8: Run `:core` tests to confirm the rename didn't change behavior**

Run: `./gradlew.bat :core:test`
Expected: `BUILD SUCCESSFUL`, all existing tests still pass (same count as before this task).

- [x] **Step 9: Commit**

```bash
git add core
git commit -m "refactor: rename TimerPhase.BREAK to SHORT_BREAK and add LONG_BREAK"
```

---

### Task 2: `TimerEngine` three-phase cycle logic + `SyncPayload` new fields

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`
- Modify: `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`

**Interfaces:**
- Consumes: `TimerPhase.LONG_BREAK`, `TimerState.completedPomodoros`, `TimerDefaults.DEFAULT_LONG_BREAK_MINUTES`/`DEFAULT_POMODOROS_UNTIL_LONG_BREAK` (Task 1).
- Produces: `SyncPayload.longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES`, `SyncPayload.pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK`, `SyncPayload.completedPomodoros: Int = 0`; `TimerEngine(focusMillis, shortBreakMillis, longBreakMillis = ..., pomodorosUntilLongBreak = ...)` (2 new constructor params with defaults, so the existing 2-arg call sites keep compiling); `TimerEngine.updateDurations(focusMillis, shortBreakMillis, longBreakMillis = ..., pomodorosUntilLongBreak = ...)` (same defaulting). Used by Tasks 3–10.

- [x] **Step 1: Write the failing tests**

In `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`, add one assertion to the end of the existing `tick past end of focus phase transitions to break without drift` test (it currently ends with `assertEquals(breakMillis, state.totalMillis)`):

```kotlin
        assertEquals(breakMillis, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }
```

Then add these new tests at the end of the class, before the final closing `}`:

```kotlin

    @Test
    fun `completing the Nth focus session enters LONG_BREAK and resets the count when it ends`() {
        val engine = TimerEngine(focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 2)
        engine.start(now = 0L)

        engine.tick(now = focusMillis) // 1st FOCUS done -> SHORT_BREAK, completedPomodoros = 1
        engine.tick(now = focusMillis + breakMillis) // SHORT_BREAK done -> FOCUS, completedPomodoros stays 1

        val afterSecondFocus = engine.tick(now = focusMillis + breakMillis + focusMillis)
        assertEquals(TimerPhase.LONG_BREAK, afterSecondFocus.phase)
        assertEquals(2, afterSecondFocus.completedPomodoros)
        assertEquals(900_000L, afterSecondFocus.totalMillis)

        val afterLongBreak = engine.tick(now = focusMillis + breakMillis + focusMillis + 900_000L)
        assertEquals(TimerPhase.FOCUS, afterLongBreak.phase)
        assertEquals(0, afterLongBreak.completedPomodoros)
    }

    @Test
    fun `reset preserves the completed-pomodoros count`() {
        val engine = TimerEngine(focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 4)
        engine.start(now = 0L)
        engine.tick(now = focusMillis) // completedPomodoros = 1, now in SHORT_BREAK

        val state = engine.reset(now = focusMillis)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `updateDurations floors pomodorosUntilLongBreak at 1`() {
        val engine = TimerEngine(focusMillis, breakMillis, longBreakMillis = 900_000L, pomodorosUntilLongBreak = 4)
        engine.updateDurations(focusMillis, breakMillis, 900_000L, pomodorosUntilLongBreak = 0)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis)

        assertEquals(TimerPhase.LONG_BREAK, state.phase)
        assertEquals(1, state.completedPomodoros)
    }

    @Test
    fun `adoptRemote applied immediately carries over completedPomodoros from the payload`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.SHORT_BREAK,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = breakMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            completedPomodoros = 3
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        assertEquals(3, state.completedPomodoros)
    }

    @Test
    fun `adoptRemote crossing into and out of a long break matches a tick-driven engine`() {
        val threshold = 1
        val longBreakMillis = 900_000L
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 5_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L,
            longBreakMinutes = 15,
            pomodorosUntilLongBreak = threshold,
            completedPomodoros = 0
        )
        // Focus ends at +5s (completedPomodoros -> 1, meets threshold -> LONG_BREAK), long break runs
        // 15 minutes, then FOCUS resumes (completedPomodoros resets to 0). Land 10s into that FOCUS.
        val now = 1_000_000L + 5_000L + longBreakMillis + 10_000L

        val adopted = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold).adoptRemote(payload, now)

        // Reference: an engine driven forward by plain ticks over the same wall-clock span. Starting
        // at 1_005_000 - focusMillis puts its focus phase's end exactly where the payload's is.
        val startedAt = 1_005_000L - focusMillis
        val reference = TimerEngine(focusMillis, breakMillis, longBreakMillis, threshold)
        reference.start(startedAt)
        var t = startedAt
        while (t < now) {
            t = minOf(t + 60_000L, now)
            reference.tick(t)
        }
        val expected = reference.currentState(now)

        assertEquals(TimerPhase.FOCUS, adopted.phase)
        assertEquals(expected.phase, adopted.phase)
        assertEquals(expected.remainingMillis, adopted.remainingMillis)
        assertEquals(expected.totalMillis, adopted.totalMillis)
        assertEquals(0, adopted.completedPomodoros)
        assertEquals(expected.completedPomodoros, adopted.completedPomodoros)
    }

    @Test
    fun `adoptRemote coerces a zero long-break duration up to the shared minimum`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 0L,
            totalMillis = 0L,
            focusMinutes = 0,
            breakMinutes = 0,
            lastActionAtMillis = 1_000_000L,
            longBreakMinutes = 0,
            pomodorosUntilLongBreak = 1,
            completedPomodoros = 0
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        // All three durations share one floor (see TimerEngine.MIN_PHASE_MILLIS) so the boundary loop
        // can never add 0 forever, regardless of which duration was zeroed out.
        assertEquals(TimerPhase.LONG_BREAK, state.phase)
        assertEquals(TimerDefaults.MIN_BREAK_MINUTES * 60_000L, state.totalMillis)
        assertEquals(1, state.completedPomodoros)
    }
```

- [x] **Step 2: Run the tests to see them fail to compile**

Run: `./gradlew.bat :core:test`
Expected: compile failure — `state.completedPomodoros` and the 4-arg `TimerEngine(...)` calls don't exist yet, and `SyncPayload` has no `longBreakMinutes`/`pomodorosUntilLongBreak`/`completedPomodoros` parameters.

- [x] **Step 3: Add the new fields to `SyncPayload`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt` with:

```kotlin
package com.pomodoro.timer.core

/**
 * A full snapshot of one device's timer, exchanged between phone and watch. [lastActionAtMillis] is
 * the wall-clock time (System.currentTimeMillis()) at which this snapshot was captured — the device
 * that most recently produced a [SyncPayload] wins on conflict, per [isRemoteNewer]. [breakMinutes] is
 * the short-break duration; [longBreakMinutes] and [pomodorosUntilLongBreak] configure the long-break
 * cycle, and [completedPomodoros] is how many focus sessions have completed since the last long break.
 */
data class SyncPayload(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val lastActionAtMillis: Long,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    val completedPomodoros: Int = 0
)
```

- [x] **Step 4: Rewrite `TimerEngine` with the three-phase cycle logic**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt` with:

```kotlin
package com.pomodoro.timer.core

class TimerEngine(
    private var focusMillis: Long,
    private var shortBreakMillis: Long,
    private var longBreakMillis: Long = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L,
    private var pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
) {
    private var phase: TimerPhase = TimerPhase.FOCUS
    private var isRunning: Boolean = false
    private var endTimestamp: Long = 0L
    private var remainingWhenIdle: Long = focusMillis
    private var completedPomodoros: Int = 0

    // Duration the in-flight phase was entered with. Kept separate from focusMillis/shortBreakMillis/
    // longBreakMillis so a mid-session duration change cannot make remainingMillis exceed totalMillis;
    // it applies on the next phase flip instead.
    private var currentPhaseDurationMillis: Long = focusMillis

    private fun durationFor(phase: TimerPhase): Long = when (phase) {
        TimerPhase.FOCUS -> focusMillis
        TimerPhase.SHORT_BREAK -> shortBreakMillis
        TimerPhase.LONG_BREAK -> longBreakMillis
    }

    /**
     * The phase to enter when [phase] just ended. Leaving FOCUS advances the completed-pomodoro
     * count and routes to LONG_BREAK once [pomodorosUntilLongBreak] is reached; leaving LONG_BREAK
     * resets that count. [reset] deliberately does not go through here, so it never resets the count.
     */
    private fun nextPhase(): TimerPhase = when (phase) {
        TimerPhase.FOCUS -> {
            completedPomodoros += 1
            if (completedPomodoros >= pomodorosUntilLongBreak) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK
        }
        TimerPhase.SHORT_BREAK -> TimerPhase.FOCUS
        TimerPhase.LONG_BREAK -> {
            completedPomodoros = 0
            TimerPhase.FOCUS
        }
    }

    /**
     * Flips [phase] forward (via [nextPhase]) until [endTimestamp] is in the future. Durations are
     * floored at [MIN_PHASE_MILLIS] before they ever reach here, so this always terminates.
     */
    private fun advanceAcrossPhaseBoundaries(now: Long) {
        while (now >= endTimestamp) {
            phase = nextPhase()
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

    fun currentState(now: Long): TimerState {
        val remaining = if (isRunning) (endTimestamp - now).coerceAtLeast(0) else remainingWhenIdle
        return TimerState(
            phase = phase,
            isRunning = isRunning,
            remainingMillis = remaining,
            totalMillis = currentPhaseDurationMillis,
            completedPomodoros = completedPomodoros
        )
    }

    fun start(now: Long): TimerState {
        if (!isRunning) {
            isRunning = true
            endTimestamp = now + remainingWhenIdle
        }
        return currentState(now)
    }

    fun pause(now: Long): TimerState {
        if (isRunning) {
            remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
            isRunning = false
        }
        return currentState(now)
    }

    fun reset(now: Long): TimerState {
        isRunning = false
        phase = TimerPhase.FOCUS
        remainingWhenIdle = focusMillis
        currentPhaseDurationMillis = focusMillis
        return currentState(now)
    }

    fun tick(now: Long): TimerState {
        if (!isRunning) return currentState(now)

        advanceAcrossPhaseBoundaries(now)
        remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        return currentState(now)
    }

    fun updateDurations(
        focusMillis: Long,
        shortBreakMillis: Long,
        longBreakMillis: Long = this.longBreakMillis,
        pomodorosUntilLongBreak: Int = this.pomodorosUntilLongBreak
    ) {
        this.focusMillis = focusMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.shortBreakMillis = shortBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.longBreakMillis = longBreakMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        this.pomodorosUntilLongBreak = pomodorosUntilLongBreak.coerceAtLeast(1)
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. A gap large enough to have crossed one or more phase boundaries (including into or
     * out of a long break) is resolved here, so the adopted state matches what a continuously-ticking
     * engine would show. [SyncPayload.completedPomodoros] is trusted as-is; only boundaries crossed
     * after the payload was captured are evolved locally.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = (payload.focusMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        shortBreakMillis = (payload.breakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        longBreakMillis = (payload.longBreakMinutes * 60_000L).coerceAtLeast(MIN_PHASE_MILLIS)
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak.coerceAtLeast(1)
        phase = payload.phase
        completedPomodoros = payload.completedPomodoros
        currentPhaseDurationMillis = payload.totalMillis.coerceAtLeast(MIN_PHASE_MILLIS)
        isRunning = payload.isRunning

        if (isRunning) {
            val elapsedSincePublish = (now - payload.lastActionAtMillis).coerceAtLeast(0)
            // Deliberately not clamped at 0: an overshoot is what tells the loop below how far past
            // the boundary we already are.
            endTimestamp = now + (payload.remainingMillis - elapsedSincePublish)
            advanceAcrossPhaseBoundaries(now)
            remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        } else {
            remainingWhenIdle = payload.remainingMillis
        }
        return currentState(now)
    }

    private companion object {
        /**
         * Floor for any externally-supplied phase duration. A 0 would make
         * [advanceAcrossPhaseBoundaries] add 0 forever.
         */
        val MIN_PHASE_MILLIS = minOf(
            TimerDefaults.MIN_FOCUS_MINUTES,
            TimerDefaults.MIN_BREAK_MINUTES,
            TimerDefaults.MIN_LONG_BREAK_MINUTES
        ) * 60_000L
    }
}
```

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew.bat :core:test`
Expected: `BUILD SUCCESSFUL`, all `TimerEngineTest` and `NotificationContentTest` cases pass.

- [x] **Step 6: Commit**

```bash
git add core
git commit -m "feat: add three-phase Pomodoro cycle (long break) to TimerEngine"
```

---

### Task 3: Phone settings — `TimerSettings` + `TimerSettingsRepository`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Modify: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `TimerDefaults.DEFAULT_LONG_BREAK_MINUTES` etc. (Task 1).
- Produces: `TimerSettings.longBreakMinutes: Int`, `TimerSettings.pomodorosUntilLongBreak: Int` (both defaulted); `TimerSettingsRepository.updateLongBreakMinutes(Int)`, `TimerSettingsRepository.updatePomodorosUntilLongBreak(Int)`. Used by Tasks 7–8.

- [x] **Step 1: Write the failing tests**

Add these tests to the end of `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`, before the final closing `}`:

```kotlin

    @Test
    fun `settings default to 15 minute long break and 4 pomodoros until long break`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(15, settings.longBreakMinutes)
        assertEquals(4, settings.pomodorosUntilLongBreak)
    }

    @Test
    fun `updateLongBreakMinutes persists and clamps to 5 to 45 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateLongBreakMinutes(20)
        assertEquals(20, repository.settings.first().longBreakMinutes)

        repository.updateLongBreakMinutes(99)
        assertEquals(45, repository.settings.first().longBreakMinutes)

        repository.updateLongBreakMinutes(0)
        assertEquals(5, repository.settings.first().longBreakMinutes)
    }

    @Test
    fun `updatePomodorosUntilLongBreak persists and clamps to 2 to 8 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updatePomodorosUntilLongBreak(3)
        assertEquals(3, repository.settings.first().pomodorosUntilLongBreak)

        repository.updatePomodorosUntilLongBreak(99)
        assertEquals(8, repository.settings.first().pomodorosUntilLongBreak)

        repository.updatePomodorosUntilLongBreak(0)
        assertEquals(2, repository.settings.first().pomodorosUntilLongBreak)
    }
```

- [x] **Step 2: Run the tests to see them fail to compile**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: compile failure — `longBreakMinutes`, `pomodorosUntilLongBreak`, `updateLongBreakMinutes`, `updatePomodorosUntilLongBreak` don't exist yet.

- [x] **Step 3: Add the new fields to `TimerSettings`**

Replace `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt` with:

```kotlin
package com.pomodoro.timer.settings

import com.pomodoro.timer.core.TimerDefaults

data class TimerSettings(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
)
```

- [x] **Step 4: Add the new keys and methods to `TimerSettingsRepository`**

Replace `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt` with:

```kotlin
package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pomodoro.timer.core.TimerDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimerSettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES_KEY = intPreferencesKey("break_minutes")
        val LONG_BREAK_MINUTES_KEY = intPreferencesKey("long_break_minutes")
        val POMODOROS_UNTIL_LONG_BREAK_KEY = intPreferencesKey("pomodoros_until_long_break")
        const val DEFAULT_FOCUS_MINUTES = TimerDefaults.DEFAULT_FOCUS_MINUTES
        const val DEFAULT_BREAK_MINUTES = TimerDefaults.DEFAULT_BREAK_MINUTES
        const val MIN_FOCUS_MINUTES = TimerDefaults.MIN_FOCUS_MINUTES
        const val MAX_FOCUS_MINUTES = TimerDefaults.MAX_FOCUS_MINUTES
        const val MIN_BREAK_MINUTES = TimerDefaults.MIN_BREAK_MINUTES
        const val MAX_BREAK_MINUTES = TimerDefaults.MAX_BREAK_MINUTES
        const val DEFAULT_LONG_BREAK_MINUTES = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES
        const val MIN_LONG_BREAK_MINUTES = TimerDefaults.MIN_LONG_BREAK_MINUTES
        const val MAX_LONG_BREAK_MINUTES = TimerDefaults.MAX_LONG_BREAK_MINUTES
        const val DEFAULT_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        const val MIN_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK
        const val MAX_POMODOROS_UNTIL_LONG_BREAK = TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK
    }

    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES,
            longBreakMinutes = prefs[LONG_BREAK_MINUTES_KEY] ?: DEFAULT_LONG_BREAK_MINUTES,
            pomodorosUntilLongBreak = prefs[POMODOROS_UNTIL_LONG_BREAK_KEY] ?: DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        )
    }

    suspend fun updateFocusMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_MINUTES_KEY] = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES) }
    }

    suspend fun updateBreakMinutes(minutes: Int) {
        dataStore.edit { it[BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) }
    }

    suspend fun updateLongBreakMinutes(minutes: Int) {
        dataStore.edit { it[LONG_BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_LONG_BREAK_MINUTES, MAX_LONG_BREAK_MINUTES) }
    }

    suspend fun updatePomodorosUntilLongBreak(count: Int) {
        dataStore.edit {
            it[POMODOROS_UNTIL_LONG_BREAK_KEY] = count.coerceIn(MIN_POMODOROS_UNTIL_LONG_BREAK, MAX_POMODOROS_UNTIL_LONG_BREAK)
        }
    }
}
```

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings app/src/test/java/com/pomodoro/timer/settings
git commit -m "feat: add long-break settings to TimerSettings and TimerSettingsRepository"
```

---

### Task 4: Wire the new fields through `SyncKeys` and both `SyncTransport` implementations

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`

**Interfaces:**
- Consumes: `SyncPayload.longBreakMinutes/pomodorosUntilLongBreak/completedPomodoros` (Task 2).
- Produces: `SyncKeys.KEY_LONG_BREAK_MINUTES/KEY_POMODOROS_UNTIL_LONG_BREAK/KEY_COMPLETED_POMODOROS`. Used by Tasks 5–6.

`DataClient` requires Google Play Services and a real device to exercise, matching the existing convention in this codebase (`PlayServicesSyncTransport`/`WearPlayServicesSyncTransport` have no unit tests) — this task is verified manually in Task 11 alongside the rest of the feature.

- [x] **Step 1: Add the new keys to `SyncKeys`**

Replace `core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt` with:

```kotlin
package com.pomodoro.timer.core

/** Shared wire-format constants for [SyncTransport] implementations, so phone and watch can't drift. */
object SyncKeys {
    const val TIMER_STATE_PATH = "/pomodoro/timer_state"
    const val KEY_PHASE = "phase"
    const val KEY_IS_RUNNING = "isRunning"
    const val KEY_REMAINING_MILLIS = "remainingMillis"
    const val KEY_TOTAL_MILLIS = "totalMillis"
    const val KEY_FOCUS_MINUTES = "focusMinutes"
    const val KEY_BREAK_MINUTES = "breakMinutes"
    const val KEY_LAST_ACTION_AT_MILLIS = "lastActionAtMillis"
    const val KEY_LONG_BREAK_MINUTES = "longBreakMinutes"
    const val KEY_POMODOROS_UNTIL_LONG_BREAK = "pomodorosUntilLongBreak"
    const val KEY_COMPLETED_POMODOROS = "completedPomodoros"
}
```

- [x] **Step 2: Encode/decode the new fields in `PlayServicesSyncTransport`**

Replace `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt` with:

```kotlin
package com.pomodoro.timer.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncKeys
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [SyncTransport] backed by the Wearable Data Layer API. A device's own writes typically also arrive
 * back through its own [DataClient.OnDataChangedListener] (a "self-echo"); [SyncCoordinator] already
 * ignores payloads that aren't newer than the last one it published, so no self-filtering is needed
 * here.
 */
class PlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(SyncKeys.TIMER_STATE_PATH).apply {
            dataMap.putString(SyncKeys.KEY_PHASE, payload.phase.name)
            dataMap.putBoolean(SyncKeys.KEY_IS_RUNNING, payload.isRunning)
            dataMap.putLong(SyncKeys.KEY_REMAINING_MILLIS, payload.remainingMillis)
            dataMap.putLong(SyncKeys.KEY_TOTAL_MILLIS, payload.totalMillis)
            dataMap.putInt(SyncKeys.KEY_FOCUS_MINUTES, payload.focusMinutes)
            dataMap.putInt(SyncKeys.KEY_BREAK_MINUTES, payload.breakMinutes)
            dataMap.putLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS, payload.lastActionAtMillis)
            dataMap.putInt(SyncKeys.KEY_LONG_BREAK_MINUTES, payload.longBreakMinutes)
            dataMap.putInt(SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK, payload.pomodorosUntilLongBreak)
            dataMap.putInt(SyncKeys.KEY_COMPLETED_POMODOROS, payload.completedPomodoros)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == SyncKeys.TIMER_STATE_PATH
                ) {
                    trySend(decode(DataMapItem.fromDataItem(event.dataItem).dataMap))
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }

    /**
     * Reads whatever state is already published, since [observeRemoteState] only ever delivers
     * *future* changes and would otherwise leave a freshly-started service stuck on defaults.
     */
    suspend fun fetchCurrentState(): SyncPayload? {
        val items = dataClient.dataItems.await()
        try {
            for (item in items) {
                if (item.uri.path == SyncKeys.TIMER_STATE_PATH) {
                    return decode(DataMapItem.fromDataItem(item).dataMap)
                }
            }
        } finally {
            items.release()
        }
        return null
    }

    private fun decode(map: DataMap): SyncPayload = SyncPayload(
        phase = enumValues<TimerPhase>()
            .firstOrNull { it.name == map.getString(SyncKeys.KEY_PHASE, TimerPhase.FOCUS.name) }
            ?: TimerPhase.FOCUS,
        isRunning = map.getBoolean(SyncKeys.KEY_IS_RUNNING),
        remainingMillis = map.getLong(SyncKeys.KEY_REMAINING_MILLIS),
        totalMillis = map.getLong(SyncKeys.KEY_TOTAL_MILLIS),
        focusMinutes = map.getInt(SyncKeys.KEY_FOCUS_MINUTES),
        breakMinutes = map.getInt(SyncKeys.KEY_BREAK_MINUTES),
        lastActionAtMillis = map.getLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS),
        longBreakMinutes = map.getInt(SyncKeys.KEY_LONG_BREAK_MINUTES),
        pomodorosUntilLongBreak = map.getInt(SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK),
        completedPomodoros = map.getInt(SyncKeys.KEY_COMPLETED_POMODOROS)
    )
}
```

- [x] **Step 3: Encode/decode the new fields in `WearPlayServicesSyncTransport`**

Replace `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt` with:

```kotlin
package com.pomodoro.timer.wear.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncKeys
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The watch-side twin of `:app`'s `PlayServicesSyncTransport` — same path, same wire format. */
class WearPlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(SyncKeys.TIMER_STATE_PATH).apply {
            dataMap.putString(SyncKeys.KEY_PHASE, payload.phase.name)
            dataMap.putBoolean(SyncKeys.KEY_IS_RUNNING, payload.isRunning)
            dataMap.putLong(SyncKeys.KEY_REMAINING_MILLIS, payload.remainingMillis)
            dataMap.putLong(SyncKeys.KEY_TOTAL_MILLIS, payload.totalMillis)
            dataMap.putInt(SyncKeys.KEY_FOCUS_MINUTES, payload.focusMinutes)
            dataMap.putInt(SyncKeys.KEY_BREAK_MINUTES, payload.breakMinutes)
            dataMap.putLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS, payload.lastActionAtMillis)
            dataMap.putInt(SyncKeys.KEY_LONG_BREAK_MINUTES, payload.longBreakMinutes)
            dataMap.putInt(SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK, payload.pomodorosUntilLongBreak)
            dataMap.putInt(SyncKeys.KEY_COMPLETED_POMODOROS, payload.completedPomodoros)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == SyncKeys.TIMER_STATE_PATH
                ) {
                    trySend(decode(DataMapItem.fromDataItem(event.dataItem).dataMap))
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }

    /**
     * Reads whatever state is already published, since [observeRemoteState] only ever delivers
     * *future* changes and would otherwise leave a freshly-started service stuck on defaults.
     */
    suspend fun fetchCurrentState(): SyncPayload? {
        val items = dataClient.dataItems.await()
        try {
            for (item in items) {
                if (item.uri.path == SyncKeys.TIMER_STATE_PATH) {
                    return decode(DataMapItem.fromDataItem(item).dataMap)
                }
            }
        } finally {
            items.release()
        }
        return null
    }

    private fun decode(map: DataMap): SyncPayload = SyncPayload(
        phase = enumValues<TimerPhase>()
            .firstOrNull { it.name == map.getString(SyncKeys.KEY_PHASE, TimerPhase.FOCUS.name) }
            ?: TimerPhase.FOCUS,
        isRunning = map.getBoolean(SyncKeys.KEY_IS_RUNNING),
        remainingMillis = map.getLong(SyncKeys.KEY_REMAINING_MILLIS),
        totalMillis = map.getLong(SyncKeys.KEY_TOTAL_MILLIS),
        focusMinutes = map.getInt(SyncKeys.KEY_FOCUS_MINUTES),
        breakMinutes = map.getInt(SyncKeys.KEY_BREAK_MINUTES),
        lastActionAtMillis = map.getLong(SyncKeys.KEY_LAST_ACTION_AT_MILLIS),
        longBreakMinutes = map.getInt(SyncKeys.KEY_LONG_BREAK_MINUTES),
        pomodorosUntilLongBreak = map.getInt(SyncKeys.KEY_POMODOROS_UNTIL_LONG_BREAK),
        completedPomodoros = map.getInt(SyncKeys.KEY_COMPLETED_POMODOROS)
    )
}
```

- [x] **Step 4: Build both modules to confirm they compile**

Run: `./gradlew.bat :app:assembleDebug :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/pomodoro/timer/core/SyncKeys.kt app/src/main/java/com/pomodoro/timer/sync wear/src/main/java/com/pomodoro/timer/wear/sync
git commit -m "feat: sync long-break settings and cycle count over the Wearable Data Layer"
```

---

### Task 5: Wire the new settings into `TimerService`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`

**Interfaces:**
- Consumes: `TimerEngine(focusMillis, shortBreakMillis, longBreakMillis, pomodorosUntilLongBreak)` (Task 2), `SyncPayload.longBreakMinutes/pomodorosUntilLongBreak/completedPomodoros`, `SyncKeys`-aware transport (Task 4).
- Produces: `TimerService.EXTRA_LONG_BREAK_MINUTES`, `TimerService.EXTRA_POMODOROS_UNTIL_LONG_BREAK` intent extras. Used by Task 7.

No dedicated unit test exists for `TimerService` today (it's exercised by the instrumented `TimerServiceTest`, which doesn't reference any of the APIs this task touches and needs no changes) — this task is verified via the module build and manually in Task 11.

- [x] **Step 1: Update `TimerService`**

Replace `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt` with:

```kotlin
package com.pomodoro.timer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.timer.MainActivity
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.sync.PlayServicesSyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.pomodoro.timer.action.START"
        const val ACTION_PAUSE = "com.pomodoro.timer.action.PAUSE"
        const val ACTION_RESET = "com.pomodoro.timer.action.RESET"
        const val ACTION_UPDATE_DURATIONS = "com.pomodoro.timer.action.UPDATE_DURATIONS"
        const val EXTRA_FOCUS_MINUTES = "extra_focus_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"
        const val EXTRA_LONG_BREAK_MINUTES = "extra_long_break_minutes"
        const val EXTRA_POMODOROS_UNTIL_LONG_BREAK = "extra_pomodoros_until_long_break"

        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val SESSION_END_CHANNEL_ID = "pomodoro_session_end_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerDefaults.DEFAULT_BREAK_MINUTES * 60_000L
        private val defaultLongBreakMillis = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L

        private val engine = TimerEngine(
            defaultFocusMillis,
            defaultBreakMillis,
            defaultLongBreakMillis,
            TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        )

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()

        // Static alongside `engine`, so a service instance restart can't rewind these to the
        // defaults while the engine still holds the user's real durations.
        private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
        private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
        private var longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES
        private var pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val transport = PlayServicesSyncTransport(applicationContext)
        syncCoordinator = SyncCoordinator(
            transport = transport,
            scope = serviceScope,
            onRemoteStateIsNewer = ::applyRemoteState
        )
        syncCoordinator.start()
        // addListener only delivers future changes, so pull whatever is already published once.
        serviceScope.launch {
            transport.fetchCurrentState()?.let(::applyRemoteState)
        }
    }

    private fun applyRemoteState(payload: SyncPayload) {
        focusMinutes = payload.focusMinutes
        breakMinutes = payload.breakMinutes
        longBreakMinutes = payload.longBreakMinutes
        pomodorosUntilLongBreak = payload.pomodorosUntilLongBreak
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
        updateOngoingNotification(_state.value)
    }

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
                completedPomodoros = state.completedPomodoros
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every command arrives via startForegroundService, which obliges us to enter the
        // foreground within ~5s regardless of which action it was.
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))

        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                publishSyncState(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                updateOngoingNotification(_state.value)
                publishSyncState(_state.value, now)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                publishSyncState(_state.value, now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_DURATIONS -> {
                focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, TimerDefaults.DEFAULT_FOCUS_MINUTES)
                breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, TimerDefaults.DEFAULT_BREAK_MINUTES)
                longBreakMinutes = intent.getIntExtra(EXTRA_LONG_BREAK_MINUTES, TimerDefaults.DEFAULT_LONG_BREAK_MINUTES)
                pomodorosUntilLongBreak = intent.getIntExtra(
                    EXTRA_POMODOROS_UNTIL_LONG_BREAK,
                    TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
                )
                engine.updateDurations(
                    focusMinutes * 60_000L,
                    breakMinutes * 60_000L,
                    longBreakMinutes * 60_000L,
                    pomodorosUntilLongBreak
                )
                _state.value = engine.currentState(now)
                publishSyncState(_state.value, now)
                if (!_state.value.isRunning) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateOngoingNotification(_state.value)
                }
            }
        }
        return START_NOT_STICKY
    }

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
                    publishSyncState(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pomodoro Timer", NotificationManager.IMPORTANCE_LOW)
            )
            // Channel importance overrides setPriority/setDefaults on API 26+, so session-end
            // alerts need their own IMPORTANCE_HIGH channel to make any sound at all.
            val sessionEndChannel = NotificationChannel(
                SESSION_END_CHANNEL_ID,
                "세션 종료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(sessionEndChannel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun pauseActionIntent(): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(ACTION_PAUSE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(this, 0, intent, flags)
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
        if (state.isRunning) {
            builder.addAction(android.R.drawable.ic_media_pause, "일시정지", pauseActionIntent())
        }
        return builder.build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun showSessionEndNotification(newPhase: TimerPhase) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newPhase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopTicker()
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

- [x] **Step 2: Build to confirm it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerService.kt
git commit -m "feat: wire long-break settings and cycle sync into TimerService"
```

---

### Task 6: Wire the new settings into `WearTimerService`

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`

**Interfaces:**
- Consumes: same as Task 5, watch side.
- Produces: `WearTimerService.pomodorosUntilLongBreak: StateFlow<Int>` — a companion property exposing the cycle-length setting for UI consumption, since the watch has no settings screen of its own and the only place this value is otherwise known is inside the (private) settings-tracking fields. Used by Task 9.

- [x] **Step 1: Update `WearTimerService`**

Replace `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt` with:

```kotlin
package com.pomodoro.timer.wear.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.MainActivity
import com.pomodoro.timer.wear.sync.WearPlayServicesSyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WearTimerService : Service() {

    companion object {
        const val ACTION_START = "com.pomodoro.timer.wear.action.START"
        const val ACTION_PAUSE = "com.pomodoro.timer.wear.action.PAUSE"
        const val ACTION_RESET = "com.pomodoro.timer.wear.action.RESET"

        const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "pomodoro_wear_timer_channel"
        private const val SESSION_END_CHANNEL_ID = "pomodoro_wear_session_end_channel"
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerDefaults.DEFAULT_BREAK_MINUTES * 60_000L
        private val defaultLongBreakMillis = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES * 60_000L

        private val engine = TimerEngine(
            defaultFocusMillis,
            defaultBreakMillis,
            defaultLongBreakMillis,
            TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
        )

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()

        // Static alongside `engine`, so a service instance restart can't rewind these to the
        // defaults while the engine still holds the user's real durations.
        private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
        private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
        private var longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES

        // Exposed as a StateFlow (rather than a plain var like the settings above) because the
        // watch has no settings screen of its own: WearTimerUiState's cycle-progress display is the
        // only consumer, and it needs to observe changes as they arrive from the phone.
        private val _pomodorosUntilLongBreak = MutableStateFlow(TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK)
        val pomodorosUntilLongBreak: StateFlow<Int> = _pomodorosUntilLongBreak.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val transport = WearPlayServicesSyncTransport(applicationContext)
        syncCoordinator = SyncCoordinator(
            transport = transport,
            scope = serviceScope,
            onRemoteStateIsNewer = ::applyRemoteState
        )
        syncCoordinator.start()
        // addListener only delivers future changes, so pull whatever is already published once.
        serviceScope.launch {
            transport.fetchCurrentState()?.let(::applyRemoteState)
        }
    }

    private fun applyRemoteState(payload: SyncPayload) {
        focusMinutes = payload.focusMinutes
        breakMinutes = payload.breakMinutes
        longBreakMinutes = payload.longBreakMinutes
        _pomodorosUntilLongBreak.value = payload.pomodorosUntilLongBreak
        _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
        if (_state.value.isRunning) startTicker() else stopTicker()
        updateOngoingNotification(_state.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))

        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                _state.value = engine.start(now)
                updateOngoingNotification(_state.value)
                startTicker()
                onLocalStateChanged(_state.value, now)
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                stopTicker()
                updateOngoingNotification(_state.value)
                onLocalStateChanged(_state.value, now)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                onLocalStateChanged(_state.value, now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

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
                completedPomodoros = state.completedPomodoros
            )
        )
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())

        val status = Status.Builder()
            .addTemplate("${NotificationContent.ongoingTitle(state.phase)} · ${NotificationContent.ongoingText(state.remainingMillis)}")
            .build()
        OngoingActivity.Builder(applicationContext, ONGOING_NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_popup_reminder)
            .setTouchIntent(contentIntent())
            .setStatus(status)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

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
                    onLocalStateChanged(newState, System.currentTimeMillis())
                }
                updateOngoingNotification(newState)
            }
        }
    }

    private fun onSessionEnded(newState: TimerState) {
        val notification = NotificationCompat.Builder(this, SESSION_END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.sessionEndTitle(newState.phase))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(SESSION_END_NOTIFICATION_ID, notification)
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pomodoro Timer", NotificationManager.IMPORTANCE_LOW)
            )
            val sessionEndChannel = NotificationChannel(
                SESSION_END_CHANNEL_ID,
                "세션 종료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(sessionEndChannel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onDestroy() {
        stopTicker()
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

- [x] **Step 2: Build to confirm it compiles**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt
git commit -m "feat: wire long-break settings and cycle sync into WearTimerService"
```

---

### Task 7: Phone state layer — `TimerUiState` + `TimerViewModel`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Modify: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Modify: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerSettings.longBreakMinutes/pomodorosUntilLongBreak` (Task 3), `TimerState.completedPomodoros` (Task 1), `TimerService.EXTRA_LONG_BREAK_MINUTES/EXTRA_POMODOROS_UNTIL_LONG_BREAK` (Task 5).
- Produces: `TimerUiState.longBreakMinutes/completedPomodoros/pomodorosUntilLongBreak` (all defaulted); `TimerViewModel.updateLongBreakMinutes(Int)`, `TimerViewModel.updatePomodorosUntilLongBreak(Int)`; `TimerViewModel`'s `onUpdateDurations` constructor parameter changes from `(Int, Int) -> Unit` to `(TimerSettings) -> Unit`. Used by Task 8.

- [x] **Step 1: Write the failing `TimerUiState` test**

Add this test to the end of `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`, before the final closing `}`:

```kotlin

    @Test
    fun `buildUiState carries over the long-break cycle fields`() {
        val timer = TimerState(
            phase = TimerPhase.LONG_BREAK,
            isRunning = true,
            remainingMillis = 600_000L,
            totalMillis = 900_000L,
            completedPomodoros = 4
        )
        val settings = TimerSettings(
            focusMinutes = 30,
            breakMinutes = 10,
            longBreakMinutes = 20,
            pomodorosUntilLongBreak = 4
        )

        val uiState = buildUiState(timer, settings)

        assertEquals(TimerPhase.LONG_BREAK, uiState.phase)
        assertEquals(20, uiState.longBreakMinutes)
        assertEquals(4, uiState.completedPomodoros)
        assertEquals(4, uiState.pomodorosUntilLongBreak)
    }
```

- [x] **Step 2: Run the test to see it fail to compile**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest"`
Expected: compile failure — `TimerPhase.LONG_BREAK` compiles fine (Task 1), but `uiState.longBreakMinutes`/`pomodorosUntilLongBreak` don't exist yet.

- [x] **Step 3: Add the new fields to `TimerUiState`**

Replace `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt` with:

```kotlin
package com.pomodoro.timer.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES,
    val longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    val completedPomodoros: Int = 0,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
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
    pomodorosUntilLongBreak = settings.pomodorosUntilLongBreak
)
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest"`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Write the failing `TimerViewModel` tests**

Replace `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt` with:

```kotlin
package com.pomodoro.timer.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<TimerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        // The ViewModel collects settings for the lifetime of its scope; without an onCleared
        // equivalent the collector outlives the test and reads a deleted TemporaryFolder.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newRepository(): TimerSettingsRepository {
        val file = File(tempFolder.newFolder(), "settings.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        return TimerSettingsRepository(dataStore)
    }

    private fun newViewModel(
        timerState: StateFlow<TimerState>,
        repository: TimerSettingsRepository = newRepository(),
        onCommand: (String) -> Unit = {},
        onUpdateDurations: (TimerSettings) -> Unit = {}
    ): TimerViewModel =
        TimerViewModel(timerState, repository, onCommand, onUpdateDurations)
            .also { createdViewModels.add(it) }

    @Test
    fun `uiState reflects the injected timer state and settings`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.SHORT_BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = newViewModel(timerState)

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.SHORT_BREAK }

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
        assertEquals(25, uiState.focusMinutes)
    }

    @Test
    fun `start pause and reset forward the matching TimerService actions`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = newViewModel(timerState, onCommand = { sentActions.add(it) })

        viewModel.start()
        viewModel.pause()
        viewModel.reset()

        assertEquals(
            listOf(TimerService.ACTION_START, TimerService.ACTION_PAUSE, TimerService.ACTION_RESET),
            sentActions
        )
    }

    @Test
    fun `updateFocusMinutes persists through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateFocusMinutes(40)

        val focusMinutes = viewModel.uiState.first { it.focusMinutes == 40 }.focusMinutes

        assertEquals(40, focusMinutes)
    }

    @Test
    fun `settings emissions are pushed to the timer service as duration updates`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val pushedSettings = MutableStateFlow<TimerSettings?>(null)
        val viewModel = newViewModel(
            timerState,
            onUpdateDurations = { pushedSettings.value = it }
        )

        // Cold start: the persisted (here, default) settings reach the service without user action.
        val initial = pushedSettings.first { it != null }
        assertEquals(TimerSettings(focusMinutes = 25, breakMinutes = 5), initial)

        viewModel.updateFocusMinutes(40)
        viewModel.updateBreakMinutes(10)

        val updated = pushedSettings.first { it?.focusMinutes == 40 && it.breakMinutes == 10 }
        assertEquals(40, updated?.focusMinutes)
        assertEquals(10, updated?.breakMinutes)
    }

    @Test
    fun `updateLongBreakMinutes and updatePomodorosUntilLongBreak persist through the repository`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState)

        viewModel.updateLongBreakMinutes(20)
        viewModel.updatePomodorosUntilLongBreak(3)

        val uiState = viewModel.uiState.first { it.longBreakMinutes == 20 && it.pomodorosUntilLongBreak == 3 }

        assertEquals(20, uiState.longBreakMinutes)
        assertEquals(3, uiState.pomodorosUntilLongBreak)
    }
}
```

- [x] **Step 6: Run the tests to see them fail to compile**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: compile failure — `TimerViewModel`'s `onUpdateDurations` is still `(Int, Int) -> Unit`, and it has no `updateLongBreakMinutes`/`updatePomodorosUntilLongBreak` methods.

- [x] **Step 7: Update `TimerViewModel`**

Replace `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt` with:

```kotlin
package com.pomodoro.timer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    timerState: StateFlow<TimerState>,
    private val settingsRepository: TimerSettingsRepository,
    private val onCommand: (String) -> Unit,
    private val onUpdateDurations: (TimerSettings) -> Unit
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> =
        combine(timerState, settingsRepository.settings, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    init {
        // DataStore replays the persisted value on subscription, so this also syncs the service's
        // engine on cold start, not only when the user edits Settings.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                onUpdateDurations(settings)
            }
        }
    }

    fun start() = onCommand(TimerService.ACTION_START)
    fun pause() = onCommand(TimerService.ACTION_PAUSE)
    fun reset() = onCommand(TimerService.ACTION_RESET)

    fun updateFocusMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateFocusMinutes(minutes) }
    }

    fun updateBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateBreakMinutes(minutes) }
    }

    fun updateLongBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateLongBreakMinutes(minutes) }
    }

    fun updatePomodorosUntilLongBreak(count: Int) {
        viewModelScope.launch { settingsRepository.updatePomodorosUntilLongBreak(count) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = TimerSettingsRepository(appContext.dataStore)
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, TimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            val onUpdateDurations: (TimerSettings) -> Unit = { settings ->
                val intent = Intent(appContext, TimerService::class.java)
                    .setAction(TimerService.ACTION_UPDATE_DURATIONS)
                    .putExtra(TimerService.EXTRA_FOCUS_MINUTES, settings.focusMinutes)
                    .putExtra(TimerService.EXTRA_BREAK_MINUTES, settings.breakMinutes)
                    .putExtra(TimerService.EXTRA_LONG_BREAK_MINUTES, settings.longBreakMinutes)
                    .putExtra(TimerService.EXTRA_POMODOROS_UNTIL_LONG_BREAK, settings.pomodorosUntilLongBreak)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return TimerViewModel(TimerService.state, repository, onCommand, onUpdateDurations) as T
        }
    }
}
```

- [x] **Step 8: Run the tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 9: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: expose long-break cycle fields through TimerUiState and TimerViewModel"
```

---

### Task 8: Phone UI — `TimerScreen`, `SettingsScreen`, `PomodoroApp`

**Files:**
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`
- Modify: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`

**Interfaces:**
- Consumes: `TimerUiState.longBreakMinutes/completedPomodoros/pomodorosUntilLongBreak`, `TimerViewModel.updateLongBreakMinutes/updatePomodorosUntilLongBreak` (Task 7).
- Produces: nothing consumed by later tasks — this is the phone's UI leaf.

The instrumented tests in this task require a connected device or emulator to run (`./gradlew.bat :app:connectedDebugAndroidTest`); if none is available when executing this task, skip running them and note it, then verify manually in Task 11 alongside the rest of the feature.

- [x] **Step 1: Update `TimerScreen`**

Replace `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt` with:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState

@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = phaseLabel(uiState.phase),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${uiState.completedPomodoros}/${uiState.pomodorosUntilLongBreak}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("cycleProgressText")
        )
        Spacer(Modifier.height(16.dp))

        val progress = if (uiState.totalMillis > 0) {
            uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
        } else {
            0f
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(220.dp),
                strokeWidth = 8.dp
            )
            Text(
                text = formatMillis(uiState.remainingMillis),
                style = MaterialTheme.typography.displayMedium
            )
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (uiState.isRunning) {
                Button(onClick = onPause, modifier = Modifier.testTag("pauseButton")) {
                    Text("일시정지")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.testTag("startButton")) {
                    Text("시작")
                }
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("resetButton")) {
                Text("리셋")
            }
        }

        Spacer(Modifier.height(16.dp))
        IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("settingsButton")) {
            Icon(Icons.Default.Settings, contentDescription = "설정")
        }
    }
}

private fun phaseLabel(phase: TimerPhase): String = when (phase) {
    TimerPhase.FOCUS -> "집중"
    TimerPhase.SHORT_BREAK -> "휴식"
    TimerPhase.LONG_BREAK -> "긴 휴식"
}

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

- [x] **Step 2: Add the instrumented test for cycle progress**

In `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`, add `androidx.compose.ui.test.assertExists` to the imports (alongside the existing `androidx.compose.ui.test.*` imports), then add this test at the end of the class, before the final closing `}`:

```kotlin

    @Test
    fun cycleProgress_showsCompletedOverThreshold() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(completedPomodoros = 2, pomodorosUntilLongBreak = 4),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("cycleProgressText").assertExists()
        composeRule.onNodeWithText("2/4").assertExists()
    }
```

- [x] **Step 3: Update `SettingsScreen`**

Replace `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt` with:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.core.TimerDefaults
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    focusMinutes: Int,
    breakMinutes: Int,
    longBreakMinutes: Int = TimerDefaults.DEFAULT_LONG_BREAK_MINUTES,
    pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onLongBreakMinutesChange: (Int) -> Unit = {},
    onPomodorosUntilLongBreakChange: (Int) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local drag state: committing every frame would round-trip through DataStore and fight the finger.
    var localFocusMinutes by remember(focusMinutes) { mutableFloatStateOf(focusMinutes.toFloat()) }
    var localBreakMinutes by remember(breakMinutes) { mutableFloatStateOf(breakMinutes.toFloat()) }
    var localLongBreakMinutes by remember(longBreakMinutes) { mutableFloatStateOf(longBreakMinutes.toFloat()) }
    var localPomodorosUntilLongBreak by remember(pomodorosUntilLongBreak) {
        mutableFloatStateOf(pomodorosUntilLongBreak.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("집중 시간: ${localFocusMinutes.roundToInt()}분")
        Slider(
            value = localFocusMinutes,
            onValueChange = { localFocusMinutes = it },
            onValueChangeFinished = { onFocusMinutesChange(localFocusMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_FOCUS_MINUTES.toFloat()..TimerDefaults.MAX_FOCUS_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_FOCUS_MINUTES - TimerDefaults.MIN_FOCUS_MINUTES - 1,
            modifier = Modifier.testTag("focusSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("휴식 시간: ${localBreakMinutes.roundToInt()}분")
        Slider(
            value = localBreakMinutes,
            onValueChange = { localBreakMinutes = it },
            onValueChangeFinished = { onBreakMinutesChange(localBreakMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_BREAK_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_BREAK_MINUTES - TimerDefaults.MIN_BREAK_MINUTES - 1,
            modifier = Modifier.testTag("breakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("긴 휴식 시간: ${localLongBreakMinutes.roundToInt()}분")
        Slider(
            value = localLongBreakMinutes,
            onValueChange = { localLongBreakMinutes = it },
            onValueChangeFinished = { onLongBreakMinutesChange(localLongBreakMinutes.roundToInt()) },
            valueRange = TimerDefaults.MIN_LONG_BREAK_MINUTES.toFloat()..TimerDefaults.MAX_LONG_BREAK_MINUTES.toFloat(),
            steps = TimerDefaults.MAX_LONG_BREAK_MINUTES - TimerDefaults.MIN_LONG_BREAK_MINUTES - 1,
            modifier = Modifier.testTag("longBreakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("긴 휴식까지 뽀모도로 횟수: ${localPomodorosUntilLongBreak.roundToInt()}회")
        Slider(
            value = localPomodorosUntilLongBreak,
            onValueChange = { localPomodorosUntilLongBreak = it },
            onValueChangeFinished = { onPomodorosUntilLongBreakChange(localPomodorosUntilLongBreak.roundToInt()) },
            valueRange = TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK.toFloat()..TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK.toFloat(),
            steps = TimerDefaults.MAX_POMODOROS_UNTIL_LONG_BREAK - TimerDefaults.MIN_POMODOROS_UNTIL_LONG_BREAK - 1,
            modifier = Modifier.testTag("pomodorosUntilLongBreakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
```

- [x] **Step 4: Add the instrumented test for the new sliders**

Add this test to the end of `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`, before the final closing `}`:

```kotlin

    @Test
    fun longBreakSlider_and_pomodorosUntilLongBreakSlider_areDisplayed() {
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    longBreakMinutes = 15,
                    pomodorosUntilLongBreak = 4,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onLongBreakMinutesChange = {},
                    onPomodorosUntilLongBreakChange = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("longBreakSlider").assertExists()
        composeRule.onNodeWithTag("pomodorosUntilLongBreakSlider").assertExists()
    }
```

This also requires `androidx.compose.ui.test.assertExists` in the imports — add it alongside the existing `androidx.compose.ui.test.*` imports.

- [x] **Step 5: Wire the new settings into `PomodoroApp`**

Replace `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt` with:

```kotlin
package com.pomodoro.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.screens.SettingsScreen
import com.pomodoro.timer.ui.screens.TimerScreen

private object Routes {
    const val TIMER = "timer"
    const val SETTINGS = "settings"
}

@Composable
fun PomodoroApp(viewModel: TimerViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.TIMER) {
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
                onFocusMinutesChange = viewModel::updateFocusMinutes,
                onBreakMinutesChange = viewModel::updateBreakMinutes,
                onLongBreakMinutesChange = viewModel::updateLongBreakMinutes,
                onPomodorosUntilLongBreakChange = viewModel::updatePomodorosUntilLongBreak,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [x] **Step 6: Build and run instrumented tests if a device is available**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

If a device or emulator is connected, also run: `./gradlew.bat :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, all `TimerScreenTest` and `SettingsScreenTest` cases pass. If no device is connected, skip this and rely on Task 11's manual verification instead.

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens app/src/androidTest/java/com/pomodoro/timer/ui/screens app/src/main/java/com/pomodoro/timer/PomodoroApp.kt
git commit -m "feat: show long-break cycle progress and settings on the phone UI"
```

---

### Task 9: Watch state layer — `WearTimerUiState` + `WearTimerViewModel`

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt`
- Modify: `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt`
- Modify: `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerState.completedPomodoros` (Task 1), `WearTimerService.pomodorosUntilLongBreak: StateFlow<Int>` (Task 6).
- Produces: `WearTimerUiState.completedPomodoros/pomodorosUntilLongBreak` (defaulted); `buildWearUiState(timer, pomodorosUntilLongBreak = ...)` (2nd param defaulted); `WearTimerViewModel`'s constructor gains a required `pomodorosUntilLongBreak: StateFlow<Int>` parameter. Used by Task 10.

- [x] **Step 1: Write the failing `WearTimerUiState` test**

Add this test to the end of `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt`, before the final closing `}`:

```kotlin

    @Test
    fun `buildWearUiState carries over the long-break cycle fields`() {
        val timer = TimerState(
            phase = TimerPhase.LONG_BREAK,
            isRunning = true,
            remainingMillis = 600_000L,
            totalMillis = 900_000L,
            completedPomodoros = 4
        )

        val uiState = buildWearUiState(timer, pomodorosUntilLongBreak = 4)

        assertEquals(TimerPhase.LONG_BREAK, uiState.phase)
        assertEquals(4, uiState.completedPomodoros)
        assertEquals(4, uiState.pomodorosUntilLongBreak)
    }
```

- [x] **Step 2: Run the test to see it fail to compile**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerUiStateTest"`
Expected: compile failure — `buildWearUiState` doesn't accept a second argument yet, and `uiState.completedPomodoros`/`pomodorosUntilLongBreak` don't exist.

- [x] **Step 3: Add the new fields to `WearTimerUiState`**

Replace `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt` with:

```kotlin
package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState

data class WearTimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val completedPomodoros: Int = 0,
    val pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
)

fun buildWearUiState(
    timer: TimerState,
    pomodorosUntilLongBreak: Int = TimerDefaults.DEFAULT_POMODOROS_UNTIL_LONG_BREAK
): WearTimerUiState = WearTimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    completedPomodoros = timer.completedPomodoros,
    pomodorosUntilLongBreak = pomodorosUntilLongBreak
)
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerUiStateTest"`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Write the failing `WearTimerViewModel` tests**

Replace `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt` with:

```kotlin
package com.pomodoro.timer.wear.ui

import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.timer.WearTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<WearTimerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        timerState: StateFlow<TimerState>,
        pomodorosUntilLongBreak: StateFlow<Int> = MutableStateFlow(4),
        onCommand: (String) -> Unit = {}
    ): WearTimerViewModel =
        WearTimerViewModel(timerState, pomodorosUntilLongBreak, onCommand).also { createdViewModels.add(it) }

    @Test
    fun `uiState reflects the injected timer state`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.SHORT_BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = newViewModel(timerState)

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.SHORT_BREAK }

        assertEquals(TimerPhase.SHORT_BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
    }

    @Test
    fun `uiState reflects the injected pomodoros-until-long-break count`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = newViewModel(timerState, pomodorosUntilLongBreak = MutableStateFlow(3))

        val uiState = viewModel.uiState.first { it.pomodorosUntilLongBreak == 3 }

        assertEquals(3, uiState.pomodorosUntilLongBreak)
    }

    @Test
    fun `start pause and reset forward the matching WearTimerService actions`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = newViewModel(timerState, onCommand = { sentActions.add(it) })

        viewModel.start()
        viewModel.pause()
        viewModel.reset()

        assertEquals(
            listOf(WearTimerService.ACTION_START, WearTimerService.ACTION_PAUSE, WearTimerService.ACTION_RESET),
            sentActions
        )
    }
}
```

- [x] **Step 6: Run the tests to see them fail to compile**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerViewModelTest"`
Expected: compile failure — `WearTimerViewModel` doesn't accept a `pomodorosUntilLongBreak` constructor argument yet.

- [x] **Step 7: Update `WearTimerViewModel`**

Replace `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt` with:

```kotlin
package com.pomodoro.timer.wear.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.timer.WearTimerService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class WearTimerViewModel(
    timerState: StateFlow<TimerState>,
    pomodorosUntilLongBreak: StateFlow<Int>,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<WearTimerUiState> =
        combine(timerState, pomodorosUntilLongBreak, ::buildWearUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearTimerUiState())

    fun start() = onCommand(WearTimerService.ACTION_START)
    fun pause() = onCommand(WearTimerService.ACTION_PAUSE)
    fun reset() = onCommand(WearTimerService.ACTION_RESET)

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, WearTimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return WearTimerViewModel(WearTimerService.state, WearTimerService.pomodorosUntilLongBreak, onCommand) as T
        }
    }
}
```

- [x] **Step 8: Run the tests to verify they pass**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.WearTimerViewModelTest"`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 9: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt
git commit -m "feat: expose long-break cycle fields through WearTimerUiState and WearTimerViewModel"
```

---

### Task 10: Watch UI — `WearTimerScreen`

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`
- Modify: `wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt`

**Interfaces:**
- Consumes: `WearTimerUiState.completedPomodoros/pomodorosUntilLongBreak` (Task 9).
- Produces: nothing consumed by later tasks — this is the watch's UI leaf.

Same as Task 8, the instrumented test here requires a connected device or emulator; if none is available, skip running it and rely on Task 11's manual verification.

- [x] **Step 1: Update `WearTimerScreen`**

Replace `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt` with:

```kotlin
package com.pomodoro.timer.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.pomodoro.timer.core.TimerPhase

@Composable
fun WearTimerScreen(
    uiState: WearTimerUiState,
    isAmbient: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingText = formatWearMillis(uiState.remainingMillis)

    if (isAmbient) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = remainingText, color = Color.White)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = phaseLabel(uiState.phase),
            style = MaterialTheme.typography.title3
        )
        Text(
            text = "${uiState.completedPomodoros}/${uiState.pomodorosUntilLongBreak}",
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.testTag("cycleProgressText")
        )
        Spacer(Modifier.height(8.dp))

        val progress = if (uiState.totalMillis > 0) {
            uiState.remainingMillis.toFloat() / uiState.totalMillis.toFloat()
        } else {
            0f
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(120.dp)
            )
            Text(text = remainingText, style = MaterialTheme.typography.title2)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.isRunning) {
                Button(onClick = onPause, modifier = Modifier.testTag("pauseButton")) {
                    Icon(Icons.Default.Pause, contentDescription = "일시정지")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.testTag("startButton")) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "시작")
                }
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.testTag("resetButton")) {
                Icon(Icons.Default.Refresh, contentDescription = "리셋")
            }
        }
    }
}

private fun phaseLabel(phase: TimerPhase): String = when (phase) {
    TimerPhase.FOCUS -> "집중"
    TimerPhase.SHORT_BREAK -> "휴식"
    TimerPhase.LONG_BREAK -> "긴 휴식"
}

fun formatWearMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

- [x] **Step 2: Add the instrumented test for cycle progress**

In `wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt`, add `androidx.compose.ui.test.assertExists` to the imports (alongside the existing `androidx.compose.ui.test.*` imports), then add this test at the end of the class, before the final closing `}`:

```kotlin

    @Test
    fun cycleProgress_showsCompletedOverThreshold() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(completedPomodoros = 2, pomodorosUntilLongBreak = 4),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithTag("cycleProgressText").assertExists()
        composeRule.onNodeWithText("2/4").assertExists()
    }
```

- [x] **Step 3: Build and run instrumented tests if a device is available**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

If a device or emulator is connected, also run: `./gradlew.bat :wear:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, all `WearTimerScreenTest` cases pass. If no device is connected, skip this and rely on Task 11's manual verification instead.

- [x] **Step 4: Commit**

```bash
git add wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt
git commit -m "feat: show long-break cycle progress on the watch UI"
```

---

### Task 11: Full build, full test suite, and manual pairing verification

**Files:** none (verification only)

- [x] **Step 1: Run the full unit test suite across all three modules**

Run:
```bash
./gradlew.bat :core:test :app:assembleDebug :app:testDebugUnitTest :wear:assembleDebug :wear:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, every test in `:core`, `:app`, and `:wear` passes.

- [x] **Step 2: Reinstall both apps on the paired phone and watch**

```bash
./gradlew.bat :app:installDebug :wear:installDebug
```

- [x] **Step 3: Manually verify the full cycle on real devices**

On the phone, open Settings and temporarily set "긴 휴식까지 뽀모도로 횟수" to 2 and "집중 시간"/"휴식 시간" to short values (e.g. 1 minute each) to make the cycle fast to observe. Then:
1. Start the timer on the phone. Confirm the phone shows "1/2" progress under "집중".
2. Let the first focus session complete. Confirm it transitions to "휴식" (short break) and the watch (once opened) reflects the same phase and "1/2" progress.
3. Let the short break complete and the second focus session complete. Confirm both phone and watch transition to "긴 휴식" and show "2/2".
4. Let the long break complete. Confirm both devices return to "집중" with progress reset to "0/2".
5. Press "리셋" mid-cycle (after at least one completed focus session) and confirm the progress count is **not** reset — only the current session's remaining time resets to full.

Restore the settings to their normal values (or leave them, since this is a personal app) once verification is complete.

- [x] **Step 4: Update the plan's checkboxes**

Mark every checkbox in this file as complete (`- [x]`) once the above passes.
