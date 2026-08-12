# Galaxy Watch 6 Companion App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Wear OS (Galaxy Watch 6) Pomodoro timer app that shares its countdown logic with the existing phone app via a new `:core` module, and stays in sync with the phone over Bluetooth using the Wearable Data Layer API, per [docs/superpowers/specs/2026-08-13-wear-os-companion-design.md](../specs/2026-08-13-wear-os-companion-design.md).

**Architecture:** A new pure-Kotlin `:core` module holds `TimerEngine`/`TimerState`/`TimerPhase`/`NotificationContent`/`TimerDefaults` (moved from `:app`, no behavior change) plus new sync primitives (`SyncPayload`, `SyncTransport`, `SyncCoordinator`). Both `:app` (phone) and the new `:wear` module run their own Foreground Service with their own `TimerEngine` instance, and each wires a `SyncCoordinator` to a `PlayServicesSyncTransport` (Wearable `DataClient`) so that whichever device acted most recently (by wall-clock timestamp) is adopted by the other on every state change and on reconnect.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01) for the phone, Compose for Wear OS (`androidx.wear.compose` 1.4.1) for the watch, AGP 8.7.3, Gradle 8.10.2, `com.google.android.gms:play-services-wearable:19.0.0` + `kotlinx-coroutines-play-services:1.9.0` for phone↔watch sync, `androidx.wear:wear-ongoing:1.0.0` for the watch's ongoing-activity chip, `androidx.wear.ambient:ambient:1.0.1` for ambient-mode support.

## Global Constraints

- New pure-Kotlin module `:core` uses the `org.jetbrains.kotlin.jvm` plugin (no Android dependency), so its logic stays testable with plain JUnit — same principle the original plan used for `TimerEngine`.
- New Wear OS module `:wear`, applicationId `com.pomodoro.timer.wear`, namespace `com.pomodoro.timer.wear`, minSdk 30 (Wear OS 3+ floor for Compose for Wear OS), targetSdk 35, compileSdk 35 — mirrors `:app`'s versions except minSdk.
- Phone applicationId stays `com.pomodoro.timer` (`:app`, unchanged).
- Sync uses **only** the Wearable `DataClient`, not `MessageClient`. `DataClient` already delivers within roughly a second while both devices are connected *and* automatically redelivers the latest item on reconnect after a gap — i.e. it covers both the "즉각적인 명령" and "재연결 시 자동 전달" requirements from the spec with a single mechanism, so a second client is unneeded complexity. Reconciliation is always "adopt the newer full snapshot," never "replay an individual command" — one rule, not two.
- Conflict resolution: whichever `SyncPayload` has the larger `lastActionAtMillis` (wall-clock millis) wins, per the spec's "가장 최근 조작이 이김" policy. Assumes both devices' clocks are NTP-synced (spec's stated assumption; no clock-skew correction is implemented).
- Settings (focus/break minutes) are edited on the phone only; the watch has no settings screen and only ever receives them through `SyncPayload`.
- Every step in this plan that touches Kotlin/XML source must leave the touched module compiling; treat `./gradlew.bat :core:build`, `./gradlew.bat :app:assembleDebug`, and `./gradlew.bat :wear:assembleDebug` as the baseline sanity checks after any file edit, even where a task doesn't call them out explicitly.

---

## File Structure

```
settings.gradle.kts                                                     (modify)

core/build.gradle.kts                                                   (create)
core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt              (create, moved)
core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt              (create, moved)
core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt             (create, moved + adoptRemote)
core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt     (create, moved)
core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt           (create, moved)
core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt             (create)
core/src/main/kotlin/com/pomodoro/timer/core/SyncTransport.kt           (create)
core/src/main/kotlin/com/pomodoro/timer/core/SyncCoordinator.kt         (create)
core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt         (create, moved + adoptRemote tests)
core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt (create, moved)
core/src/test/kotlin/com/pomodoro/timer/core/SyncCoordinatorTest.kt     (create)

app/build.gradle.kts                                                    (modify)
app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt                (delete)
app/src/main/java/com/pomodoro/timer/timer/TimerState.kt                (delete)
app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt               (delete)
app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt       (delete)
app/src/main/java/com/pomodoro/timer/timer/TimerDefaults.kt             (delete)
app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt           (delete)
app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt   (delete)
app/src/main/java/com/pomodoro/timer/timer/TimerService.kt              (modify)
app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt   (modify)
app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt                 (modify)
app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt               (modify)
app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt          (modify)
app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt       (modify)
app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt (modify)
app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt (modify)
app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt             (modify)
app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt           (modify)
app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt  (create)

wear/build.gradle.kts                                                              (create)
wear/src/main/AndroidManifest.xml                                                  (create)
wear/src/main/res/values/strings.xml                                               (create)
wear/src/main/res/values/colors.xml                                                (create)
wear/src/main/res/values/themes.xml                                                (create)
wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml                                (create)
wear/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml                          (create)
wear/src/main/res/drawable/ic_launcher_background.xml                              (create)
wear/src/main/res/drawable/ic_launcher_foreground.xml                              (create)
wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt                      (create)
wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt                         (create, then modified in Task 10)
wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt               (create)
wear/src/androidTest/java/com/pomodoro/timer/wear/timer/WearTimerServiceTest.kt    (create)
wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt   (create)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt                  (create)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt                (create)
wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt                   (create)
wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt              (create)
wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt            (create)
wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt        (create)
```

---

### Task 1: Extract `:core` module (move existing timer logic, no behavior change)

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/build.gradle.kts`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt`
- Create: `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`
- Create: `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt`
- Delete: `app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt`
- Delete: `app/src/main/java/com/pomodoro/timer/timer/TimerState.kt`
- Delete: `app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt`
- Delete: `app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt`
- Delete: `app/src/main/java/com/pomodoro/timer/timer/TimerDefaults.kt`
- Delete: `app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt`
- Delete: `app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt`

**Interfaces:**
- Produces: `enum class TimerPhase`, `data class TimerState`, `class TimerEngine`, `object NotificationContent`, `object TimerDefaults` — all under package `com.pomodoro.timer.core` (moved verbatim from `com.pomodoro.timer.timer`, only the package line changes). Used by Task 2 (`:app` fixups) and every later `:wear` task.

This task only relocates already-tested code into a new package/module. No logic changes, no new tests — the existing test bodies move unchanged.

- [ ] **Step 1: Add the `:core` module to `settings.gradle.kts`**

Append to `settings.gradle.kts`:

```kotlin
include(":core")
```

- [ ] **Step 2: Write the `:core` build file**

Create `core/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 3: Move `TimerPhase`, `TimerState`, `TimerEngine`, `NotificationContent`, `TimerDefaults` into `:core`**

Create `core/src/main/kotlin/com/pomodoro/timer/core/TimerPhase.kt`:

```kotlin
package com.pomodoro.timer.core

enum class TimerPhase {
    FOCUS,
    BREAK
}
```

Create `core/src/main/kotlin/com/pomodoro/timer/core/TimerState.kt`:

```kotlin
package com.pomodoro.timer.core

data class TimerState(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long
)
```

Create `core/src/main/kotlin/com/pomodoro/timer/core/TimerDefaults.kt`:

```kotlin
package com.pomodoro.timer.core

object TimerDefaults {
    const val DEFAULT_FOCUS_MINUTES = 25
    const val DEFAULT_BREAK_MINUTES = 5
    const val MIN_FOCUS_MINUTES = 5
    const val MAX_FOCUS_MINUTES = 60
    const val MIN_BREAK_MINUTES = 1
    const val MAX_BREAK_MINUTES = 30
}
```

Create `core/src/main/kotlin/com/pomodoro/timer/core/NotificationContent.kt`:

```kotlin
package com.pomodoro.timer.core

object NotificationContent {

    fun ongoingTitle(phase: TimerPhase): String = when (phase) {
        TimerPhase.FOCUS -> "집중 중"
        TimerPhase.BREAK -> "휴식 중"
    }

    fun ongoingText(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d 남음".format(minutes, seconds)
    }

    fun sessionEndTitle(newPhase: TimerPhase): String = when (newPhase) {
        TimerPhase.BREAK -> "휴식 시간입니다!"
        TimerPhase.FOCUS -> "다시 집중할 시간입니다!"
    }
}
```

Create `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`:

```kotlin
package com.pomodoro.timer.core

class TimerEngine(
    private var focusMillis: Long,
    private var breakMillis: Long
) {
    private var phase: TimerPhase = TimerPhase.FOCUS
    private var isRunning: Boolean = false
    private var endTimestamp: Long = 0L
    private var remainingWhenIdle: Long = focusMillis

    // Duration the in-flight phase was entered with. Kept separate from focusMillis/breakMillis so a
    // mid-session duration change cannot make remainingMillis exceed totalMillis; it applies on the
    // next phase flip instead.
    private var currentPhaseDurationMillis: Long = focusMillis

    private fun durationFor(phase: TimerPhase): Long =
        if (phase == TimerPhase.FOCUS) focusMillis else breakMillis

    fun currentState(now: Long): TimerState {
        val remaining = if (isRunning) (endTimestamp - now).coerceAtLeast(0) else remainingWhenIdle
        return TimerState(
            phase = phase,
            isRunning = isRunning,
            remainingMillis = remaining,
            totalMillis = currentPhaseDurationMillis
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

        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
        remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        return currentState(now)
    }

    fun updateDurations(focusMillis: Long, breakMillis: Long) {
        this.focusMillis = focusMillis
        this.breakMillis = breakMillis
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
            currentPhaseDurationMillis = durationFor(phase)
        }
    }

    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. If that gap is large enough to have crossed a phase boundary, [remainingMillis] may
     * briefly read 0 until the next [tick] call, which flips phases correctly from there.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = payload.focusMinutes * 60_000L
        breakMillis = payload.breakMinutes * 60_000L
        phase = payload.phase
        currentPhaseDurationMillis = payload.totalMillis
        isRunning = payload.isRunning

        val elapsedSincePublish = (now - payload.lastActionAtMillis).coerceAtLeast(0)
        val remainingNow = (payload.remainingMillis - elapsedSincePublish).coerceAtLeast(0)
        if (isRunning) {
            endTimestamp = now + remainingNow
        } else {
            remainingWhenIdle = remainingNow
        }
        return currentState(now)
    }
}
```

- [ ] **Step 4: Move the existing tests into `:core`**

Create `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`:

```kotlin
package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val focusMillis = 25 * 60_000L
    private val breakMillis = 5 * 60_000L

    @Test
    fun `initial state is idle focus with full duration`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val state = engine.currentState(now = 0L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
        assertEquals(focusMillis, state.totalMillis)
    }

    @Test
    fun `start begins counting down from now`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)

        val state = engine.tick(now = 10_000L)

        assertTrue(state.isRunning)
        assertEquals(focusMillis - 10_000L, state.remainingMillis)
    }

    @Test
    fun `pause freezes remaining time and resume continues from same point`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        val paused = engine.pause(now = 10_000L)
        assertFalse(paused.isRunning)
        assertEquals(focusMillis - 10_000L, paused.remainingMillis)

        val stillPaused = engine.currentState(now = 60_000L)
        assertEquals(focusMillis - 10_000L, stillPaused.remainingMillis)

        val resumed = engine.start(now = 60_000L)
        assertTrue(resumed.isRunning)
        assertEquals(focusMillis - 10_000L, resumed.remainingMillis)

        val laterTick = engine.tick(now = 65_000L)
        assertEquals(focusMillis - 15_000L, laterTick.remainingMillis)
    }

    @Test
    fun `reset returns to idle focus with full duration`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = 10_000L)

        val state = engine.reset(now = 10_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertFalse(state.isRunning)
        assertEquals(focusMillis, state.remainingMillis)
    }

    @Test
    fun `tick past end of focus phase transitions to break without drift`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)

        val state = engine.tick(now = focusMillis + 5_000L)

        assertEquals(TimerPhase.BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(breakMillis - 5_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `tick past end of break phase transitions back to focus`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = focusMillis)

        val state = engine.tick(now = focusMillis + breakMillis + 2_000L)

        assertEquals(TimerPhase.FOCUS, state.phase)
        assertEquals(focusMillis - 2_000L, state.remainingMillis)
    }

    @Test
    fun `tick while paused does not change state`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val before = engine.currentState(now = 0L)

        val after = engine.tick(now = 999_000L)

        assertEquals(before, after)
    }

    @Test
    fun `updateDurations changes the duration used by the current idle phase`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.updateDurations(focusMillis = 10 * 60_000L, breakMillis = 2 * 60_000L)

        val state = engine.currentState(now = 0L)

        assertEquals(10 * 60_000L, state.remainingMillis)
        assertEquals(10 * 60_000L, state.totalMillis)
    }

    @Test
    fun `updateDurations mid-session does not change the running phase total until it flips`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        engine.start(now = 0L)
        engine.tick(now = 60_000L)

        engine.updateDurations(focusMillis = 10 * 60_000L, breakMillis = 2 * 60_000L)

        val midSession = engine.currentState(now = 60_000L)
        assertEquals(focusMillis, midSession.totalMillis)
        assertEquals(focusMillis - 60_000L, midSession.remainingMillis)
        assertTrue(midSession.remainingMillis <= midSession.totalMillis)

        val afterFlip = engine.tick(now = focusMillis)
        assertEquals(TimerPhase.BREAK, afterFlip.phase)
        assertEquals(2 * 60_000L, afterFlip.totalMillis)
    }

    @Test
    fun `adoptRemote applied immediately reproduces the remote running state`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.BREAK,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = breakMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        assertEquals(TimerPhase.BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `adoptRemote accounts for elapsed time between publish and apply`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_010_000L) // 10s later

        assertEquals(80_000L, state.remainingMillis)
        val laterTick = engine.tick(now = 1_015_000L)
        assertEquals(75_000L, laterTick.remainingMillis)
    }

    @Test
    fun `adoptRemote of a paused payload freezes at the adjusted remaining time`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = false,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_005_000L)

        assertFalse(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        val later = engine.currentState(now = 2_000_000L)
        assertEquals(90_000L, later.remainingMillis)
    }
}
```

Create `core/src/test/kotlin/com/pomodoro/timer/core/NotificationContentTest.kt`:

```kotlin
package com.pomodoro.timer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `ongoing title reflects phase`() {
        assertEquals("집중 중", NotificationContent.ongoingTitle(TimerPhase.FOCUS))
        assertEquals("휴식 중", NotificationContent.ongoingTitle(TimerPhase.BREAK))
    }

    @Test
    fun `ongoing text formats remaining time as mm colon ss`() {
        assertEquals("12:34 남음", NotificationContent.ongoingText(754_000L))
        assertEquals("00:05 남음", NotificationContent.ongoingText(5_000L))
        assertEquals("00:00 남음", NotificationContent.ongoingText(0L))
    }

    @Test
    fun `session end title reflects the phase just entered`() {
        assertEquals("휴식 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.BREAK))
        assertEquals("다시 집중할 시간입니다!", NotificationContent.sessionEndTitle(TimerPhase.FOCUS))
    }
}
```

Note: `TimerEngineTest` above references `SyncPayload`, which doesn't exist yet — that's created in Task 3. This is intentional: Steps 5–6 below run the test suite *before* `adoptRemote`'s tests are added is not possible, so this task and Task 3 are written so that Task 1 compiles the engine but its full test file (including `adoptRemote` tests) only compiles once Task 3 adds `SyncPayload`. To keep every task independently buildable, add the `adoptRemote`-related tests in Task 3 instead of here — **skip adding the three `adoptRemote` tests and the `adoptRemote` method in this task**; add `TimerEngine.adoptRemote` and its three tests as part of Task 3 alongside `SyncPayload`. Use only the first 9 tests (through `updateDurations mid-session...`) in this task's `TimerEngineTest.kt`, and omit the `adoptRemote` method from this task's `TimerEngine.kt`.

- [ ] **Step 5: Delete the old copies from `:app`**

```bash
git rm app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt
git rm app/src/main/java/com/pomodoro/timer/timer/TimerState.kt
git rm app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt
git rm app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt
git rm app/src/main/java/com/pomodoro/timer/timer/TimerDefaults.kt
git rm app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt
git rm app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt
```

(`:app` will not build again until Task 2 fixes its imports — that's expected and fixed in the very next task.)

- [ ] **Step 6: Run the `:core` tests**

Run: `./gradlew.bat :core:test`
Expected: `BUILD SUCCESSFUL`, 9 `TimerEngineTest` tests + 3 `NotificationContentTest` tests pass.

- [ ] **Step 7: Commit**

```bash
git add core settings.gradle.kts
git add -u app
git commit -m "refactor: extract TimerEngine/NotificationContent into a shared :core module"
```

---

### Task 2: Fix `:app` imports for the moved types

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`
- Modify: `app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Modify: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`
- Modify: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`
- Modify: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: everything produced by Task 1 (`com.pomodoro.timer.core.*`).
- Produces: no new interfaces — `:app` compiles and all its existing tests pass again, now sourcing timer logic from `:core`.

This task is a mechanical import fix. No behavior changes; every file below keeps its existing logic and only swaps `com.pomodoro.timer.timer.{TimerPhase,TimerState,TimerEngine,NotificationContent,TimerDefaults}` for `com.pomodoro.timer.core.*`. `TimerService.kt` and `TimerServiceTest.kt` need *new* import lines added (they used to be in the same package as the moved types and had no imports for them at all).

- [ ] **Step 1: Add the `:core` dependency to `:app`**

In `app/build.gradle.kts`, add this line as the first line inside `dependencies { ... }`:

```kotlin
    implementation(project(":core"))
```

- [ ] **Step 2: Fix `TimerService.kt`**

Replace the top of `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt` (the `package` line through the last `import`) with:

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
```

Leave the rest of the file (everything from `class TimerService : Service() {` onward) unchanged.

- [ ] **Step 3: Fix `TimerServiceTest.kt`**

In `app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt`, add this import alongside the existing ones (after `import androidx.test.ext.junit.runners.AndroidJUnit4`):

```kotlin
import com.pomodoro.timer.core.TimerPhase
```

- [ ] **Step 4: Fix `TimerUiState.kt`**

Replace the import block at the top of `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt` with:

```kotlin
package com.pomodoro.timer.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
```

- [ ] **Step 5: Fix `TimerViewModel.kt`**

In `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`, replace:

```kotlin
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
```

with:

```kotlin
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
```

- [ ] **Step 6: Fix `TimerScreen.kt`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`, replace:

```kotlin
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
```

with:

```kotlin
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
```

- [ ] **Step 7: Fix `SettingsScreen.kt`**

In `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`, replace:

```kotlin
import com.pomodoro.timer.timer.TimerDefaults
```

with:

```kotlin
import com.pomodoro.timer.core.TimerDefaults
```

- [ ] **Step 8: Fix `TimerSettingsRepository.kt`**

In `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`, replace:

```kotlin
import com.pomodoro.timer.timer.TimerDefaults
```

with:

```kotlin
import com.pomodoro.timer.core.TimerDefaults
```

- [ ] **Step 9: Fix `TimerScreenTest.kt`**

In `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`, replace:

```kotlin
import com.pomodoro.timer.timer.TimerPhase
```

with:

```kotlin
import com.pomodoro.timer.core.TimerPhase
```

- [ ] **Step 10: Fix `TimerUiStateTest.kt`**

In `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`, replace:

```kotlin
import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerState
```

with:

```kotlin
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettings
```

- [ ] **Step 11: Fix `TimerViewModelTest.kt`**

In `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`, replace:

```kotlin
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
```

with:

```kotlin
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerService
```

- [ ] **Step 12: Build and run the full `:app` unit test suite**

Run: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all previously-passing `:app` unit tests still pass.

- [ ] **Step 13: Commit**

```bash
git add app
git commit -m "refactor: point :app imports at the extracted :core module"
```

---

### Task 3: Sync primitives in `:core` (`SyncPayload`, `SyncTransport`, `SyncCoordinator`, `TimerEngine.adoptRemote`)

**Files:**
- Modify: `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`
- Modify: `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/SyncTransport.kt`
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/SyncCoordinator.kt`
- Create: `core/src/test/kotlin/com/pomodoro/timer/core/SyncCoordinatorTest.kt`

**Interfaces:**
- Consumes: `TimerPhase`, `TimerState`, `TimerEngine` (Task 1).
- Produces: `data class SyncPayload(phase, isRunning, remainingMillis, totalMillis, focusMinutes, breakMinutes, lastActionAtMillis)`; `interface SyncTransport { suspend fun publish(payload: SyncPayload); fun observeRemoteState(): Flow<SyncPayload> }`; `fun isRemoteNewer(local: SyncPayload?, remote: SyncPayload): Boolean`; `class SyncCoordinator(transport: SyncTransport, scope: CoroutineScope, onRemoteStateIsNewer: (SyncPayload) -> Unit) { fun start(); fun publishLocalState(payload: SyncPayload) }`; `TimerEngine.adoptRemote(payload: SyncPayload, now: Long): TimerState`. Used by Task 4 (`:app`) and Task 8 (`:wear`).

- [ ] **Step 1: Add `SyncPayload`**

Create `core/src/main/kotlin/com/pomodoro/timer/core/SyncPayload.kt`:

```kotlin
package com.pomodoro.timer.core

/**
 * A full snapshot of one device's timer, exchanged between phone and watch. [lastActionAtMillis] is
 * the wall-clock time (System.currentTimeMillis()) at which this snapshot was captured — the device
 * that most recently produced a [SyncPayload] wins on conflict, per [isRemoteNewer].
 */
data class SyncPayload(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val lastActionAtMillis: Long
)
```

- [ ] **Step 2: Add `SyncTransport`**

Create `core/src/main/kotlin/com/pomodoro/timer/core/SyncTransport.kt`:

```kotlin
package com.pomodoro.timer.core

import kotlinx.coroutines.flow.Flow

/** Abstraction over however payloads actually cross from one device to the other. */
interface SyncTransport {
    suspend fun publish(payload: SyncPayload)
    fun observeRemoteState(): Flow<SyncPayload>
}
```

- [ ] **Step 3: Add `SyncCoordinator` and `isRemoteNewer`**

Create `core/src/main/kotlin/com/pomodoro/timer/core/SyncCoordinator.kt`:

```kotlin
package com.pomodoro.timer.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "가장 최근 조작이 이김": a remote payload wins if we have no local state yet, or it is newer. */
fun isRemoteNewer(local: SyncPayload?, remote: SyncPayload): Boolean =
    local == null || remote.lastActionAtMillis > local.lastActionAtMillis

/**
 * Wires a [SyncTransport] to a local timer: forwards locally-produced [SyncPayload]s out, and invokes
 * [onRemoteStateIsNewer] whenever an incoming payload is newer than the latest one this coordinator
 * knows about (either published locally or previously received).
 */
class SyncCoordinator(
    private val transport: SyncTransport,
    private val scope: CoroutineScope,
    private val onRemoteStateIsNewer: (SyncPayload) -> Unit
) {
    private var lastKnown: SyncPayload? = null

    fun start() {
        scope.launch {
            transport.observeRemoteState().collect { remote ->
                if (isRemoteNewer(lastKnown, remote)) {
                    lastKnown = remote
                    onRemoteStateIsNewer(remote)
                }
            }
        }
    }

    fun publishLocalState(payload: SyncPayload) {
        lastKnown = payload
        scope.launch { transport.publish(payload) }
    }
}
```

- [ ] **Step 4: Add `TimerEngine.adoptRemote` (deferred from Task 1)**

In `core/src/main/kotlin/com/pomodoro/timer/core/TimerEngine.kt`, add this method inside the `TimerEngine` class, after `updateDurations`:

```kotlin
    /**
     * Adopts a [SyncPayload] received from the paired device as this engine's new state, correcting
     * for the time elapsed between when the payload was captured ([SyncPayload.lastActionAtMillis])
     * and [now]. If that gap is large enough to have crossed a phase boundary, [remainingMillis] may
     * briefly read 0 until the next [tick] call, which flips phases correctly from there.
     */
    fun adoptRemote(payload: SyncPayload, now: Long): TimerState {
        focusMillis = payload.focusMinutes * 60_000L
        breakMillis = payload.breakMinutes * 60_000L
        phase = payload.phase
        currentPhaseDurationMillis = payload.totalMillis
        isRunning = payload.isRunning

        val elapsedSincePublish = (now - payload.lastActionAtMillis).coerceAtLeast(0)
        val remainingNow = (payload.remainingMillis - elapsedSincePublish).coerceAtLeast(0)
        if (isRunning) {
            endTimestamp = now + remainingNow
        } else {
            remainingWhenIdle = remainingNow
        }
        return currentState(now)
    }
```

- [ ] **Step 5: Add the `adoptRemote` tests (deferred from Task 1)**

In `core/src/test/kotlin/com/pomodoro/timer/core/TimerEngineTest.kt`, add these three tests at the end of the class, before the final closing `}`:

```kotlin

    @Test
    fun `adoptRemote applied immediately reproduces the remote running state`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.BREAK,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = breakMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_000_000L)

        assertEquals(TimerPhase.BREAK, state.phase)
        assertTrue(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        assertEquals(breakMillis, state.totalMillis)
    }

    @Test
    fun `adoptRemote accounts for elapsed time between publish and apply`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = true,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_010_000L)

        assertEquals(80_000L, state.remainingMillis)
        val laterTick = engine.tick(now = 1_015_000L)
        assertEquals(75_000L, laterTick.remainingMillis)
    }

    @Test
    fun `adoptRemote of a paused payload freezes at the adjusted remaining time`() {
        val engine = TimerEngine(focusMillis, breakMillis)
        val payload = SyncPayload(
            phase = TimerPhase.FOCUS,
            isRunning = false,
            remainingMillis = 90_000L,
            totalMillis = focusMillis,
            focusMinutes = 25,
            breakMinutes = 5,
            lastActionAtMillis = 1_000_000L
        )

        val state = engine.adoptRemote(payload, now = 1_005_000L)

        assertFalse(state.isRunning)
        assertEquals(90_000L, state.remainingMillis)
        val later = engine.currentState(now = 2_000_000L)
        assertEquals(90_000L, later.remainingMillis)
    }
```

- [ ] **Step 6: Run `:core` tests to verify the new engine tests fail without the coordinator tests yet**

Run: `./gradlew.bat :core:test`
Expected: `BUILD SUCCESSFUL`, 12 `TimerEngineTest` tests pass (9 original + 3 `adoptRemote`).

- [ ] **Step 7: Write the failing `SyncCoordinator` tests**

Create `core/src/test/kotlin/com/pomodoro/timer/core/SyncCoordinatorTest.kt`:

```kotlin
package com.pomodoro.timer.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSyncTransport : SyncTransport {
    val published = mutableListOf<SyncPayload>()
    private val remoteFlow = MutableSharedFlow<SyncPayload>(extraBufferCapacity = 8)

    override suspend fun publish(payload: SyncPayload) {
        published.add(payload)
    }

    override fun observeRemoteState(): Flow<SyncPayload> = remoteFlow

    suspend fun emitRemote(payload: SyncPayload) {
        remoteFlow.emit(payload)
    }
}

private fun payload(lastActionAtMillis: Long, isRunning: Boolean = true) = SyncPayload(
    phase = TimerPhase.FOCUS,
    isRunning = isRunning,
    remainingMillis = 100_000L,
    totalMillis = 1_500_000L,
    focusMinutes = 25,
    breakMinutes = 5,
    lastActionAtMillis = lastActionAtMillis
)

class SyncCoordinatorTest {

    @Test
    fun `isRemoteNewer is true when there is no local state yet`() {
        assertEquals(true, isRemoteNewer(local = null, remote = payload(1_000L)))
    }

    @Test
    fun `isRemoteNewer compares lastActionAtMillis`() {
        assertEquals(false, isRemoteNewer(local = payload(2_000L), remote = payload(1_000L)))
        assertEquals(true, isRemoteNewer(local = payload(1_000L), remote = payload(2_000L)))
        assertEquals(false, isRemoteNewer(local = payload(2_000L), remote = payload(2_000L)))
    }

    @Test
    fun `publishLocalState sends the payload through the transport`() = runTest {
        val transport = FakeSyncTransport()
        val coordinator = SyncCoordinator(transport, this) {}

        coordinator.publishLocalState(payload(5_000L))
        advanceUntilIdle()

        assertEquals(1, transport.published.size)
        assertEquals(5_000L, transport.published[0].lastActionAtMillis)
    }

    @Test
    fun `newer remote state triggers the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        coordinator.start()

        transport.emitRemote(payload(10_000L))
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(10_000L, received[0].lastActionAtMillis)
    }

    @Test
    fun `stale remote state does not trigger the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        coordinator.start()

        coordinator.publishLocalState(payload(20_000L))
        transport.emitRemote(payload(10_000L))
        advanceUntilIdle()

        assertEquals(0, received.size)
    }

    @Test
    fun `a self-echo of the just-published payload does not re-trigger the callback`() = runTest {
        val transport = FakeSyncTransport()
        val received = mutableListOf<SyncPayload>()
        val coordinator = SyncCoordinator(transport, this) { received.add(it) }
        coordinator.start()

        val mine = payload(30_000L)
        coordinator.publishLocalState(mine)
        transport.emitRemote(mine) // the transport echoing our own write back to us
        advanceUntilIdle()

        assertEquals(0, received.size)
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew.bat :core:test`
Expected: `BUILD SUCCESSFUL`, 12 `TimerEngineTest` + 3 `NotificationContentTest` + 6 `SyncCoordinatorTest` tests pass.

- [ ] **Step 9: Commit**

```bash
git add core
git commit -m "feat: add SyncPayload/SyncTransport/SyncCoordinator and TimerEngine.adoptRemote"
```

---

### Task 4: Wire phone-side sync (`PlayServicesSyncTransport`) into `:app`'s `TimerService`

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`

**Interfaces:**
- Consumes: `SyncPayload`, `SyncTransport`, `SyncCoordinator`, `TimerEngine.adoptRemote` (Task 3).
- Produces: `class PlayServicesSyncTransport(context: Context) : SyncTransport`. `TimerService` now publishes a `SyncPayload` on every local state change and adopts newer remote payloads automatically. Used by no later task in `:app`; the `:wear` side (Task 8) is the symmetric counterpart.

`DataClient` requires Google Play Services and a real (or emulator-with-Play-Services) device to exercise, so this task has no automated test — it's verified manually together with Task 11's pairing checklist. Keep the change small and behind the `SyncTransport` interface (already tested in Task 3) to minimize the untested surface.

- [ ] **Step 1: Add the Play Services Wearable dependency**

In `app/build.gradle.kts`, add these two lines to `dependencies { ... }` (after the `implementation(project(":core"))` line added in Task 2):

```kotlin
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
```

- [ ] **Step 2: Implement `PlayServicesSyncTransport`**

Create `app/src/main/java/com/pomodoro/timer/sync/PlayServicesSyncTransport.kt`:

```kotlin
package com.pomodoro.timer.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TIMER_STATE_PATH = "/pomodoro/timer_state"

/**
 * [SyncTransport] backed by the Wearable Data Layer API. A device's own writes typically also arrive
 * back through its own [DataClient.OnDataChangedListener] (a "self-echo"); [SyncCoordinator] already
 * ignores payloads that aren't newer than the last one it published, so no self-filtering is needed
 * here.
 */
class PlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(TIMER_STATE_PATH).apply {
            dataMap.putString("phase", payload.phase.name)
            dataMap.putBoolean("isRunning", payload.isRunning)
            dataMap.putLong("remainingMillis", payload.remainingMillis)
            dataMap.putLong("totalMillis", payload.totalMillis)
            dataMap.putInt("focusMinutes", payload.focusMinutes)
            dataMap.putInt("breakMinutes", payload.breakMinutes)
            dataMap.putLong("lastActionAtMillis", payload.lastActionAtMillis)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == TIMER_STATE_PATH) {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    trySend(
                        SyncPayload(
                            phase = TimerPhase.valueOf(map.getString("phase", TimerPhase.FOCUS.name)),
                            isRunning = map.getBoolean("isRunning"),
                            remainingMillis = map.getLong("remainingMillis"),
                            totalMillis = map.getLong("totalMillis"),
                            focusMinutes = map.getInt("focusMinutes"),
                            breakMinutes = map.getInt("breakMinutes"),
                            lastActionAtMillis = map.getLong("lastActionAtMillis")
                        )
                    )
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
}
```

- [ ] **Step 3: Wire `SyncCoordinator` into `TimerService`**

In `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`, add these imports after the existing `com.pomodoro.timer.core.*` imports:

```kotlin
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.sync.PlayServicesSyncTransport
```

Add a `syncCoordinator` field, two fields tracking the current settings (`TimerState` only carries the *current* phase's duration, not both focus and break, so the settings values need to be tracked separately for `SyncPayload`), and initialize the coordinator in `onCreate`. Replace:

```kotlin
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
```

with:

```kotlin
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
    private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        syncCoordinator = SyncCoordinator(
            transport = PlayServicesSyncTransport(applicationContext),
            scope = serviceScope,
            onRemoteStateIsNewer = { payload ->
                focusMinutes = payload.focusMinutes
                breakMinutes = payload.breakMinutes
                _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
                updateOngoingNotification(_state.value)
            }
        )
        syncCoordinator.start()
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
                lastActionAtMillis = actionAtMillis
            )
        )
    }
```

Now call `publishSyncState` after every local state change, and keep `focusMinutes`/`breakMinutes` up to date. Replace the `onStartCommand` body's `when (intent?.action) { ... }` block with:

```kotlin
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
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                publishSyncState(_state.value, now)
            }
            ACTION_UPDATE_DURATIONS -> {
                focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, TimerDefaults.DEFAULT_FOCUS_MINUTES)
                breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, TimerDefaults.DEFAULT_BREAK_MINUTES)
                engine.updateDurations(focusMinutes * 60_000L, breakMinutes * 60_000L)
                _state.value = engine.currentState(now)
                if (!_state.value.isRunning) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateOngoingNotification(_state.value)
                }
                publishSyncState(_state.value, now)
            }
        }
        return START_NOT_STICKY
```

- [ ] **Step 4: Build**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: sync phone timer state to the paired watch via Wearable DataClient"
```

---

### Task 5: Scaffold the `:wear` Wear OS module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/res/values/strings.xml`
- Create: `wear/src/main/res/values/colors.xml`
- Create: `wear/src/main/res/values/themes.xml`
- Create: `wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `wear/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `wear/src/main/res/drawable/ic_launcher_background.xml`
- Create: `wear/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`
- Create: `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`

**Interfaces:**
- Produces: `PomodoroWearTheme` composable (`com.pomodoro.timer.wear.theme.PomodoroWearTheme`), used by every later `:wear` screen task.

- [ ] **Step 1: Add the `:wear` module**

Append to `settings.gradle.kts`:

```kotlin
include(":wear")
```

- [ ] **Step 2: Write the `:wear` build file**

Create `wear/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pomodoro.timer.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pomodoro.timer.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear:wear-ongoing:1.0.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")
    implementation("androidx.wear.ambient:ambient:1.0.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 3: Write the manifest**

Create `wear/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.PomodoroWear">

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <uses-library
            android:name="com.google.android.wearable"
            android:required="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PomodoroWear">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".timer.WearTimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Pomodoro focus/break countdown timer" />
        </service>

    </application>
</manifest>
```

- [ ] **Step 4: Write minimal resources**

Create `wear/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Pomodoro</string>
</resources>
```

Create `wear/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="pomodoro_primary">#E85D4A</color>
    <color name="pomodoro_background">#FF000000</color>
    <color name="launcher_background">#E85D4A</color>
</resources>
```

Create `wear/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.PomodoroWear" parent="android:Theme.DeviceDefault" />
</resources>
```

- [ ] **Step 5: Write a text-only adaptive launcher icon**

Create `wear/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/launcher_background"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

Create `wear/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,27a27,27 0,1 0,0.001 0z"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="6"
        android:fillAlpha="0" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M52,32h4v24h16v4h-20z" />
</vector>
```

Create `wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Create `wear/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` (identical content):

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 6: Write the Compose for Wear OS theme**

Create `wear/src/main/java/com/pomodoro/timer/wear/theme/WearTheme.kt`:

```kotlin
package com.pomodoro.timer.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val PomodoroWearColors = Colors(
    primary = Color(0xFFE85D4A),
    primaryVariant = Color(0xFFB6432F),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF)
)

@Composable
fun PomodoroWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = PomodoroWearColors, content = content)
}
```

- [ ] **Step 7: Write a placeholder MainActivity**

Create `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`:

```kotlin
package com.pomodoro.timer.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Text
import com.pomodoro.timer.wear.theme.PomodoroWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PomodoroWearTheme {
                Text("Pomodoro", modifier = Modifier.fillMaxSize())
            }
        }
    }
}
```

- [ ] **Step 8: Build and verify**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `wear/build/outputs/apk/debug/wear-debug.apk` exists.

- [ ] **Step 9: Commit**

```bash
git add wear settings.gradle.kts
git commit -m "feat: scaffold :wear Wear OS module with placeholder MainActivity"
```

---

### Task 6: `WearTimerService` (Foreground Service reusing `:core`'s `TimerEngine`)

**Files:**
- Create: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`
- Create: `wear/src/androidTest/java/com/pomodoro/timer/wear/timer/WearTimerServiceTest.kt`

**Interfaces:**
- Consumes: `TimerEngine`, `TimerState`, `TimerPhase`, `NotificationContent`, `TimerDefaults` (`:core`, Task 1).
- Produces: `class WearTimerService : Service()` with companion `val state: StateFlow<TimerState>`, `const val ACTION_START`, `const val ACTION_PAUSE`, `const val ACTION_RESET`. Used by Task 7 (ongoing activity + vibration), Task 8 (sync), Task 9 (`WearTimerViewModel`).

This mirrors `:app`'s `TimerService` (same drift-free accuracy approach), simplified: the watch never edits durations locally (settings are phone-only), so there is no `ACTION_UPDATE_DURATIONS` — duration changes only ever arrive through `WearTimerService.adoptRemote` in Task 8. This task builds the service with hardcoded default durations and start/pause/reset only; Task 8 adds sync.

Per the design spec's testing plan and the lesson learned building the phone app: `WearTimerService.onBind()` returns `null` by design, so its instrumented test must start the service directly via `context.startService()`, **not** `androidx.test.rule.ServiceTestRule` (which blocks on `bindService()`/`onServiceConnected()` and never receives it for a null-binding service).

- [ ] **Step 1: Write the failing instrumented test**

Create `wear/src/androidTest/java/com/pomodoro/timer/wear/timer/WearTimerServiceTest.kt`:

```kotlin
package com.pomodoro.timer.wear.timer

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pomodoro.timer.core.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearTimerServiceTest {

    @Test
    fun startAction_setsRunningStateToTrue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START)

        context.startService(intent)

        val deadline = System.currentTimeMillis() + 3_000
        while (!WearTimerService.state.value.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertTrue(WearTimerService.state.value.isRunning)
        assertEquals(TimerPhase.FOCUS, WearTimerService.state.value.phase)
    }

    @Test
    fun pauseAction_stopsRunningWithoutLosingRemainingTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START))
        Thread.sleep(1_200)

        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_PAUSE))
        Thread.sleep(200)

        val remainingAfterPause = WearTimerService.state.value.remainingMillis
        assertTrue(!WearTimerService.state.value.isRunning)

        Thread.sleep(500)
        assertEquals(remainingAfterPause, WearTimerService.state.value.remainingMillis)
    }

    @Test
    fun resetAction_returnsToIdleFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_START))
        Thread.sleep(500)

        context.startService(Intent(context, WearTimerService::class.java).setAction(WearTimerService.ACTION_RESET))
        Thread.sleep(200)

        val state = WearTimerService.state.value
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertTrue(!state.isRunning)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :wear:assembleDebugAndroidTest`
Expected: FAIL to compile — `WearTimerService` is unresolved. (Running `connectedDebugAndroidTest` requires a connected device/emulator; if none is available, verify with this compile-only check now and defer the full run to Task 11's manual verification.)

- [ ] **Step 3: Implement `WearTimerService`**

Create `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`:

```kotlin
package com.pomodoro.timer.wear.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.timer.core.NotificationContent
import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerEngine
import com.pomodoro.timer.core.TimerState
import com.pomodoro.timer.wear.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerDefaults.DEFAULT_BREAK_MINUTES * 60_000L

        val engine = TimerEngine(defaultFocusMillis, defaultBreakMillis)

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                onLocalStateChanged(_state.value, now)
            }
        }
        return START_NOT_STICKY
    }

    /** Replaced in Task 8 to publish sync state; no-op until then. */
    private fun onLocalStateChanged(state: TimerState, actionAtMillis: Long) = Unit

    /** Replaced in Task 7 to also raise the ongoing-activity chip; plain notification until then. */
    private fun buildOngoingNotification(state: TimerState): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

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
                }
                updateOngoingNotification(newState)
            }
        }
    }

    /** Replaced in Task 7 to raise the session-end notification; no-op until then. */
    private fun onSessionEnded(newState: TimerState) = Unit

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pomodoro Timer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }
}
```

- [ ] **Step 4: Build and, if a device/emulator is connected, run the instrumented test**

Run: `./gradlew.bat :wear:connectedDebugAndroidTest --tests "com.pomodoro.timer.wear.timer.WearTimerServiceTest"`
Expected: PASS (3 tests), if a Wear OS emulator/device is connected. If none is available, run `./gradlew.bat :wear:assembleDebug` to confirm it compiles, and defer the instrumented run to Task 11.

- [ ] **Step 5: Commit**

```bash
git add wear
git commit -m "feat: add WearTimerService driving TimerEngine on the watch"
```

---

### Task 7: Ongoing Activity chip + session-end notification for `WearTimerService`

**Files:**
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`

**Interfaces:**
- Consumes: `WearTimerService` (Task 6).
- Produces: no new public API — the `buildOngoingNotification`/`onSessionEnded` function bodies (stubbed in Task 6) are filled in here to give the watch its Wear-specific UX (ongoing-activity chip on the watch face, session-end notification with sound + vibration). Nothing later depends on this beyond what Task 6 already exposed.

`androidx.wear.ongoing.OngoingActivity` and notification/vibration APIs are Android-framework/Play-Services surfaces with no meaningful JVM unit test; verify this task by building and by the manual pairing checklist in Task 11 (watch face chip, felt vibration, heard sound).

- [ ] **Step 1: Add a session-end notification channel alongside the ongoing one**

In `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`, add these imports:

```kotlin
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
```

Add a `SESSION_END_CHANNEL_ID` constant next to `CHANNEL_ID`. Replace:

```kotlin
        private const val CHANNEL_ID = "pomodoro_wear_timer_channel"
```

with:

```kotlin
        private const val CHANNEL_ID = "pomodoro_wear_timer_channel"
        private const val SESSION_END_CHANNEL_ID = "pomodoro_wear_session_end_channel"
```

Replace `createNotificationChannel` (which currently only creates the ongoing channel) with a version that creates both, and rename its call site:

```kotlin
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
```

In `onCreate`, replace the call `createNotificationChannel()` with `createNotificationChannels()`.

- [ ] **Step 2: Replace `buildOngoingNotification` to also publish an `OngoingActivity`**

Replace the `buildOngoingNotification` function body with a version that also builds and applies the `OngoingActivity`:

```kotlin
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
```

- [ ] **Step 3: Replace `onSessionEnded` to raise the session-end notification (sound + vibration via the channel)**

Replace the `onSessionEnded` function body:

```kotlin
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
```

- [ ] **Step 4: Build**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add wear
git commit -m "feat: add ongoing-activity chip and session-end notification to WearTimerService"
```

---

### Task 8: Wire watch-side sync (`WearPlayServicesSyncTransport`) into `WearTimerService`

**Files:**
- Create: `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`

**Interfaces:**
- Consumes: `SyncPayload`, `SyncTransport`, `SyncCoordinator`, `TimerEngine.adoptRemote` (`:core`, Task 3).
- Produces: `WearTimerService` now publishes and adopts `SyncPayload`s exactly like `:app`'s `TimerService` (Task 4) — the symmetric other half of the sync link. Used by Task 11's manual pairing verification.

Same untestable-without-a-paired-device caveat as Task 4: verified by building and by Task 11's manual checklist.

- [ ] **Step 1: Implement `WearPlayServicesSyncTransport`**

Create `wear/src/main/java/com/pomodoro/timer/wear/sync/WearPlayServicesSyncTransport.kt`:

```kotlin
package com.pomodoro.timer.wear.sync

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.core.SyncTransport
import com.pomodoro.timer.core.TimerPhase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TIMER_STATE_PATH = "/pomodoro/timer_state"

/** The watch-side twin of `:app`'s `PlayServicesSyncTransport` — same path, same wire format. */
class WearPlayServicesSyncTransport(context: Context) : SyncTransport {
    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(payload: SyncPayload) {
        val request = PutDataMapRequest.create(TIMER_STATE_PATH).apply {
            dataMap.putString("phase", payload.phase.name)
            dataMap.putBoolean("isRunning", payload.isRunning)
            dataMap.putLong("remainingMillis", payload.remainingMillis)
            dataMap.putLong("totalMillis", payload.totalMillis)
            dataMap.putInt("focusMinutes", payload.focusMinutes)
            dataMap.putInt("breakMinutes", payload.breakMinutes)
            dataMap.putLong("lastActionAtMillis", payload.lastActionAtMillis)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    override fun observeRemoteState(): Flow<SyncPayload> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == TIMER_STATE_PATH) {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    trySend(
                        SyncPayload(
                            phase = TimerPhase.valueOf(map.getString("phase", TimerPhase.FOCUS.name)),
                            isRunning = map.getBoolean("isRunning"),
                            remainingMillis = map.getLong("remainingMillis"),
                            totalMillis = map.getLong("totalMillis"),
                            focusMinutes = map.getInt("focusMinutes"),
                            breakMinutes = map.getInt("breakMinutes"),
                            lastActionAtMillis = map.getLong("lastActionAtMillis")
                        )
                    )
                }
            }
            events.release()
        }
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
}
```

- [ ] **Step 2: Wire `SyncCoordinator` into `WearTimerService`**

In `wear/src/main/java/com/pomodoro/timer/wear/timer/WearTimerService.kt`, add these imports:

```kotlin
import com.pomodoro.timer.core.SyncCoordinator
import com.pomodoro.timer.core.SyncPayload
import com.pomodoro.timer.wear.sync.WearPlayServicesSyncTransport
```

Add tracking fields and a `syncCoordinator`, mirroring `:app`'s `TimerService`. Replace:

```kotlin
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
```

with:

```kotlin
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var focusMinutes: Int = TimerDefaults.DEFAULT_FOCUS_MINUTES
    private var breakMinutes: Int = TimerDefaults.DEFAULT_BREAK_MINUTES
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        syncCoordinator = SyncCoordinator(
            transport = WearPlayServicesSyncTransport(applicationContext),
            scope = serviceScope,
            onRemoteStateIsNewer = { payload ->
                focusMinutes = payload.focusMinutes
                breakMinutes = payload.breakMinutes
                _state.value = engine.adoptRemote(payload, System.currentTimeMillis())
                updateOngoingNotification(_state.value)
            }
        )
        syncCoordinator.start()
    }
```

Now replace the `onLocalStateChanged` function body (currently a no-op placeholder from Task 6) to publish through the coordinator:

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
                lastActionAtMillis = actionAtMillis
            )
        )
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add wear
git commit -m "feat: sync watch timer state to the paired phone via Wearable DataClient"
```

---

### Task 9: `WearTimerUiState` + `WearTimerViewModel`

**Files:**
- Create: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt`
- Create: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt`
- Create: `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt`
- Create: `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerState`, `TimerPhase` (`:core`, Task 1), `WearTimerService.ACTION_START/ACTION_PAUSE/ACTION_RESET` (Task 6).
- Produces: `data class WearTimerUiState(phase, isRunning, remainingMillis, totalMillis)`; `fun buildWearUiState(timer: TimerState): WearTimerUiState`; `class WearTimerViewModel(timerState: StateFlow<TimerState>, onCommand: (String) -> Unit) : ViewModel()` exposing `val uiState: StateFlow<WearTimerUiState>`, `fun start()`, `fun pause()`, `fun reset()`, and nested `class Factory(context: Context) : ViewModelProvider.Factory`. Used by Task 10 (`WearTimerScreen`, `MainActivity`).

No settings here — unlike the phone's `TimerUiState`, there is no `focusMinutes`/`breakMinutes` or settings repository, since the watch never edits durations locally (spec decision: settings are phone-only).

- [ ] **Step 1: Write the failing tests**

Create `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerUiStateTest.kt`:

```kotlin
package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTimerUiStateTest {

    @Test
    fun `buildWearUiState mirrors the timer state`() {
        val timer = TimerState(
            phase = TimerPhase.BREAK,
            isRunning = true,
            remainingMillis = 120_000L,
            totalMillis = 300_000L
        )

        val uiState = buildWearUiState(timer)

        assertEquals(TimerPhase.BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
    }
}
```

Create `wear/src/test/java/com/pomodoro/timer/wear/ui/WearTimerViewModelTest.kt`:

```kotlin
package com.pomodoro.timer.wear.ui

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
        onCommand: (String) -> Unit = {}
    ): WearTimerViewModel =
        WearTimerViewModel(timerState, onCommand).also { createdViewModels.add(it) }

    @Test
    fun `uiState reflects the injected timer state`() = runTest(dispatcher) {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.BREAK, isRunning = true, remainingMillis = 120_000L, totalMillis = 300_000L)
        )
        val viewModel = newViewModel(timerState)

        val uiState = viewModel.uiState.first { it.phase == TimerPhase.BREAK }

        assertEquals(TimerPhase.BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
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

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.*"`
Expected: FAIL — `WearTimerUiState`/`buildWearUiState`/`WearTimerViewModel` unresolved.

- [ ] **Step 3: Implement `WearTimerUiState`**

Create `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerUiState.kt`:

```kotlin
package com.pomodoro.timer.wear.ui

import com.pomodoro.timer.core.TimerDefaults
import com.pomodoro.timer.core.TimerPhase
import com.pomodoro.timer.core.TimerState

data class WearTimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerDefaults.DEFAULT_FOCUS_MINUTES * 60_000L
)

fun buildWearUiState(timer: TimerState): WearTimerUiState = WearTimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis
)
```

- [ ] **Step 4: Implement `WearTimerViewModel`**

Create `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerViewModel.kt`:

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WearTimerViewModel(
    timerState: StateFlow<TimerState>,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<WearTimerUiState> =
        timerState.map(::buildWearUiState)
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
            return WearTimerViewModel(WearTimerService.state, onCommand) as T
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :wear:testDebugUnitTest --tests "com.pomodoro.timer.wear.ui.*"`
Expected: PASS (1 `WearTimerUiStateTest` + 2 `WearTimerViewModelTest` tests).

- [ ] **Step 6: Commit**

```bash
git add wear
git commit -m "feat: add WearTimerUiState mapper and WearTimerViewModel"
```

---

### Task 10: `WearTimerScreen` (Compose for Wear OS UI, ambient mode) + `MainActivity` wiring

**Files:**
- Create: `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`
- Create: `wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt`
- Modify: `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`

**Interfaces:**
- Consumes: `WearTimerUiState`, `WearTimerViewModel`, `WearTimerViewModel.Factory` (Task 9), `PomodoroWearTheme` (Task 5).
- Produces: `@Composable fun WearTimerScreen(uiState: WearTimerUiState, isAmbient: Boolean, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit)`. Final integration point for `:wear` — no later task depends on it.

- [ ] **Step 1: Write the failing Compose UI tests**

Create `wear/src/androidTest/java/com/pomodoro/timer/wear/ui/WearTimerScreenTest.kt`:

```kotlin
package com.pomodoro.timer.wear.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.wear.theme.PomodoroWearTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearTimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startButton_isShownWhenIdle_andInvokesCallback() {
        var startClicked = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L),
                    isAmbient = false,
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithTag("startButton").performClick()
        assertTrue(startClicked)
    }

    @Test
    fun pauseButton_isShownWhenRunning_andInvokesCallback() {
        var pauseClicked = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(isRunning = true, remainingMillis = 1_000_000L, totalMillis = 1_500_000L),
                    isAmbient = false,
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithTag("pauseButton").performClick()
        assertTrue(pauseClicked)
    }

    @Test
    fun resetButton_invokesCallback() {
        var resetClicked = false
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = { resetClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("resetButton").performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun remainingTime_isFormattedAsMinutesAndSeconds() {
        composeRule.setContent {
            PomodoroWearTheme {
                WearTimerScreen(
                    uiState = WearTimerUiState(remainingMillis = 754_000L),
                    isAmbient = false,
                    onStart = {},
                    onPause = {},
                    onReset = {}
                )
            }
        }

        composeRule.onNodeWithText("12:34").assertExists()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :wear:assembleDebugAndroidTest`
Expected: FAIL to compile — `WearTimerScreen` is unresolved. (Run `connectedDebugAndroidTest` if a device/emulator is connected; otherwise defer to Task 11.)

- [ ] **Step 3: Implement `WearTimerScreen`**

Create `wear/src/main/java/com/pomodoro/timer/wear/ui/WearTimerScreen.kt`:

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
            text = if (uiState.phase == com.pomodoro.timer.core.TimerPhase.FOCUS) "집중" else "휴식",
            style = MaterialTheme.typography.title3
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

fun formatWearMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 4: Wire ambient mode, the ViewModel, and the notification permission request into `MainActivity`**

Replace the contents of `wear/src/main/java/com/pomodoro/timer/wear/MainActivity.kt`:

```kotlin
package com.pomodoro.timer.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.Surface
import com.pomodoro.timer.wear.theme.PomodoroWearTheme
import com.pomodoro.timer.wear.ui.WearTimerScreen
import com.pomodoro.timer.wear.ui.WearTimerViewModel

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private val viewModel: WearTimerViewModel by viewModels {
        WearTimerViewModel.Factory(applicationContext)
    }

    private var isAmbient = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ambientObserver = AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    isAmbient.value = true
                }

                override fun onExitAmbient() {
                    isAmbient.value = false
                }
            }
        )
        lifecycle.addObserver(ambientObserver)

        setContent {
            PomodoroWearTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val ambient by isAmbient
                    WearTimerScreen(
                        uiState = uiState,
                        isAmbient = ambient,
                        onStart = viewModel::start,
                        onPause = viewModel::pause,
                        onReset = viewModel::reset
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Full build and full unit test suite**

Run: `./gradlew.bat :wear:assembleDebug :wear:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all `:wear` unit tests from Tasks 9 pass.

- [ ] **Step 6: Run instrumented tests if a device/emulator is connected**

Run: `./gradlew.bat :wear:connectedDebugAndroidTest`
Expected: PASS (`WearTimerServiceTest` + `WearTimerScreenTest`), if a Wear OS emulator/device is connected. Otherwise defer to Task 11.

- [ ] **Step 7: Commit**

```bash
git add wear
git commit -m "feat: wire ambient mode, ViewModel, and navigation into wear MainActivity"
```

---

### Task 11: Full build, full test suite, and manual pairing verification

**Files:** none (verification only).

- [ ] **Step 1: Build and test everything**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` across `:core`, `:app`, and `:wear`; every unit test from Tasks 1, 2, 3, 9 passes.

- [ ] **Step 2: Run instrumented tests on the phone**

Run: `./gradlew.bat :app:connectedDebugAndroidTest`
Expected: PASS, with the phone connected (as already verified for the existing phone app).

- [ ] **Step 3: Run instrumented tests on the watch**

Run: `./gradlew.bat :wear:connectedDebugAndroidTest`
Expected: PASS (`WearTimerServiceTest`, `WearTimerScreenTest`), with a Galaxy Watch 6 (or Wear OS emulator) connected.

- [ ] **Step 4: Install both apps**

Run: `./gradlew.bat :app:installDebug :wear:installDebug`

- [ ] **Step 5: Manually verify on the paired phone + Galaxy Watch 6**

Pair the watch with the phone through the Galaxy Wearable app first if not already paired, then:

1. Launch the watch app, grant the notification permission prompt. Confirm the timer shows 25:00 idle Focus, matching the phone's default (settings sync on first connect).
2. On the watch, tap Start — confirm the countdown begins and the watch face shows the ongoing-activity chip ("집중 중 · 24:59" or similar).
3. Confirm the phone's timer screen (open the phone app) shows the same running countdown within a couple of seconds, having adopted the watch's state.
4. On the phone, tap 일시정지 (Pause) — confirm the watch also pauses and freezes at the same remaining time within a couple of seconds.
5. Turn off Bluetooth on the phone (or move the watch out of range). On the watch, tap 리셋 (Reset). Wait 10+ seconds, then turn Bluetooth back on / bring the watch back in range. Confirm the phone adopts the watch's reset (idle Focus at the configured duration) once reconnected — this is the "가장 최근 조작이 이김" reconnect scenario from the spec.
6. On the phone, open 설정 (Settings) and lower focus duration to a low value (e.g. 1–2 minutes). Confirm the watch's idle duration updates to match once reconnected/synced.
7. Start a session on either device and let it run out. Confirm: the originating device fires its session-end notification/vibration and flips to Break, and the *other* device also flips to Break once its `SyncPayload` catches up.
8. On the watch, let the screen dim into ambient mode while a session is running. Confirm the ambient view shows the remaining time in the low-power style, and confirm it returns to the full circular UI on exiting ambient.
9. Disconnect the watch from the phone entirely (e.g. leave Bluetooth range) and confirm the watch timer keeps counting down accurately and independently — standalone operation with no phone nearby.

- [ ] **Step 6: Commit** (only if Step 5 surfaced fixes — otherwise nothing to commit)

If manual verification uncovers a bug, fix it, re-run the relevant automated tests, and commit with a message describing the fix, following the same investigate-before-fixing discipline used for the phone app's `TimerServiceTest`/`ServiceTestRule` bug.
