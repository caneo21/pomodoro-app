# Pomodoro Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working Android Pomodoro timer app (Kotlin + Jetpack Compose) with a Foreground-Service-backed timer that stays accurate in the background, adjustable focus/break durations, and start/end notifications.

**Architecture:** MVVM. A pure, Android-free `TimerEngine` computes phase/remaining-time from wall-clock timestamps (no drift). A `TimerService` (Foreground Service) owns the only `TimerEngine` instance and exposes a `StateFlow<TimerState>` via a companion object; it also owns notifications. `TimerViewModel` combines that state with `TimerSettingsRepository` (DataStore) into `TimerUiState` for two Compose screens (Timer, Settings) connected with Navigation-Compose.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), AGP 8.7.3, Gradle 8.10.2, minSdk 26 / targetSdk 35 / compileSdk 35, DataStore Preferences 1.1.1, Navigation-Compose 2.8.4, JUnit4 + kotlinx-coroutines-test + Compose UI testing + AndroidX Test (ServiceTestRule).

## Global Constraints

- Package/applicationId: `com.pomodoro.timer`. App display name: `Pomodoro`.
- minSdk 26, targetSdk 35, compileSdk 35 (from spec).
- Focus duration range: 5–60 minutes. Break duration range: 1–30 minutes. Defaults: 25 / 5 (from spec).
- Cycle is a simple FOCUS ↔ BREAK repeat — no "long break every 4 sessions" logic (from spec, explicitly out of scope).
- Timer accuracy must be drift-free across background/screen-off: always compute remaining time from an absolute end-timestamp (`System.currentTimeMillis()`), never by decrementing a counter on a fixed-interval tick (from spec).
- The timer must keep running when the app is backgrounded or the screen is off — implemented as a Foreground Service with an ongoing notification (from spec).
- On session end, fire a separate notification with sound + vibration (from spec).
- Android 13+ (API 33) requires runtime `POST_NOTIFICATIONS` permission request on first launch (from spec).
- No session history/statistics, no custom notification sound/theme — out of scope for this plan (from spec).
- Environment already prepared on this machine: Android Studio installed at `C:\Program Files\Android\Android Studio`; JDK 17 at `C:\Users\kk102\AppData\Local\JDK17` (env var `JAVA_HOME` set at user scope); Android SDK at `C:\Users\kk102\AppData\Local\Android\Sdk` (env var `ANDROID_HOME` set, `platform-tools` on `PATH`) with `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` installed; Gradle wrapper (8.10.2) already generated and committed at the project root. All `gradlew` commands below assume this environment and should be run from `D:\make_pomodoro_app`.

---

## File Structure

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/pomodoro/timer/
  MainActivity.kt
  PomodoroApp.kt
  timer/TimerPhase.kt
  timer/TimerState.kt
  timer/TimerEngine.kt
  timer/NotificationContent.kt
  timer/TimerService.kt
  settings/TimerSettings.kt
  settings/DataStoreModule.kt
  settings/TimerSettingsRepository.kt
  ui/TimerUiState.kt
  ui/TimerViewModel.kt
  ui/screens/TimerScreen.kt
  ui/screens/SettingsScreen.kt
  ui/theme/Color.kt
  ui/theme/Theme.kt
  ui/theme/Type.kt
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/themes.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/drawable/ic_launcher_background.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt
app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt
app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt
app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt
app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt
app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt
app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
```

---

### Task 1: App module scaffold

**Files:**
- Create: `app/build.gradle.kts`
- Modify: `build.gradle.kts:1` (root — add plugin declarations)
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/pomodoro/timer/MainActivity.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/theme/Type.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`

**Interfaces:**
- Produces: `PomodoroTheme` composable (`com.pomodoro.timer.ui.theme.PomodoroTheme`) used by every later screen task.

- [ ] **Step 1: Declare plugin versions in the root build file**

Replace the contents of `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [ ] **Step 2: Add the app module to settings.gradle.kts**

Append to `settings.gradle.kts`:

```kotlin
include(":app")
```

- [ ] **Step 3: Write the app module build file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pomodoro.timer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pomodoro.timer"
        minSdk = 26
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 4: Write the manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.Pomodoro">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Pomodoro">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".timer.TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Pomodoro focus/break countdown timer" />
        </service>

    </application>
</manifest>
```

- [ ] **Step 5: Write minimal resources**

Create `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Pomodoro</string>
</resources>
```

Create `app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="pomodoro_primary">#E85D4A</color>
    <color name="pomodoro_background">#FFFBF7</color>
    <color name="launcher_background">#E85D4A</color>
</resources>
```

Create `app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.Pomodoro" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: Write a text-only adaptive launcher icon**

Create `app/src/main/res/drawable/ic_launcher_background.xml`:

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

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:

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

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` (identical content):

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 7: Write the Compose theme files**

Create `app/src/main/java/com/pomodoro/timer/ui/theme/Color.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.ui.graphics.Color

val PomodoroPrimary = Color(0xFFE85D4A)
val PomodoroBackground = Color(0xFFFFFBF7)
val PomodoroOnPrimary = Color(0xFFFFFFFF)
```

Create `app/src/main/java/com/pomodoro/timer/ui/theme/Type.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PomodoroTypography = Typography(
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
)
```

Create `app/src/main/java/com/pomodoro/timer/ui/theme/Theme.kt`:

```kotlin
package com.pomodoro.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PomodoroPrimary,
    onPrimary = PomodoroOnPrimary,
    background = PomodoroBackground
)

private val DarkColors = darkColorScheme(
    primary = PomodoroPrimary,
    onPrimary = PomodoroOnPrimary
)

@Composable
fun PomodoroTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = PomodoroTypography,
        content = content
    )
}
```

- [ ] **Step 8: Write a placeholder MainActivity**

Create `app/src/main/java/com/pomodoro/timer/MainActivity.kt`:

```kotlin
package com.pomodoro.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.pomodoro.timer.ui.theme.PomodoroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PomodoroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Pomodoro")
                }
            }
        }
    }
}
```

- [ ] **Step 9: Build and verify**

Run: `./gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 10: Commit**

```bash
git add app build.gradle.kts settings.gradle.kts
git commit -m "feat: scaffold app module with placeholder MainActivity"
```

---

### Task 2: TimerEngine (pure countdown logic)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt`
- Create: `app/src/main/java/com/pomodoro/timer/timer/TimerState.kt`
- Create: `app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt`
- Test: `app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin, no Android dependency).
- Produces: `enum class TimerPhase { FOCUS, BREAK }`; `data class TimerState(val phase: TimerPhase, val isRunning: Boolean, val remainingMillis: Long, val totalMillis: Long)`; `class TimerEngine(focusMillis: Long, breakMillis: Long)` with methods `currentState(now: Long): TimerState`, `start(now: Long): TimerState`, `pause(now: Long): TimerState`, `reset(now: Long): TimerState`, `tick(now: Long): TimerState`, `updateDurations(focusMillis: Long, breakMillis: Long)`. Used by Task 5 (`TimerService`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt`:

```kotlin
package com.pomodoro.timer.timer

enum class TimerPhase {
    FOCUS,
    BREAK
}
```

Create `app/src/main/java/com/pomodoro/timer/timer/TimerState.kt`:

```kotlin
package com.pomodoro.timer.timer

data class TimerState(
    val phase: TimerPhase,
    val isRunning: Boolean,
    val remainingMillis: Long,
    val totalMillis: Long
)
```

Create `app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt`:

```kotlin
package com.pomodoro.timer.timer

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

        // time passes while paused; remaining must not change
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

        // tick 5 seconds after focus should have ended
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
        engine.tick(now = focusMillis) // flips to BREAK

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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.timer.TimerEngineTest"`
Expected: FAIL — `TimerEngine` is unresolved.

- [ ] **Step 3: Implement TimerEngine**

Create `app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt`:

```kotlin
package com.pomodoro.timer.timer

class TimerEngine(
    private var focusMillis: Long,
    private var breakMillis: Long
) {
    private var phase: TimerPhase = TimerPhase.FOCUS
    private var isRunning: Boolean = false
    private var endTimestamp: Long = 0L
    private var remainingWhenIdle: Long = focusMillis

    private fun durationFor(phase: TimerPhase): Long =
        if (phase == TimerPhase.FOCUS) focusMillis else breakMillis

    fun currentState(now: Long): TimerState {
        val remaining = if (isRunning) (endTimestamp - now).coerceAtLeast(0) else remainingWhenIdle
        return TimerState(
            phase = phase,
            isRunning = isRunning,
            remainingMillis = remaining,
            totalMillis = durationFor(phase)
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
        return currentState(now)
    }

    fun tick(now: Long): TimerState {
        if (!isRunning) return currentState(now)

        while (now >= endTimestamp) {
            phase = if (phase == TimerPhase.FOCUS) TimerPhase.BREAK else TimerPhase.FOCUS
            endTimestamp += durationFor(phase)
        }
        remainingWhenIdle = (endTimestamp - now).coerceAtLeast(0)
        return currentState(now)
    }

    fun updateDurations(focusMillis: Long, breakMillis: Long) {
        this.focusMillis = focusMillis
        this.breakMillis = breakMillis
        if (!isRunning) {
            remainingWhenIdle = durationFor(phase)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.timer.TimerEngineTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerPhase.kt app/src/main/java/com/pomodoro/timer/timer/TimerState.kt app/src/main/java/com/pomodoro/timer/timer/TimerEngine.kt app/src/test/java/com/pomodoro/timer/timer/TimerEngineTest.kt
git commit -m "feat: add drift-free TimerEngine with unit tests"
```

---

### Task 3: Notification content (pure text builder)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt`
- Test: `app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt`

**Interfaces:**
- Consumes: `TimerPhase` (Task 2).
- Produces: `object NotificationContent` with `fun ongoingTitle(phase: TimerPhase): String`, `fun ongoingText(remainingMillis: Long): String`, `fun sessionEndTitle(newPhase: TimerPhase): String`. Used by Task 5 (`TimerService`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt`:

```kotlin
package com.pomodoro.timer.timer

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

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.timer.NotificationContentTest"`
Expected: FAIL — `NotificationContent` is unresolved.

- [ ] **Step 3: Implement NotificationContent**

Create `app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt`:

```kotlin
package com.pomodoro.timer.timer

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.timer.NotificationContentTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/NotificationContent.kt app/src/test/java/com/pomodoro/timer/timer/NotificationContentTest.kt
git commit -m "feat: add pure notification text builder with unit tests"
```

---

### Task 4: TimerSettingsRepository (DataStore)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`
- Create: `app/src/main/java/com/pomodoro/timer/settings/DataStoreModule.kt`
- Create: `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`
- Test: `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `androidx.datastore.core.DataStore<Preferences>`.
- Produces: `data class TimerSettings(val focusMinutes: Int, val breakMinutes: Int)`; `class TimerSettingsRepository(dataStore: DataStore<Preferences>)` with `val settings: Flow<TimerSettings>`, `suspend fun updateFocusMinutes(minutes: Int)`, `suspend fun updateBreakMinutes(minutes: Int)`, and companion constants `DEFAULT_FOCUS_MINUTES = 25`, `DEFAULT_BREAK_MINUTES = 5`. `val Context.dataStore: DataStore<Preferences>` extension from `DataStoreModule.kt`. Used by Task 6 (`TimerViewModel`) and Task 9 (`MainActivity`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/pomodoro/timer/settings/TimerSettingsRepositoryTest.kt`:

```kotlin
package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimerSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.newFolder(), "test_settings.preferences_pb")
        return PreferenceDataStoreFactory.create { file }
    }

    @Test
    fun `settings default to 25 focus and 5 break minutes`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        val settings = repository.settings.first()

        assertEquals(25, settings.focusMinutes)
        assertEquals(5, settings.breakMinutes)
    }

    @Test
    fun `updateFocusMinutes persists and clamps to 5 to 60 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateFocusMinutes(45)
        assertEquals(45, repository.settings.first().focusMinutes)

        repository.updateFocusMinutes(200)
        assertEquals(60, repository.settings.first().focusMinutes)

        repository.updateFocusMinutes(0)
        assertEquals(5, repository.settings.first().focusMinutes)
    }

    @Test
    fun `updateBreakMinutes persists and clamps to 1 to 30 range`() = runTest {
        val repository = TimerSettingsRepository(newDataStore())

        repository.updateBreakMinutes(10)
        assertEquals(10, repository.settings.first().breakMinutes)

        repository.updateBreakMinutes(99)
        assertEquals(30, repository.settings.first().breakMinutes)

        repository.updateBreakMinutes(0)
        assertEquals(1, repository.settings.first().breakMinutes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: FAIL — `TimerSettingsRepository` is unresolved.

- [ ] **Step 3: Implement the repository**

Create `app/src/main/java/com/pomodoro/timer/settings/TimerSettings.kt`:

```kotlin
package com.pomodoro.timer.settings

data class TimerSettings(
    val focusMinutes: Int,
    val breakMinutes: Int
)
```

Create `app/src/main/java/com/pomodoro/timer/settings/DataStoreModule.kt`:

```kotlin
package com.pomodoro.timer.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "timer_settings")
```

Create `app/src/main/java/com/pomodoro/timer/settings/TimerSettingsRepository.kt`:

```kotlin
package com.pomodoro.timer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimerSettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES_KEY = intPreferencesKey("break_minutes")
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 60
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 30
    }

    val settings: Flow<TimerSettings> = dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES,
            breakMinutes = prefs[BREAK_MINUTES_KEY] ?: DEFAULT_BREAK_MINUTES
        )
    }

    suspend fun updateFocusMinutes(minutes: Int) {
        dataStore.edit { it[FOCUS_MINUTES_KEY] = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES) }
    }

    suspend fun updateBreakMinutes(minutes: Int) {
        dataStore.edit { it[BREAK_MINUTES_KEY] = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.settings.TimerSettingsRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/settings app/src/test/java/com/pomodoro/timer/settings
git commit -m "feat: add DataStore-backed TimerSettingsRepository with unit tests"
```

---

### Task 5: TimerService (Foreground Service)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt`

**Interfaces:**
- Consumes: `TimerEngine`, `TimerState`, `TimerPhase`, `NotificationContent` (Task 2, 3).
- Produces: `class TimerService : Service()` with companion `val state: StateFlow<TimerState>`, `const val ACTION_START`, `const val ACTION_PAUSE`, `const val ACTION_RESET`, `const val ACTION_UPDATE_DURATIONS`, `const val EXTRA_FOCUS_MINUTES`, `const val EXTRA_BREAK_MINUTES`. Used by Task 6 (`TimerViewModel`) and Task 9 (`MainActivity` manifest already declares the service in Task 1).

This task is verified with an instrumented test (real Android `Service`, cannot run as a plain JVM unit test) per the spec's testing plan.

- [ ] **Step 1: Write the failing instrumented test**

Create `app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt`:

```kotlin
package com.pomodoro.timer.timer

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun startAction_setsRunningStateToTrue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START)

        serviceRule.startService(intent)

        val deadline = System.currentTimeMillis() + 3_000
        while (!TimerService.state.value.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertTrue(TimerService.state.value.isRunning)
        assertEquals(TimerPhase.FOCUS, TimerService.state.value.phase)
    }

    @Test
    fun pauseAction_stopsRunningWithoutLosingRemainingTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(1_200)

        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_PAUSE))
        Thread.sleep(200)

        val remainingAfterPause = TimerService.state.value.remainingMillis
        assertTrue(!TimerService.state.value.isRunning)

        Thread.sleep(500)
        assertEquals(remainingAfterPause, TimerService.state.value.remainingMillis)
    }

    @Test
    fun resetAction_returnsToIdleFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_START))
        Thread.sleep(500)

        serviceRule.startService(Intent(context, TimerService::class.java).setAction(TimerService.ACTION_RESET))
        Thread.sleep(200)

        val state = TimerService.state.value
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertTrue(!state.isRunning)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.timer.TimerServiceTest"`
Expected: FAIL to compile — `TimerService` is unresolved. (Requires a connected device/emulator; if none is available, skip straight to Step 3 and rely on Step 4's manual verification instead.)

- [ ] **Step 3: Implement TimerService**

Create `app/src/main/java/com/pomodoro/timer/timer/TimerService.kt`:

```kotlin
package com.pomodoro.timer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.timer.MainActivity
import com.pomodoro.timer.settings.TimerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

        private const val CHANNEL_ID = "pomodoro_timer_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val SESSION_END_NOTIFICATION_ID = 2
        private const val TICK_INTERVAL_MILLIS = 1_000L

        private val defaultFocusMillis = TimerSettingsRepository.DEFAULT_FOCUS_MINUTES * 60_000L
        private val defaultBreakMillis = TimerSettingsRepository.DEFAULT_BREAK_MINUTES * 60_000L

        private val engine = TimerEngine(defaultFocusMillis, defaultBreakMillis)

        private val _state = MutableStateFlow(engine.currentState(System.currentTimeMillis()))
        val state: StateFlow<TimerState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                val previousPhase = _state.value.phase
                _state.value = engine.start(now)
                startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(_state.value))
                startTicker()
                if (previousPhase != _state.value.phase) {
                    // no-op: start() never changes phase, kept for symmetry with tick()
                }
            }
            ACTION_PAUSE -> {
                _state.value = engine.pause(now)
                updateOngoingNotification(_state.value)
            }
            ACTION_RESET -> {
                _state.value = engine.reset(now)
                stopTicker()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            ACTION_UPDATE_DURATIONS -> {
                val focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, TimerSettingsRepository.DEFAULT_FOCUS_MINUTES)
                val breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, TimerSettingsRepository.DEFAULT_BREAK_MINUTES)
                engine.updateDurations(focusMinutes * 60_000L, breakMinutes * 60_000L)
                _state.value = engine.currentState(now)
            }
        }
        return START_STICKY
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
                }
                updateOngoingNotification(newState)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun buildOngoingNotification(state: TimerState): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(NotificationContent.ongoingTitle(state.phase))
            .setContentText(NotificationContent.ongoingText(state.remainingMillis))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun updateOngoingNotification(state: TimerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(state))
    }

    private fun showSessionEndNotification(newPhase: TimerPhase) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
        super.onDestroy()
    }
}
```

- [ ] **Step 4: Run test to verify it passes, or verify manually if no device is connected**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.timer.TimerServiceTest"`
Expected: PASS (3 tests), if an emulator/device is connected.

If no device/emulator is available, instead run `./gradlew.bat assembleDebug` to confirm it compiles, and defer the instrumented run to Task 9's manual verification pass, where a real device is required anyway.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/timer/TimerService.kt app/src/androidTest/java/com/pomodoro/timer/timer/TimerServiceTest.kt
git commit -m "feat: add Foreground Service driving TimerEngine with ongoing and session-end notifications"
```

---

### Task 6: TimerUiState mapping + TimerViewModel

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`
- Test: `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerState`, `TimerPhase` (Task 2), `TimerSettings`, `TimerSettingsRepository` (Task 4), `TimerService.ACTION_START/ACTION_PAUSE/ACTION_RESET` (Task 5).
- Produces: `data class TimerUiState(...)`; `fun buildUiState(timer: TimerState, settings: TimerSettings): TimerUiState`; `class TimerViewModel(timerState: StateFlow<TimerState>, settingsRepository: TimerSettingsRepository, onCommand: (String) -> Unit) : ViewModel()` exposing `val uiState: StateFlow<TimerUiState>`, `fun start()`, `fun pause()`, `fun reset()`, `fun updateFocusMinutes(minutes: Int)`, `fun updateBreakMinutes(minutes: Int)`, and nested `class Factory(context: Context) : ViewModelProvider.Factory`. Used by Task 7, 8, 9 (screens and `MainActivity`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt`:

```kotlin
package com.pomodoro.timer.ui

import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerUiStateTest {

    @Test
    fun `buildUiState combines timer state and settings`() {
        val timer = TimerState(
            phase = TimerPhase.BREAK,
            isRunning = true,
            remainingMillis = 120_000L,
            totalMillis = 300_000L
        )
        val settings = TimerSettings(focusMinutes = 30, breakMinutes = 10)

        val uiState = buildUiState(timer, settings)

        assertEquals(TimerPhase.BREAK, uiState.phase)
        assertEquals(true, uiState.isRunning)
        assertEquals(120_000L, uiState.remainingMillis)
        assertEquals(300_000L, uiState.totalMillis)
        assertEquals(30, uiState.focusMinutes)
        assertEquals(10, uiState.breakMinutes)
    }
}
```

Create `app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt`:

```kotlin
package com.pomodoro.timer.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newRepository(): TimerSettingsRepository {
        val file = File(tempFolder.newFolder(), "settings.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }
        return TimerSettingsRepository(dataStore)
    }

    @Test
    fun `uiState reflects the injected timer state and settings`() = runTest {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val viewModel = TimerViewModel(timerState, newRepository(), onCommand = {})

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TimerPhase.FOCUS, viewModel.uiState.value.phase)
        assertEquals(1_500_000L, viewModel.uiState.value.remainingMillis)
        assertEquals(25, viewModel.uiState.value.focusMinutes)
    }

    @Test
    fun `start pause and reset forward the matching TimerService actions`() = runTest {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val sentActions = mutableListOf<String>()
        val viewModel = TimerViewModel(timerState, newRepository(), onCommand = { sentActions.add(it) })

        viewModel.start()
        viewModel.pause()
        viewModel.reset()

        assertEquals(
            listOf(TimerService.ACTION_START, TimerService.ACTION_PAUSE, TimerService.ACTION_RESET),
            sentActions
        )
    }

    @Test
    fun `updateFocusMinutes persists through the repository`() = runTest {
        val timerState = MutableStateFlow(
            TimerState(TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L)
        )
        val repository = newRepository()
        val viewModel = TimerViewModel(timerState, repository, onCommand = {})

        viewModel.updateFocusMinutes(40)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(40, viewModel.uiState.value.focusMinutes)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest" --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: FAIL — `TimerUiState`, `buildUiState`, `TimerViewModel` are unresolved.

- [ ] **Step 3: Implement TimerUiState and TimerViewModel**

Create `app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt`:

```kotlin
package com.pomodoro.timer.ui

import com.pomodoro.timer.settings.TimerSettings
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.timer.TimerState

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.FOCUS,
    val isRunning: Boolean = false,
    val remainingMillis: Long = TimerSettingsRepository.DEFAULT_FOCUS_MINUTES * 60_000L,
    val totalMillis: Long = TimerSettingsRepository.DEFAULT_FOCUS_MINUTES * 60_000L,
    val focusMinutes: Int = TimerSettingsRepository.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = TimerSettingsRepository.DEFAULT_BREAK_MINUTES
)

fun buildUiState(timer: TimerState, settings: TimerSettings): TimerUiState = TimerUiState(
    phase = timer.phase,
    isRunning = timer.isRunning,
    remainingMillis = timer.remainingMillis,
    totalMillis = timer.totalMillis,
    focusMinutes = settings.focusMinutes,
    breakMinutes = settings.breakMinutes
)
```

Create `app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt`:

```kotlin
package com.pomodoro.timer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pomodoro.timer.settings.TimerSettingsRepository
import com.pomodoro.timer.settings.dataStore
import com.pomodoro.timer.timer.TimerService
import com.pomodoro.timer.timer.TimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    timerState: StateFlow<TimerState>,
    private val settingsRepository: TimerSettingsRepository,
    private val onCommand: (String) -> Unit
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> =
        combine(timerState, settingsRepository.settings, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun start() = onCommand(TimerService.ACTION_START)
    fun pause() = onCommand(TimerService.ACTION_PAUSE)
    fun reset() = onCommand(TimerService.ACTION_RESET)

    fun updateFocusMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateFocusMinutes(minutes) }
    }

    fun updateBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.updateBreakMinutes(minutes) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = TimerSettingsRepository(appContext.dataStore)
            val onCommand: (String) -> Unit = { action ->
                val intent = Intent(appContext, TimerService::class.java).setAction(action)
                ContextCompat.startForegroundService(appContext, intent)
            }
            return TimerViewModel(TimerService.state, repository, onCommand) as T
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.pomodoro.timer.ui.TimerUiStateTest" --tests "com.pomodoro.timer.ui.TimerViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/TimerUiState.kt app/src/main/java/com/pomodoro/timer/ui/TimerViewModel.kt app/src/test/java/com/pomodoro/timer/ui/TimerUiStateTest.kt app/src/test/java/com/pomodoro/timer/ui/TimerViewModelTest.kt
git commit -m "feat: add TimerUiState mapper and TimerViewModel with unit tests"
```

---

### Task 7: Timer screen (Compose UI)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`

**Interfaces:**
- Consumes: `TimerUiState`, `TimerPhase` (Task 6, 2), `PomodoroTheme` (Task 1).
- Produces: `@Composable fun TimerScreen(uiState: TimerUiState, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier)`; `fun formatMillis(millis: Long): String`. Used by Task 9 (`PomodoroApp` navigation).

- [ ] **Step 1: Write the failing Compose UI test**

Create `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt`:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.timer.TimerPhase
import com.pomodoro.timer.ui.TimerUiState
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startButton_isShownWhenIdle_andInvokesCallback() {
        var startClicked = false
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(phase = TimerPhase.FOCUS, isRunning = false, remainingMillis = 1_500_000L, totalMillis = 1_500_000L),
                    onStart = { startClicked = true },
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
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
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(phase = TimerPhase.FOCUS, isRunning = true, remainingMillis = 1_000_000L, totalMillis = 1_500_000L),
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onReset = {},
                    onSettingsClick = {}
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
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(),
                    onStart = {},
                    onPause = {},
                    onReset = { resetClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("resetButton").performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun remainingTime_isFormattedAsMinutesAndSeconds() {
        composeRule.setContent {
            PomodoroTheme {
                TimerScreen(
                    uiState = TimerUiState(remainingMillis = 754_000L),
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("12:34").assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.ui.screens.TimerScreenTest"`
Expected: FAIL to compile — `TimerScreen` is unresolved. (Requires a connected device/emulator; if unavailable, proceed to Step 3 and verify with `assembleDebug` instead, deferring the real UI test run to Task 9's manual pass.)

- [ ] **Step 3: Implement TimerScreen**

Create `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt`:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.pomodoro.timer.timer.TimerPhase
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
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (uiState.phase == TimerPhase.FOCUS) "집중" else "휴식",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(24.dp))

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

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.ui.screens.TimerScreenTest"`
Expected: PASS (4 tests), if an emulator/device is connected. Otherwise run `./gradlew.bat assembleDebug` to confirm it compiles, and defer to Task 9's manual pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt
git commit -m "feat: add TimerScreen composable with UI tests"
```

---

### Task 8: Settings screen (Compose UI)

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `PomodoroTheme` (Task 1).
- Produces: `@Composable fun SettingsScreen(focusMinutes: Int, breakMinutes: Int, onFocusMinutesChange: (Int) -> Unit, onBreakMinutesChange: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)`. Used by Task 9 (`PomodoroApp` navigation).

- [ ] **Step 1: Write the failing Compose UI test**

Create `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt`:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.pomodoro.timer.ui.theme.PomodoroTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onBack = { backClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("backButton").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun focusSlider_and_breakSlider_areDisplayed() {
        composeRule.setContent {
            PomodoroTheme {
                SettingsScreen(
                    focusMinutes = 25,
                    breakMinutes = 5,
                    onFocusMinutesChange = {},
                    onBreakMinutesChange = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("focusSlider").assertExists()
        composeRule.onNodeWithTag("breakSlider").assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.ui.screens.SettingsScreenTest"`
Expected: FAIL to compile — `SettingsScreen` is unresolved. (If no device/emulator is available, proceed to Step 3 and verify with `assembleDebug`, deferring to Task 9's manual pass.)

- [ ] **Step 3: Implement SettingsScreen**

Create `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt`:

```kotlin
package com.pomodoro.timer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.settings.TimerSettingsRepository

@Composable
fun SettingsScreen(
    focusMinutes: Int,
    breakMinutes: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onBreakMinutesChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("집중 시간: ${focusMinutes}분")
        Slider(
            value = focusMinutes.toFloat(),
            onValueChange = { onFocusMinutesChange(it.toInt()) },
            valueRange = TimerSettingsRepository.MIN_FOCUS_MINUTES.toFloat()..TimerSettingsRepository.MAX_FOCUS_MINUTES.toFloat(),
            steps = TimerSettingsRepository.MAX_FOCUS_MINUTES - TimerSettingsRepository.MIN_FOCUS_MINUTES - 1,
            modifier = Modifier.testTag("focusSlider")
        )

        Spacer(Modifier.height(24.dp))
        Text("휴식 시간: ${breakMinutes}분")
        Slider(
            value = breakMinutes.toFloat(),
            onValueChange = { onBreakMinutesChange(it.toInt()) },
            valueRange = TimerSettingsRepository.MIN_BREAK_MINUTES.toFloat()..TimerSettingsRepository.MAX_BREAK_MINUTES.toFloat(),
            steps = TimerSettingsRepository.MAX_BREAK_MINUTES - TimerSettingsRepository.MIN_BREAK_MINUTES - 1,
            modifier = Modifier.testTag("breakSlider")
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.testTag("backButton")) {
            Text("뒤로")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat connectedDebugAndroidTest --tests "com.pomodoro.timer.ui.screens.SettingsScreenTest"`
Expected: PASS (2 tests), if an emulator/device is connected. Otherwise run `./gradlew.bat assembleDebug` to confirm it compiles, and defer to Task 9's manual pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt
git commit -m "feat: add SettingsScreen composable with UI tests"
```

---

### Task 9: Navigation, MainActivity wiring, and manual verification

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/MainActivity.kt` (replace the Task 1 placeholder body)

**Interfaces:**
- Consumes: `TimerViewModel`, `TimerViewModel.Factory` (Task 6), `TimerScreen` (Task 7), `SettingsScreen` (Task 8), `PomodoroTheme` (Task 1).
- Produces: `@Composable fun PomodoroApp(viewModel: TimerViewModel)`. Final integration point — no later task depends on it.

- [ ] **Step 1: Write the navigation graph**

Create `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt`:

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
                onFocusMinutesChange = viewModel::updateFocusMinutes,
                onBreakMinutesChange = viewModel::updateBreakMinutes,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 2: Wire MainActivity: permission request, ViewModel, navigation**

Replace the contents of `app/src/main/java/com/pomodoro/timer/MainActivity.kt`:

```kotlin
package com.pomodoro.timer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.theme.PomodoroTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PomodoroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PomodoroApp(viewModel)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Full build and full unit test suite**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all unit tests from Tasks 2, 3, 4, 6 pass.

- [ ] **Step 4: Install and manually verify on a device or emulator**

Run: `./gradlew.bat installDebug`

Then, on the device:
1. Launch the app, grant the notification permission prompt.
2. Tap 시작 (Start) — the circular countdown should begin from 25:00 and the status bar should show an ongoing "집중 중" notification with the remaining time.
3. Turn the screen off (or switch to another app) for at least 1 minute, then return — the remaining time shown must match real elapsed time (drift-free), confirming the spec's core background-accuracy requirement.
4. Tap 설정 (Settings), change 집중 시간 to a low value (e.g., 5분) via the slider, tap 뒤로 (Back).
5. Tap 시작 again and let the focus session run out — confirm a session-end notification fires with sound + vibration and the screen automatically shows "휴식" with the break duration.
6. Tap 일시정지 (Pause) mid-session, wait 10 seconds, confirm the remaining time did not change, then resume and confirm it continues correctly.
7. Tap 리셋 (Reset) — confirm it returns to an idle 집중 state at the configured focus duration and the ongoing notification is cleared.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pomodoro/timer/PomodoroApp.kt app/src/main/java/com/pomodoro/timer/MainActivity.kt
git commit -m "feat: wire navigation, ViewModel, and notification permission request into MainActivity"
```

---

## Self-Review Notes

- **Spec coverage:** Main screen (Task 7) ✓, Settings screen with 5–60 / 1–30 minute ranges (Task 4, 8) ✓, Foreground Service background accuracy via end-timestamp recomputation (Task 2, 5) ✓, ongoing + session-end notifications (Task 3, 5) ✓, POST_NOTIFICATIONS runtime request (Task 9) ✓, DataStore persistence (Task 4) ✓, simple FOCUS↔BREAK repeat with no long-break rule (Task 2 — engine only ever flips between two phases) ✓, unit tests for engine/settings/viewmodel and Compose UI tests for buttons (Tasks 2–8) ✓, manual background-accuracy + notification test (Task 9 Step 4) ✓.
- **Type consistency checked:** `TimerState`, `TimerPhase`, `TimerSettings`, `TimerUiState` field names and the `TimerService.ACTION_*` / `EXTRA_*` constants are used identically across Tasks 2, 3, 4, 5, 6, 7, 8, 9.
