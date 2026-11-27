# Releasing

Pushing a `vMAJOR.MINOR.PATCH` tag runs `.github/workflows/release.yml`, which tests, builds,
signs, and publishes a GitHub Release with:

- `pomodoro-phone-<v>.apk`, `pomodoro-watch-<v>.apk` (same signing key)
- `PomodoroWindows-<v>-x64.msix`, `PomodoroWindows-<v>-arm64.msix`
- `WindowsAppRuntime-x64.msix`, `WindowsAppRuntime-arm64.msix` (the runtime the Windows app needs)
- `PomodoroWindows.cer`, `Install.ps1`, `Install.cmd`

The version comes only from the tag: `v1.2.3` → `versionName 1.2.3`, `versionCode 10203`,
MSIX `1.2.3.0`. Minor and patch must be 0–99. Local builds are `0.0.0-dev` and never show the
update banner.

## One-time setup

1. **Windows signing certificate.** Run once and pick a password:

   ```powershell
   powershell -File scripts/new-windows-signing-cert.ps1
   ```

   This writes `keystore/PomodoroWindows.pfx` and `keystore/PomodoroWindows.cer` (both
   git-ignored) and copies the PFX's base64 to the clipboard.

2. **Base64 of the Android keystore**, copied to the clipboard:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\pomodoro-release.jks")) | Set-Clipboard
   ```

   (Same for the PFX if you need it again: replace the path with `keystore\PomodoroWindows.pfx`.)

3. **GitHub Secrets.** In the repo: *Settings → Secrets and variables → Actions → New repository
   secret*. Add:

   | Secret | Value |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | base64 of `keystore/pomodoro-release.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
   | `ANDROID_KEY_ALIAS` | `keyAlias` from `keystore.properties` |
   | `ANDROID_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
   | `WINDOWS_CERT_PFX_BASE64` | base64 of `keystore/PomodoroWindows.pfx` |
   | `WINDOWS_CERT_PASSWORD` | the password you chose in step 1 |

4. **Back up** `keystore/pomodoro-release.jks`, `keystore.properties`, and
   `keystore/PomodoroWindows.pfx` somewhere off this PC (a password manager works). If the Android
   key is lost, every phone and watch has to uninstall (losing their data) to get updates. A new
   Windows certificate means everyone runs `Install.cmd` once more.

## Releasing a version

```powershell
git tag v1.2.0
git push origin v1.2.0
```

Watch the run under the repo's **Actions** tab. When it's green, the release is live and every
installed phone and Windows app shows the update banner within a day of its next launch.

## A bad release

Delete it (and its tag) and publish a fixed one with a higher version:

```powershell
gh release delete v1.2.0 --cleanup-tag --yes
```

Never reuse a version number: Android refuses to install a `versionCode` that isn't higher than
what's installed.
