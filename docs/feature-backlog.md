# Feature Backlog

Prioritized list of feature gaps identified from a competitive analysis against the reference app `com.pomodrone.app` (an ad-supported Pomodoro timer installed on the phone used for testing). Feature 1 (long-break cycle) is done; this tracks what's next. Each item should go through `superpowers:brainstorming` when picked up — most are "bounded" (existing flow, small change), but confirm before assuming.

## Status

- [x] 1. **Three-phase Pomodoro cycle** (long break every N focus sessions) — implemented, merged to `master`. See `docs/superpowers/specs/2026-08-17-long-break-cycle-design.md` and `docs/superpowers/plans/2026-08-17-long-break-cycle.md`.
- [x] 1a. **LONG_BREAK accent color** — `TimerScreen` and `WearTimerScreen` now render an indigo (`#5C6BC0`) `CircularProgressIndicator` when `phase == LONG_BREAK`, via a shared `progressColorFor(phase, primary, accent)` helper in each screen. Covered by local JUnit tests (`TimerScreenColorTest`, `WearTimerScreenColorTest`).
- [x] 2. **Autostart options** — "자동으로 휴식 시작" (auto-start break when focus ends) and "자동으로 다음 뽀모도로 시작" (auto-start next focus when break ends), each a separate toggle in Settings, defaulting off. Persisted via `TimerSettingsRepository`, consumed by `TimerEngine`'s phase-transition logic, synced to the watch, and exposed as `Switch` controls in `SettingsScreen` (phone). See `docs/superpowers/specs/2026-08-18-autostart-options-design.md` and `docs/superpowers/plans/2026-08-18-autostart-options.md`.
- [x] 3. **Notification customization** — "알림 사용"/"진동"/"알람음 사용" toggles in Settings (phone-only), defaulting to on/on/off (알림음). `notificationsEnabled`/`vibrationEnabled`/`useAlarmSound` persist via `TimerSettingsRepository`, sync to the watch through `SyncPayload`, and drive both `TimerService` and `WearTimerService`. Because notification channels are immutable after creation on API 26+, the sound x vibration combinations are pre-created as four channels up front (`NotificationChannels`, `:core`) and selected at notify-time; `notificationsEnabled = false` skips the session-end/daily-goal `notify()` calls entirely (the ongoing foreground notification always shows, per Android's requirement). The ongoing notification's single pause action was replaced with a play/pause toggle (`NotificationContent.toggleActionLabel`) plus a reset action, mirrored on the watch (which previously had no notification actions at all).
- [x] 4. **Daily goal** — "일일 목표 사용" toggle and a 1–20 target slider in Settings (phone-only), defaulting to off/8. A pure `DailyGoalTracker` (`:core`) rolls the count over at local midnight (ISO date string) and detects the exact instant the target is crossed; `TimerSettingsRepository` persists today's date/count separately from settings; `TimerService`/`WearTimerService` record a completion whenever a FOCUS phase ends and fire a one-time celebratory notification (`DAILY_GOAL_NOTIFICATION_ID = 3`) in addition to the normal session-end notification. The running count syncs between phone and watch via `SyncPayload`, same as `completedPomodoros`; the watch has no settings screen and tracks progress in memory only (no local persistence), consistent with `autoStartBreak`/`autoStartFocus`. See `docs/superpowers/specs/2026-08-19-daily-goal-design.md` and `docs/superpowers/plans/2026-08-19-daily-goal.md`.
- [x] 5. **Keep screen awake** — "화면 꺼짐 방지" toggle in Settings (phone-only), persisted via `TimerSettingsRepository`. `TimerScreen` applies `LocalView.current.keepScreenOn` only while the toggle is on AND a session is running (`isRunning`); resets on dispose. Not applied to the watch — Wear OS uses ambient mode for always-visible time rather than `FLAG_KEEP_SCREEN_ON`, and forcing the screen on would drain the watch's smaller battery.
- [ ] 6. **Color themes** — multiple selectable UI color themes (the reference app has ~20). Larger scope than 1a above — a full theming system, not just one accent color.
- [ ] 7. **Onboarding flow** — a first-run, multi-screen introduction (reference app has 6 screens) explaining the Pomodoro technique and how to use the app.

## Effort ordering (smallest to largest)

1. 1a. LONG_BREAK 색상 구분 — 조건 하나로 강조색만 변경 (phone/watch 각각 한 줄 수준)
2. 5. 화면 꺼짐 방지 — 토글 하나 + `keepScreenOn`/`FLAG_KEEP_SCREEN_ON` 적용, phone 전용
3. 2. 자동시작 옵션 — 토글 2개, 기존 `TimerEngine`의 phase 전환 로직 위에 바로 얹음
4. 4. 일일 목표 — 자정 리셋 카운터라는 새 개념 + 알림 트리거 로직 필요
5. 3. 알림 커스터마이징 — 하위 항목 다수(알림 끄기/진동/사운드/인터랙티브 버튼), `TimerService`·`WearTimerService` 양쪽 수정
6. 6. 컬러 테마 — 전체 테마 시스템, 여러 화면의 색상 참조 구조 재설계
7. 7. 온보딩 플로우 — 첫 실행 감지 + 다중 화면(6개) 신규 UI 플로우

## Context

Original gap analysis and priority ordering came from inspecting `com.pomodrone.app` directly on a real device (screenshots, settings screen, `aapt2 dump resources` on the APK for exact string-resource names). The watch companion app (Galaxy Watch 6, separate `:wear` module) has no equivalent in the reference app — already a differentiator, not a gap.
