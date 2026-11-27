# Release System — Design Spec

**Goal:** Ship the phone, watch, and Windows apps to the owner's own devices and a few friends through **GitHub Releases**, with one command (pushing a version tag), and have the phone and Windows apps tell users when a newer release exists.

**Why:** Today there is no way to hand a friend an installable build: versions are hardcoded at `1.0`, release signing exists only on the owner's PC (`keystore.properties` + `keystore/pomodoro-release.jks`, both git-ignored), the watch has no release signing at all, and CI (`.github/workflows/ci.yml`) only builds debug. Friends won't watch the repo, so the apps themselves must say "a new version is out."

**Audience:** the owner and a handful of friends who install by hand (sideload APK / MSIX). Not the public.

## Goals

- `git tag v1.2.0 && git push origin v1.2.0` → GitHub Actions builds, signs, and publishes a GitHub Release with every installer attached. No manual version edits in build files.
- Phone (`:app`) and Windows apps check GitHub for the latest release and, if it is newer than what's installed, show a dismissible "새 버전이 있어요" notice that opens the release page in the browser.
- Friends can install the Windows app with a documented, one-time admin step and plain double-click updates afterwards.

## Non-goals

- Google Play / Microsoft Store distribution (AAB, Play Console, Partner Center).
- In-app download-and-install of updates (user downloads from the browser).
- Windows App Installer (`.appinstaller`) auto-update.
- Update notices on the watch (`:wear`). Wear OS has no friendly sideload path; the watch APK is attached to the release with ADB instructions, nothing more.
- Pre-release / beta channels.
- A paid, publicly trusted code-signing certificate.

## Anti-goals (would count as failure even if it "works")

- The release keystore, its passwords, or the Windows signing PFX ever committed to the repo or printed in CI logs.
- The update check nagging: showing while a timer is running, showing again for a version the user already dismissed, or surfacing network errors to the user.
- A release whose phone and watch APKs are signed with **different** keys (see Constraints) — the watch would silently stop syncing.
- Debug builds on the owner's machine constantly claiming an update is available.

## Constraints (found in the codebase)

- **Phone and watch share `applicationId = "com.pomodoro.timer"`** (`wear/build.gradle.kts:14`). The Wearable Data Layer only connects apps with the same package name **and the same signing certificate**, so `:wear` release must be signed with the same keystore as `:app`.
- **Signature continuity:** an APK can only update an installed app signed with the same key. Anyone who currently has a debug build (or a build signed with another key) must uninstall once before installing the first release; this loses their local settings/stats. Release notes for the first release say so.
- **Windows is packaged MSIX** (`WindowsPackageType=MSIX`, `Identity Publisher="CN=PomodoroWindowsDev"`, `Platforms` x64;arm64). MSIX only installs if its signing certificate is trusted, and the certificate subject must equal the manifest `Publisher`. Packaging is kept because toast notifications and `ApplicationData` settings depend on package identity.
- MSIX versions are four-part (`Major.Minor.Build.Revision`); revision stays `0`.
- `gradle.properties` pins `org.gradle.java.home` to a local Windows JDK; CI already overrides with `-Dorg.gradle.java.home="$JAVA_HOME"` and the release workflow must too.
- The phone app currently has **no `INTERNET` permission**; the update check adds it (normal permission, no runtime prompt). This is the app's first network access.
- The repo `caneo21/pomodoro-app` is public, so `GET https://api.github.com/repos/caneo21/pomodoro-app/releases/latest` works without auth (60 requests/hour/IP limit — far above once-a-day checks). GitHub requires a `User-Agent` header; use a generic one (`PomodoroApp-UpdateCheck`), nothing identifying.
- Existing pattern: pure logic lives in `core/` (Kotlin, JVM tests) and `windows/PomodoroWindows.Core/` (C#, `PomodoroWindows.Core.Tests`). The version comparison goes there.
- Secrets can only be entered into GitHub by the owner (Claude does not handle passwords/keys in fields).

## Approaches considered

**Update check**
1. **App asks GitHub's "latest release" API, shows a notice, opens the release page in the browser — chosen.** Least code, nothing to maintain beyond publishing releases, same mechanism on phone and Windows.
2. Same check, but the app downloads and installs the update itself. One-tap updates, but needs `REQUEST_INSTALL_PACKAGES`, a `FileProvider`, download/error handling on Android and MSIX launch on Windows — roughly double the work and test surface. Rejected for now.
3. Windows App Installer auto-update. Windows-only (phone still needs approach 1), and flaky behind GitHub's download redirects. Rejected.

**Windows distribution**
- **A. Self-signed certificate + install script — chosen.** Keeps MSIX/package identity; one admin prompt on first install.
- B. Unpackaged zip. Easiest install, but loses package identity that notifications and settings storage rely on. Rejected.

## Design

### 1. Versioning (single source of truth: the git tag)

- Tag format: `vMAJOR.MINOR.PATCH` (e.g. `v1.2.0`). The release workflow fails fast on anything else. Each part 0–99 for minor/patch.
- Gradle reads the version from a Gradle property `-PreleaseVersion=1.2.0` (passed by CI from the tag). Shared helper (root `build.gradle.kts` or a small `buildSrc`/convention snippet — builder's choice) computes for both `:app` and `:wear`:
  - `versionName = "1.2.0"`
  - `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH` (→ `10200`)
  - Without the property (local builds): `versionName = "0.0.0-dev"`, `versionCode = 1`.
- Windows: CI writes `Version="1.2.0.0"` into `Package.appxmanifest`'s `Identity` before building. The checked-in value stays `1.0.0.0`; local Debug builds skip the update check (§4), so it never misfires.
- **First release: `v1.0.0`** (Claude's call; matches the current `1.0`).

### 2. Signing

**Android (phone + watch, same key)**
- `:wear` gets the same `signingConfigs.release` block as `:app`, reading the same `keystore.properties`.
- In CI, signing values come from environment variables when `keystore.properties` is absent. Both modules read: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The workflow decodes the base64 keystore secret to a temp file and sets the path.
- GitHub Secrets (owner adds them): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
- A release build with no signing config must **fail**, not silently produce an unsigned APK.

**Windows (self-signed)**
- New script `scripts/new-windows-signing-cert.ps1` (owner runs once): creates a self-signed code-signing cert with subject `CN=PomodoroWindowsDev`, ~5-year validity, exports `PomodoroWindows.pfx` (password-protected) and public `PomodoroWindows.cer` into a git-ignored folder, and prints the base64 of the PFX for the owner to paste into GitHub.
- GitHub Secrets: `WINDOWS_CERT_PFX_BASE64`, `WINDOWS_CERT_PASSWORD`.
- The public `.cer` is attached to every release (it's not secret).
- The same cert must be reused across releases; a new cert means friends re-trust once. The script refuses to overwrite an existing PFX without `-Force`.

### 3. Release workflow (`.github/workflows/release.yml`)

Trigger: `push` of tags matching `v*`. `permissions: contents: write`.

Jobs:
1. **validate** — parse the tag into `version` (`1.2.0`); fail on bad format.
2. **android** (ubuntu-latest) — same JDK/Gradle setup as `ci.yml`; run `:core:test :app:testDebugUnitTest :wear:testDebugUnitTest`, then `:app:assembleRelease :wear:assembleRelease` with `-PreleaseVersion`. Upload as job artifacts, renamed to:
   - `pomodoro-phone-1.2.0.apk`
   - `pomodoro-watch-1.2.0.apk`
3. **windows** (windows-latest) — run `PomodoroWindows.Core.Tests`; stamp manifest version; build signed MSIX packages for **x64 and arm64** with the decoded PFX (MSBuild `AppxPackageSigningEnabled`, `PackageCertificateKeyFile`, `PackageCertificatePassword`). Artifacts:
   - `PomodoroWindows-1.2.0-x64.msix`
   - `PomodoroWindows-1.2.0-arm64.msix`
   - `PomodoroWindows.cer`
   - `Install.ps1`
   - `Install.cmd` (wrapper; see §5)
4. **publish** (needs 2 and 3) — `gh release create v1.2.0 --generate-notes` with all files attached, plus a short fixed header prepended to the notes (`.github/release-notes-header.md`: which file to download per device, and a link to `docs/install.md` on GitHub for full steps). Not a draft, not a pre-release (so `/releases/latest` returns it).

Secrets are never echoed; temp keystore/PFX files are deleted in an `always()` step.

### 4. In-app update check (phone + Windows)

**Shared logic** (pure, unit-tested in both `core/` and `PomodoroWindows.Core/`):
- Parse `"v1.2.0"` / `"1.2.0"` → (major, minor, patch); invalid → no update.
- `isNewer(latestTag, installedVersion)`; installed `0.0.0-dev` (or unparseable) → never newer (so dev builds stay quiet).
- `shouldCheck(lastCheckedAt, now)` → true if ≥ 24h since last check.
- `shouldShow(latestVersion, dismissedVersion)` → hidden if the user dismissed this exact version.

**Fetch:** `GET /repos/caneo21/pomodoro-app/releases/latest`, read `tag_name` and `html_url`. Timeout ~10s. Any failure (offline, 403 rate limit, bad JSON) → silently do nothing and retry next eligible check. No new dependencies: Android uses `HttpURLConnection` + `org.json` on `Dispatchers.IO`; Windows uses `HttpClient` + `System.Text.Json`.

**When:** on app start only (no foreground/background polling), at most once per 24h. Store `lastUpdateCheckAt`, `latestKnownVersion`, `latestReleaseUrl`, `dismissedUpdateVersion` — phone in the existing DataStore (`TimerSettingsRepository` or a small sibling repository), Windows in `ApplicationDataSettingsStore`. A cached newer version keeps the notice visible between checks.

**Phone UI:** a small banner at the top of `TimerScreen`, shown only when the timer is idle (not running). Text: "새 버전 v1.2.0이 있어요" with actions "다운로드" (opens `html_url` via `ACTION_VIEW`) and "닫기" (sets `dismissedUpdateVersion`). `SettingsScreen` shows "현재 버전 1.2.0" at the bottom (from `BuildConfig.VERSION_NAME`; enable `buildConfig`).

**Windows UI:** a WinUI `InfoBar` at the top of the main window with the same text and actions ("다운로드" opens the URL with `Launcher.LaunchUriAsync`; closing dismisses that version). Installed version from `Package.Current.Id.Version`. Settings page shows the current version.

**Debug builds:** the check is skipped entirely in Android debug builds and Windows Debug configuration.

### 5. Install docs

`docs/install.md` (linked from every release's notes), Korean, short:
- **휴대폰:** download `pomodoro-phone-*.apk` → allow "출처를 알 수 없는 앱" for the browser → install. First time only: uninstall any old build.
- **Windows:** download the `.msix` for your PC (most PCs: x64), `PomodoroWindows.cer`, `Install.ps1`, and `Install.cmd` to one folder → double-click `Install.cmd` → approve the admin prompt (trusts the cert once, then installs). Later updates: double-click the new `.msix`.
- Why `Install.cmd`: downloaded `.ps1` files carry the internet "Mark of the Web", and the default execution policy blocks them. `Install.cmd` runs `powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1"`, so friends never touch execution policy.
- **Watch:** enable ADB debugging + Wi-Fi debugging on the watch → `adb connect <ip>` → `adb install pomodoro-watch-*.apk`.

`Install.ps1`: if the cert isn't in `LocalMachine\TrustedPeople`, self-elevate and import it; then `Add-AppxPackage` the MSIX in the same folder matching the machine's architecture. Clear Korean messages on failure.

### 6. Owner's one-time setup (documented in `docs/releasing.md`)

1. Run `scripts/new-windows-signing-cert.ps1`.
2. Add the six GitHub Secrets (base64 of the existing `pomodoro-release.jks` + its passwords/alias; PFX base64 + password). The doc gives the exact PowerShell one-liner to produce base64 locally.
3. Back up the `.jks` and `.pfx` somewhere outside the PC — losing the Android key means every user must uninstall to update.
4. Releasing: `git tag v1.0.0`, `git push origin v1.0.0`, watch the Actions run.

## Testing

- Unit tests for version parsing/comparison/check-interval/dismiss logic in `core` (JUnit) and `PomodoroWindows.Core.Tests` (xUnit), TDD.
- Phone: ViewModel/repository tests for "banner shown when newer & idle, hidden when dismissed/running" with a fake fetcher; the fetcher itself is behind an interface.
- Gradle: verify `-PreleaseVersion=1.2.3` yields `versionCode 10203` (e.g. a check task or inspecting `aapt dump badging` locally).
- Workflow: first run on a throwaway tag like `v0.0.1` (then delete that release/tag), verify all seven files attach, phone APK installs over nothing, watch APK installs and syncs with the phone, MSIX installs via `Install.ps1` on a clean-ish Windows user.

## Left to the builder

- Exact Gradle placement of the version/signing helper.
- MSBuild invocation details for signed MSIX per architecture.
- Banner/InfoBar styling within the existing design tokens.
- Whether the existing `ci.yml` should also gain `assembleRelease` smoke builds (unsigned) — optional.

## Calls Claude made (owner may override)

- First release is `v1.0.0`; `versionCode = M*10000 + m*100 + p`.
- Windows ships both x64 and arm64 MSIX.
- Update check: on launch, once per 24h, silent on failure, per-version dismiss, hidden while the timer runs, skipped in debug builds.
- Release notes: GitHub auto-generated from commits/PRs plus a fixed install header.
- Watch release is signed with the phone's key and gets no update notice.
