# Release System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pushing a `vX.Y.Z` tag builds, signs, and publishes phone/watch APKs and Windows MSIX packages to a GitHub Release, and the phone and Windows apps show a dismissible "new version" notice that opens the release page.

**Architecture:** Gradle derives `versionName`/`versionCode` from a `-PreleaseVersion` property and resolves release signing from `keystore.properties` or env vars (both `:app` and `:wear`, same key). Update-check logic (version parsing, 24h interval, per-version dismiss, coordinator over a store + fetcher interface) lives in the pure `core` (Kotlin) and `PomodoroWindows.Core` (C#) modules and is unit-tested there; each app only supplies an HTTP fetcher, a persistent store, and UI. A new `.github/workflows/release.yml` runs tests, builds signed artifacts on Ubuntu + Windows runners, and publishes with `gh release create`.

**Tech Stack:** Gradle Kotlin DSL (AGP 8.7.3, Kotlin 2.0.21), Jetpack Compose + DataStore, `HttpURLConnection` + `org.json`; .NET 8 WinUI 3 (Windows App SDK 1.8) + `HttpClient` + `System.Text.Json`, xUnit; GitHub Actions, PowerShell.

**Spec:** `docs/superpowers/specs/2026-11-27-release-system-design.md`

**Branch:** start from `main` on a new branch `feat/release-system`.

## Global Constraints

- Tag format `vMAJOR.MINOR.PATCH`; minor and patch 0–99. Anything else fails the release workflow and the Gradle build.
- `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`; without `-PreleaseVersion`: `versionName = "0.0.0-dev"`, `versionCode = 1`.
- `:app` and `:wear` release builds are signed with the **same** keystore. A release build with no signing config fails; it never produces an unsigned APK.
- Signing env vars: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. GitHub Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `WINDOWS_CERT_PFX_BASE64`, `WINDOWS_CERT_PASSWORD`.
- Windows cert subject `CN=PomodoroWindowsDev` (must equal manifest `Publisher`). MSIX version `MAJOR.MINOR.PATCH.0`.
- Latest-release endpoint: `https://api.github.com/repos/caneo21/pomodoro-app/releases/latest`; read `tag_name` and `html_url`. Header `User-Agent: PomodoroApp-UpdateCheck`, `Accept: application/vnd.github+json`. Timeout 10 s.
- Check interval 24 h (`86_400_000` ms). Any fetch failure is silent.
- No new runtime dependencies (test-only `org.json:json` is allowed).
- Debug builds (Android `BuildConfig.DEBUG`, Windows `DEBUG` symbol) never check for updates.
- UI copy (Korean, exact): banner `새 버전 {tag}이 있어요`, buttons `다운로드` / `닫기`, settings `현재 버전 {version}`.
- Release asset names: `pomodoro-phone-{v}.apk`, `pomodoro-watch-{v}.apk`, `PomodoroWindows-{v}-x64.msix`, `PomodoroWindows-{v}-arm64.msix`, `PomodoroWindows.cer`, `Install.ps1`, `Install.cmd` (`{v}` = `1.2.0`, no `v`).
- Secrets, keystore, and PFX are never committed or echoed. Keep `keystore/*.jks` git-ignored; add `keystore/*.pfx` and `keystore/*.cer` to `.gitignore`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- **GitHub answers with a non-200 (403 rate limit, 404 when no release exists yet, 5xx) or non-JSON (captive-portal HTML)** → fetcher returns null, no banner, no crash, `lastCheckedAt` not advanced. Tests: Task 3 `parseLatestRelease` cases, Task 5 `LatestReleaseParser` cases, coordinator "fetch failure" tests in Tasks 2 and 5.
- **Odd tags (`v1.2.0-beta`, `1.2`, `release-5`, empty)** → treated as "not newer", never shown. Tests: `SemVer.parse` cases in Tasks 2 and 5.
- **Installed version is newer than or equal to the latest release** (owner's local build ahead, or user just updated while an old cached result exists) → no banner. Tests: `isNewer` + coordinator "cached tag equal to installed" in Tasks 2 and 5.
- **Device clock moved backwards (stored `lastCheckedAt` is in the future)** → check again instead of never checking. Tests: `shouldCheck` in Tasks 2 and 5.
- **User dismissed v1.1.0, then v1.2.0 is released** → banner shows again for v1.2.0. Tests: `shouldShow` + coordinator test in Tasks 2 and 5.

---

### Task 1: Tag-driven version and shared release signing (Gradle)

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts:9-46` (replace local keystore loading/signing with root-provided values; set version from root)
- Modify: `wear/build.gradle.kts:7-25` (same)
- Modify: `.gitignore` (add `keystore/*.pfx`, `keystore/*.cer`)

**Interfaces:**
- Produces (root `extra`, read by modules as `rootProject.extra[...]`):
  - `"appVersionName"`: `String`
  - `"appVersionCode"`: `Int`
  - `"releaseSigning"`: `Map<String, String>?` with keys `storeFile` (absolute path), `storePassword`, `keyAlias`, `keyPassword`; null when neither `keystore.properties` nor all four env vars are present.
- Produces task `:printReleaseVersion` printing exactly `versionName=<name> versionCode=<code>`.

- [ ] **Step 1: Implement root version + signing resolution in `build.gradle.kts`**
  - Read `providers.gradleProperty("releaseVersion")`. If present, it must match `^(\d+)\.(\d{1,2})\.(\d{1,2})$`, else throw `GradleException("releaseVersion must look like 1.2.3, got '<value>'")`.
  - Signing: prefer `keystore.properties` (existing keys `storeFile` relative to root, `storePassword`, `keyAlias`, `keyPassword`); else the four `ANDROID_*` env vars; else null.
  - `gradle.taskGraph.whenReady`: if any scheduled task path ends with `assembleRelease`, `bundleRelease`, or `packageRelease` and `releaseSigning == null`, throw `GradleException("Release signing is not configured: add keystore.properties or ANDROID_KEYSTORE_* env vars")`.
  - Register `printReleaseVersion`.

- [ ] **Step 2: Use root values in `:app` and `:wear`**
  Both modules: `versionCode`/`versionName` from root extras; `signingConfigs.create("release")` only when `releaseSigning != null`, and `buildTypes.release.signingConfig` set to it. In `:app` also set `buildFeatures.buildConfig = true` (needed by Task 4). Remove `:app`'s own `Properties()` loading.

- [ ] **Step 3: Verify version derivation**
  Run: `./gradlew -q printReleaseVersion -PreleaseVersion=1.2.3`
  Expected: `versionName=1.2.3 versionCode=10203`
  Run: `./gradlew -q printReleaseVersion`
  Expected: `versionName=0.0.0-dev versionCode=1`
  Run: `./gradlew -q printReleaseVersion -PreleaseVersion=1.2`
  Expected: build fails with `releaseVersion must look like 1.2.3, got '1.2'`

- [ ] **Step 4: Verify signing (local, with `keystore.properties` present)**
  Run: `./gradlew :app:assembleRelease :wear:assembleRelease -PreleaseVersion=1.0.0`
  Then: `"$ANDROID_HOME/build-tools/<latest>/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk` and the same for `wear/build/outputs/apk/release/wear-release.apk`.
  Expected: both succeed and print the **same** `Signer #1 certificate SHA-256 digest`.

- [ ] **Step 5: Verify the missing-signing failure**
  Temporarily rename `keystore.properties`, run `./gradlew :app:assembleRelease`, expect `Release signing is not configured...`; rename it back. Also confirm `./gradlew :app:assembleDebug` still succeeds without it.

- [ ] **Step 6: Run existing tests**
  Run: `./gradlew :core:test :app:testDebugUnitTest :wear:testDebugUnitTest`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit** — `build: derive app version from release tag and sign watch with the phone key`

---

### Task 2: Update-check logic in `core` (Kotlin)

**Files:**
- Create: `core/src/main/kotlin/com/pomodoro/timer/core/UpdateCheck.kt`
- Test: `core/src/test/kotlin/com/pomodoro/timer/core/UpdateCheckTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
      companion object { fun parse(text: String?): SemVer? }  // optional leading "v", exactly 3 numeric parts, nothing else
  }
  data class LatestRelease(val tag: String, val url: String)
  data class StoredUpdateInfo(
      val lastCheckedAtMillis: Long = 0L,
      val latestTag: String? = null,
      val latestUrl: String? = null,
      val dismissedTag: String? = null,
  )
  object UpdatePolicy {
      const val CHECK_INTERVAL_MILLIS: Long = 86_400_000L
      fun isNewer(latestTag: String?, installedVersion: String): Boolean
      fun shouldCheck(lastCheckedAtMillis: Long, nowMillis: Long): Boolean
      fun shouldShow(latestTag: String?, installedVersion: String, dismissedTag: String?): Boolean
  }
  interface UpdateInfoStore {
      suspend fun load(): StoredUpdateInfo
      suspend fun saveCheckResult(nowMillis: Long, release: LatestRelease)
      suspend fun saveDismissed(tag: String)
  }
  fun interface ReleaseFetcher { suspend fun fetchLatest(): LatestRelease? }
  class UpdateCoordinator(
      private val store: UpdateInfoStore,
      private val fetcher: ReleaseFetcher,
      private val installedVersion: String,
      private val nowMillis: () -> Long,
  ) {
      suspend fun refresh(): LatestRelease?   // the release to show, or null
      suspend fun dismiss(tag: String)
  }
  ```

- [ ] **Step 1: Write the failing tests** (JUnit 4 + `runTest`, in-memory fake store and fake fetcher that counts calls)
  - `parse accepts v-prefixed and bare versions`: `parse("v1.2.3") == SemVer(1,2,3)`, `parse("10.0.7") == SemVer(10,0,7)`
  - `parse rejects malformed tags`: null for `"v1.2"`, `"1.2.3.4"`, `"v1.2.0-beta"`, `"release-5"`, `""`, `null`, `"0.0.0-dev"`
  - `isNewer compares numerically`: `isNewer("v1.10.0","1.9.0")` true; `isNewer("v1.2.0","1.2.0")` false; `isNewer("v1.1.0","1.2.0")` false
  - `isNewer is false when either side is unparseable`: `isNewer("v2.0.0","0.0.0-dev")` false; `isNewer("garbage","1.0.0")` false
  - `shouldCheck after 24 hours`: `(0, 1)` true; `(1_000, 1_000 + 86_399_999)` false; `(1_000, 1_000 + 86_400_000)` true
  - `shouldCheck when clock moved backwards`: `(5_000_000, 1_000)` true
  - `shouldShow hides dismissed version only`: `shouldShow("v1.1.0","1.0.0","v1.1.0")` false; `shouldShow("v1.2.0","1.0.0","v1.1.0")` true
  - `refresh fetches when due and returns newer release`: store empty, fetcher returns `LatestRelease("v1.1.0","https://x/1.1.0")`, installed `"1.0.0"` → returns that release; store's `lastCheckedAtMillis == now`
  - `refresh skips fetch within interval and uses cached result`: store `(now - 1000, "v1.1.0", "u", null)` → fetcher call count 0, returns `LatestRelease("v1.1.0","u")`
  - `refresh on fetch failure keeps cache and does not advance lastChecked`: fetcher returns null (and separately: throws `IOException`) → returns cached banner (or null if no cache); `lastCheckedAtMillis` unchanged
  - `refresh returns null when cached tag equals installed version`: cache `"v1.1.0"`, installed `"1.1.0"` → null
  - `refresh never fetches for dev version`: installed `"0.0.0-dev"` → null, fetcher call count 0
  - `dismiss hides that version; a newer one shows again`: after `dismiss("v1.1.0")` refresh → null; then cache updated to `"v1.2.0"` (fetch due) → returns v1.2.0

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./gradlew :core:test --tests "com.pomodoro.timer.core.UpdateCheckTest"`
  Expected: FAIL (unresolved references)

- [ ] **Step 3: Implement `UpdateCheck.kt`** per Interfaces. `refresh()` catches exceptions from the fetcher except `CancellationException` (rethrow).

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./gradlew :core:test`
  Expected: PASS

- [ ] **Step 5: Commit** — `feat(core): add update-check policy and coordinator`

---

### Task 3: Android fetcher and DataStore-backed update store

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/update/GitHubReleaseFetcher.kt`
- Create: `app/src/main/java/com/pomodoro/timer/update/DataStoreUpdateInfoStore.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `<uses-permission android:name="android.permission.INTERNET" />`)
- Modify: `app/build.gradle.kts` dependencies (add `testImplementation("org.json:json:20240303")`)
- Test: `app/src/test/java/com/pomodoro/timer/update/GitHubReleaseFetcherTest.kt`
- Test: `app/src/test/java/com/pomodoro/timer/update/DataStoreUpdateInfoStoreTest.kt`

**Interfaces:**
- Consumes: `LatestRelease`, `StoredUpdateInfo`, `UpdateInfoStore`, `ReleaseFetcher` (Task 2)
- Produces:
  ```kotlin
  const val LATEST_RELEASE_URL = "https://api.github.com/repos/caneo21/pomodoro-app/releases/latest"
  internal fun parseLatestRelease(json: String): LatestRelease?
  class GitHubReleaseFetcher(private val url: String = LATEST_RELEASE_URL) : ReleaseFetcher
  class DataStoreUpdateInfoStore(private val dataStore: DataStore<Preferences>) : UpdateInfoStore
  ```
  DataStore keys (in the existing `timer_settings` store): `longPreferencesKey("update_last_checked_at")`, `stringPreferencesKey("update_latest_tag")`, `stringPreferencesKey("update_latest_url")`, `stringPreferencesKey("update_dismissed_tag")`.

- [ ] **Step 1: Write the failing tests**
  - `parseLatestRelease reads tag_name and html_url`: `{"tag_name":"v1.2.0","html_url":"https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0","name":"x"}` → `LatestRelease("v1.2.0", "https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0")`
  - `parseLatestRelease returns null for missing fields, non-JSON, or rate-limit body`: `{"message":"API rate limit exceeded"}`, `<html>login</html>`, `""`, `{"tag_name":"v1.2.0"}` → all null
  - `parseLatestRelease rejects non-https url`: `html_url` `"http://evil/x"` → null
  - `DataStoreUpdateInfoStore` (temp-file DataStore like `TimerSettingsRepositoryTest.newDataStore()`): empty → `StoredUpdateInfo()`; after `saveCheckResult(42, LatestRelease("v1.1.0","u"))` → `(42,"v1.1.0","u",null)`; after `saveDismissed("v1.1.0")` → dismissedTag `"v1.1.0"` and other fields kept.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.update.*"`
  Expected: FAIL (unresolved references)

- [ ] **Step 3: Implement both classes**
  `GitHubReleaseFetcher.fetchLatest()` runs on `Dispatchers.IO`, `HttpURLConnection` with the Global Constraints headers and 10 s connect/read timeouts; non-200 → null; any exception → null; body → `parseLatestRelease`. Always `disconnect()`.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./gradlew :app:testDebugUnitTest`
  Expected: PASS

- [ ] **Step 5: Commit** — `feat(app): fetch latest GitHub release and persist update-check state`

---

### Task 4: Android update banner and current-version row

**Files:**
- Create: `app/src/main/java/com/pomodoro/timer/update/UpdateViewModel.kt`
- Create: `app/src/main/java/com/pomodoro/timer/ui/screens/UpdateBanner.kt`
- Modify: `app/src/main/java/com/pomodoro/timer/MainActivity.kt` (second `by viewModels` for `UpdateViewModel`; pass to `PomodoroApp`)
- Modify: `app/src/main/java/com/pomodoro/timer/PomodoroApp.kt` (new param `updateViewModel: UpdateViewModel`; collect banner; pass to `TimerScreen`; pass `BuildConfig.VERSION_NAME` to `SettingsScreen`)
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/TimerScreen.kt:67-74` (new params)
- Modify: `app/src/main/java/com/pomodoro/timer/ui/screens/SettingsScreen.kt:50-79` (new param `appVersion: String = ""`, text row at the bottom)
- Test: `app/src/test/java/com/pomodoro/timer/update/UpdateViewModelTest.kt`
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/TimerScreenTest.kt` (add cases)
- Test: `app/src/androidTest/java/com/pomodoro/timer/ui/screens/SettingsScreenTest.kt` (add case)

**Interfaces:**
- Consumes: `UpdateCoordinator`, `LatestRelease` (Task 2); `GitHubReleaseFetcher`, `DataStoreUpdateInfoStore` (Task 3); `BuildConfig` (Task 1)
- Produces:
  ```kotlin
  class UpdateViewModel(private val coordinator: UpdateCoordinator?) : ViewModel() {
      val banner: StateFlow<LatestRelease?>
      fun dismiss()
      class Factory(private val appContext: Context) : ViewModelProvider.Factory  // coordinator = null when BuildConfig.DEBUG
  }
  @Composable fun UpdateBanner(release: LatestRelease, onDownload: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)
  // TimerScreen gains: updateBanner: LatestRelease? = null, onUpdateDownload: () -> Unit = {}, onUpdateDismiss: () -> Unit = {}
  ```

- [ ] **Step 1: Write the failing ViewModel tests** (main-dispatcher setup as in `TimerViewModelTest`, fake store/fetcher)
  - `banner emits newer release after init`: fetcher → `v1.1.0`, installed `1.0.0` → `banner.value == LatestRelease("v1.1.0", ...)`
  - `dismiss clears banner and persists dismissed tag`: after `dismiss()` → `banner.value == null`, fake store `dismissedTag == "v1.1.0"`
  - `null coordinator never shows a banner`: `UpdateViewModel(null).banner.value == null`

- [ ] **Step 2: Run to verify they fail**
  Run: `./gradlew :app:testDebugUnitTest --tests "com.pomodoro.timer.update.UpdateViewModelTest"`
  Expected: FAIL

- [ ] **Step 3: Implement `UpdateViewModel`** — `init` launches `coordinator?.refresh()` into a `MutableStateFlow`; `dismiss()` reads current tag, sets null, launches `coordinator.dismiss(tag)`. Factory builds `UpdateCoordinator(DataStoreUpdateInfoStore(appContext.dataStore), GitHubReleaseFetcher(), BuildConfig.VERSION_NAME, System::currentTimeMillis)` unless `BuildConfig.DEBUG`.

- [ ] **Step 4: Run to verify they pass** — same command, expected PASS.

- [ ] **Step 5: Add UI tests** (`TimerScreenTest`, `SettingsScreenTest`)
  - `updateBanner_isShownWhenIdle`: `updateBanner = LatestRelease("v1.2.0","u")`, not running → text `새 버전 v1.2.0이 있어요`, `다운로드`, `닫기` displayed
  - `updateBanner_isHiddenWhenRunning`: same banner, `isRunning = true` → text does not exist
  - `updateBanner_buttonsInvokeCallbacks`: click `다운로드` → `onUpdateDownload` called once; click `닫기` → `onUpdateDismiss` called once
  - `settings_showsCurrentVersion`: `appVersion = "1.2.0"` → `현재 버전 1.2.0` displayed

- [ ] **Step 6: Implement UI** — `UpdateBanner` uses existing Material3/theme tokens, placed at the top of `TimerScreen`'s column, rendered only when `updateBanner != null && !uiState.isRunning`. In `PomodoroApp`, `onUpdateDownload` calls `LocalUriHandler.current.openUri(release.url)`.

- [ ] **Step 7: Verify**
  Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
  Expected: PASS
  Run (device/emulator connected): `./gradlew :app:connectedDebugAndroidTest --tests "*TimerScreenTest*" --tests "*SettingsScreenTest*"` — expected PASS. If no device, say so in the report rather than skipping silently.

- [ ] **Step 8: Commit** — `feat(app): show a new-version banner and the installed version`

---

### Task 5: Update-check logic in `PomodoroWindows.Core` (C#)

**Files:**
- Create: `windows/PomodoroWindows.Core/UpdateCheck.cs`
- Test: `windows/PomodoroWindows.Core.Tests/UpdateCheckTests.cs`

**Interfaces:**
- Produces (namespace `PomodoroWindows.Core`), mirroring Task 2:
  ```csharp
  public readonly record struct SemVer(int Major, int Minor, int Patch) : IComparable<SemVer> { public static SemVer? Parse(string? text); }
  public sealed record LatestRelease(string Tag, string Url);
  public sealed record StoredUpdateInfo(long LastCheckedAtMillis = 0, string? LatestTag = null, string? LatestUrl = null, string? DismissedTag = null);
  public static class UpdatePolicy {
      public const long CheckIntervalMillis = 86_400_000;
      public static bool IsNewer(string? latestTag, string installedVersion);
      public static bool ShouldCheck(long lastCheckedAtMillis, long nowMillis);
      public static bool ShouldShow(string? latestTag, string installedVersion, string? dismissedTag);
  }
  public static class LatestReleaseParser { public static LatestRelease? Parse(string json); }  // System.Text.Json; https-only url
  public interface IUpdateInfoStore { StoredUpdateInfo Load(); void SaveCheckResult(long nowMillis, LatestRelease release); void SaveDismissed(string tag); }
  public interface IReleaseFetcher { Task<LatestRelease?> FetchLatestAsync(); }
  public sealed class UpdateCoordinator {
      public UpdateCoordinator(IUpdateInfoStore store, IReleaseFetcher fetcher, string installedVersion, Func<long> nowMillis);
      public Task<LatestRelease?> RefreshAsync();
      public void Dismiss(string tag);
  }
  ```

- [ ] **Step 1: Write the failing xUnit tests** — the same cases and values as Task 2 Step 1, plus the `parseLatestRelease` cases from Task 3 Step 1 against `LatestReleaseParser.Parse`.

- [ ] **Step 2: Run to verify they fail**
  Run: `dotnet test windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj --filter "FullyQualifiedName~UpdateCheckTests"`
  Expected: FAIL (build errors)

- [ ] **Step 3: Implement `UpdateCheck.cs`** per Interfaces. `RefreshAsync` catches all exceptions from the fetcher (except `OperationCanceledException`) and treats them as failure.

- [ ] **Step 4: Run to verify they pass**
  Run: `dotnet test windows/PomodoroWindows.Core.Tests/PomodoroWindows.Core.Tests.csproj`
  Expected: PASS (all, including existing tests)

- [ ] **Step 5: Commit** — `feat(windows-core): add update-check policy, parser, and coordinator`

---

### Task 6: Windows update InfoBar and current version

**Files:**
- Create: `windows/PomodoroWindows/Services/GitHubReleaseFetcher.cs`
- Create: `windows/PomodoroWindows/Services/ApplicationDataUpdateInfoStore.cs`
- Modify: `windows/PomodoroWindows/MainWindow.xaml` (add a row between title bar and `RootFrame` holding `<InfoBar x:Name="UpdateInfoBar" x:FieldModifier="public" IsOpen="False" IsClosable="True" Severity="Informational">` with an `ActionButton` `다운로드`)
- Modify: `windows/PomodoroWindows/App.xaml.cs` (`OnLaunched`: build coordinator, `_ = ShowUpdateBannerAsync()`; hide/show on `TimerViewModel.IsRunning` changes)
- Modify: `windows/PomodoroWindows/Views/SettingsPage.xaml` + `.xaml.cs` (TextBlock `VersionText` above `BackButton`, set in `OnNavigatedTo`)

**Interfaces:**
- Consumes: everything in Task 5's Produces block.
- Produces:
  ```csharp
  public sealed class GitHubReleaseFetcher : IReleaseFetcher   // static HttpClient, 10 s timeout, Global Constraints headers
  public sealed class ApplicationDataUpdateInfoStore : IUpdateInfoStore
  // LocalSettings keys: "UpdateLastCheckedAt" (long), "UpdateLatestTag", "UpdateLatestUrl", "UpdateDismissedTag"
  internal static class AppVersion { public static string Current { get; } }  // Package.Current.Id.Version → "Major.Minor.Build"
  ```

- [ ] **Step 1: Implement fetcher and store** per Interfaces; fetcher returns null on non-success status or any exception, else `LatestReleaseParser.Parse(body)`.

- [ ] **Step 2: Wire InfoBar in `App.OnLaunched`**
  - Wrap the whole update wiring in `#if !DEBUG`.
  - After `_window.Activate()`, await `coordinator.RefreshAsync()` on the UI thread context; if non-null, set `Message = $"새 버전 {release.Tag}이 있어요"`, `IsOpen = !timerViewModel.IsRunning`.
  - `다운로드` → `Windows.System.Launcher.LaunchUriAsync(new Uri(release.Url))`.
  - InfoBar `Closed` event with reason `CloseButton` → `coordinator.Dismiss(release.Tag)`; clear the stored banner so it never reopens.
  - Extend the existing `IsRunning` PropertyChanged handler: when a banner exists and wasn't dismissed, `IsOpen = !IsRunning`.

- [ ] **Step 3: Settings version text** — `VersionText.Text = $"현재 버전 {AppVersion.Current}"`; in unpackaged/design contexts where `Package.Current` throws, show nothing.

- [ ] **Step 4: Verify build**
  Run: `dotnet build windows/PomodoroWindows.sln -c Release -p:Platform=x64`
  Expected: Build succeeded, 0 errors.

- [ ] **Step 5: Manual check (Release config, owner's PC)** — temporarily set the manifest `Version` to `0.9.0.0`, deploy Release x64 from Visual Studio, launch: once a `v1.x` release exists the InfoBar appears; starting the timer hides it; closing it keeps it closed across restarts; the settings page shows `현재 버전 0.9.0`. Revert the manifest. If no release exists yet, defer this to Task 8 Step 6 and say so.

- [ ] **Step 6: Commit** — `feat(windows): show a new-version InfoBar and the installed version`

---

### Task 7: Windows signing certificate script and installer scripts

**Files:**
- Create: `scripts/new-windows-signing-cert.ps1`
- Create: `windows/installer/Install.ps1`
- Create: `windows/installer/Install.cmd`

**Interfaces:**
- Produces: `scripts/new-windows-signing-cert.ps1 [-OutDir keystore] [-Force]` → writes `PomodoroWindows.pfx`, `PomodoroWindows.cer` to `-OutDir`, prints the PFX base64 to the console; prompts for the PFX password with `Read-Host -AsSecureString` (never a parameter).
- Produces: `Install.ps1` expects `PomodoroWindows.cer` and `PomodoroWindows-*-<arch>.msix` in its own folder (`$PSScriptRoot`).

- [ ] **Step 1: Write `new-windows-signing-cert.ps1`**
  `New-SelfSignedCertificate -Type Custom -Subject "CN=PomodoroWindowsDev" -KeyUsage DigitalSignature -FriendlyName "Pomodoro Windows Signing" -CertStoreLocation Cert:\CurrentUser\My -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3","2.5.29.19={text}") -NotAfter (Get-Date).AddYears(5)`; export PFX/CER; refuse to overwrite an existing PFX unless `-Force`.

- [ ] **Step 2: Write `Install.ps1`**
  - Pick MSIX by `$env:PROCESSOR_ARCHITECTURE` (`ARM64` → `arm64`, else `x64`); if none matches, Korean error `이 PC에 맞는 설치 파일(.msix)을 같은 폴더에 넣어주세요.` and exit 1.
  - If `Cert:\LocalMachine\TrustedPeople` lacks the cert's thumbprint: if not elevated, relaunch itself elevated (`Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`""`) and exit; elevated → `Import-Certificate -CertStoreLocation Cert:\LocalMachine\TrustedPeople`.
  - `Add-AppxPackage -Path <msix>`; success message `설치가 끝났어요. 시작 메뉴에서 Pomodoro를 실행하세요.`; on failure print the exception message and `Read-Host` to keep the window open.

- [ ] **Step 3: Write `Install.cmd`**
  ```bat
  @echo off
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1"
  ```

- [ ] **Step 4: Verify locally (owner runs; the cert step creates a signing key)**
  1. `powershell -File scripts/new-windows-signing-cert.ps1` → `keystore/PomodoroWindows.pfx` and `.cer` exist; `git status` shows neither.
  2. Build a signed MSIX with the same command Task 8 uses:
     `msbuild windows/PomodoroWindows/PomodoroWindows.csproj /restore /p:Configuration=Release /p:Platform=x64 /p:GenerateAppxPackageOnBuild=true /p:UapAppxPackageBuildMode=SideloadOnly /p:AppxBundle=Never /p:AppxPackageSigningEnabled=true /p:PackageCertificateKeyFile=<abs path to pfx> /p:PackageCertificatePassword=<pwd> /p:AppxPackageDir=<abs out dir>\`
     Expected: an `.msix` under the out dir.
  3. Copy that MSIX (renamed `PomodoroWindows-0.0.1-x64.msix`), the `.cer`, `Install.ps1`, `Install.cmd` into one folder, mark them as downloaded (`Set-Content -Path <file> -Stream Zone.Identifier -Value "[ZoneTransfer]`nZoneId=3"`), uninstall any dev install of the app, double-click `Install.cmd`. Expected: one UAC prompt, app installs and launches from Start.

- [ ] **Step 5: Commit** — `build(windows): add self-signed cert script and one-click installer`

---

### Task 8: Release workflow and docs

**Files:**
- Create: `.github/workflows/release.yml`
- Create: `.github/release-notes-header.md`
- Create: `docs/install.md`
- Create: `docs/releasing.md`

**Interfaces:**
- Consumes: Task 1 (`-PreleaseVersion`, `ANDROID_*` env vars), Task 7 (`windows/installer/*`, msbuild command), Global Constraints (secret names, asset names).

- [ ] **Step 1: Write `release.yml`**
  - `on: push: tags: ['v*']`; `permissions: contents: write`; `concurrency: release-${{ github.ref }}`.
  - **validate** (ubuntu): bash regex `^v([0-9]+)\.([0-9]{1,2})\.([0-9]{1,2})$` on `${GITHUB_REF_NAME}`; output `version` (no `v`).
  - **android** (needs validate): checkout, setup-java 17 temurin, setup-gradle (as `ci.yml`). Decode `ANDROID_KEYSTORE_BASE64` to `$RUNNER_TEMP/release.jks`; export `ANDROID_KEYSTORE_PATH` + the three passwords/alias from secrets. Run `./gradlew --console=plain -Dorg.gradle.java.home="$JAVA_HOME" -PreleaseVersion=$version :core:test :app:testDebugUnitTest :wear:testDebugUnitTest :app:assembleRelease :wear:assembleRelease`. Copy/rename APKs to `pomodoro-phone-$version.apk` / `pomodoro-watch-$version.apk`; upload with `actions/upload-artifact@v4` name `android`. `if: always()` step deletes the jks.
  - **windows** (needs validate, windows-latest): checkout, setup-dotnet 8, `microsoft/setup-msbuild@v2`. Run Core tests. Stamp `windows/PomodoroWindows/Package.appxmanifest` `Identity/@Version` = `$version.0` (PowerShell `[xml]` edit). Decode PFX to `$env:RUNNER_TEMP\signing.pfx`. For `x64` and `arm64`: the Task 7 msbuild command with `/p:Platform=<arch>`. Rename outputs to `PomodoroWindows-$version-<arch>.msix`. Export the public cert as `PomodoroWindows.cer` from the PFX (`Get-PfxData` / `Export-Certificate`). Copy `windows/installer/Install.ps1` and `Install.cmd`. Upload as artifact `windows`. `if: always()` step deletes the PFX.
  - **publish** (needs android, windows): download both artifacts; `gh release create "$GITHUB_REF_NAME" --title "$GITHUB_REF_NAME" --generate-notes --notes-file .github/release-notes-header.md <all 7 files>` with `GH_TOKEN: ${{ github.token }}`. Check in the Step 7 trial that the header and the generated notes both appear; their order doesn't matter.
  - No step echoes a secret; never use `set -x`.

- [ ] **Step 2: Write `.github/release-notes-header.md`** (Korean) — a three-line table: 휴대폰 → `pomodoro-phone-*.apk`; Windows → `.msix` for your PC + `PomodoroWindows.cer` + `Install.ps1` + `Install.cmd` (run `Install.cmd`); 워치 → `pomodoro-watch-*.apk` via ADB. Link: `https://github.com/caneo21/pomodoro-app/blob/main/docs/install.md`. One line: 처음 설치 전 예전 버전이 있으면 삭제 후 설치하세요 (설정/기록이 초기화돼요).

- [ ] **Step 3: Write `docs/install.md`** — the phone / Windows / watch steps from spec §5, Korean, including the "first time only: uninstall old build" note and why `Install.cmd` exists.

- [ ] **Step 4: Write `docs/releasing.md`** — spec §6: run the cert script; produce base64 locally with
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\pomodoro-release.jks")) | Set-Clipboard` (and the same for the PFX); add the six secrets at *Settings → Secrets and variables → Actions*; back up `.jks` and `.pfx` off the PC; release with `git tag vX.Y.Z` + `git push origin vX.Y.Z`; how to delete a bad release (`gh release delete vX.Y.Z --cleanup-tag`).

- [ ] **Step 5: Lint the workflow**
  Run: `actionlint .github/workflows/release.yml` if available (else `docker run --rm -v "${PWD}:/repo" -w /repo rhysd/actionlint:latest`). If neither is available, say so.
  Expected: no errors.

- [ ] **Step 6: Commit** — `ci: publish signed phone, watch, and Windows builds on version tags`

- [ ] **Step 7: End-to-end trial (needs the owner: secrets added, branch merged to `main`)**
  Owner pushes `v0.0.1`. Expected: workflow green; release `v0.0.1` has exactly the 7 assets; phone APK installs; watch APK installs via ADB and syncs with the phone; Windows install via `Install.cmd` works on a PC that never trusted the cert. Then a second trial tag `v0.0.2` → the `v0.0.1` phone/Windows apps show `새 버전 v0.0.2이 있어요` on next launch (clear app data / wait 24 h, or set `update_last_checked_at` back). Delete both trial releases and tags afterwards, then release `v1.0.0`.
