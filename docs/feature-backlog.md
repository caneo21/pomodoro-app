# Feature Backlog

Prioritized list of feature gaps identified from a competitive analysis against the reference app `com.pomodrone.app` (an ad-supported Pomodoro timer installed on the phone used for testing). Feature 1 (long-break cycle) is done; this tracks what's next. Each item should go through `superpowers:brainstorming` when picked up — most are "bounded" (existing flow, small change), but confirm before assuming.

## Status

- [x] 1. **Three-phase Pomodoro cycle** (long break every N focus sessions) — implemented, merged to `master`. See `docs/superpowers/specs/2026-08-17-long-break-cycle-design.md` and `docs/superpowers/plans/2026-08-17-long-break-cycle.md`.
- [x] 1a. **LONG_BREAK accent color** — `TimerScreen` and `WearTimerScreen` now render an indigo (`#5C6BC0`) `CircularProgressIndicator` when `phase == LONG_BREAK`, via a shared `progressColorFor(phase, primary, accent)` helper in each screen. Covered by local JUnit tests (`TimerScreenColorTest`, `WearTimerScreenColorTest`).
- [x] 2. **Autostart options** — "자동으로 휴식 시작" (auto-start break when focus ends) and "자동으로 다음 뽀모도로 시작" (auto-start next focus when break ends), each a separate toggle in Settings. Persisted via `TimerSettingsRepository`, consumed by `TimerEngine`'s phase-transition logic, and exposed as `Switch` controls in `SettingsScreen` (phone). See `.superpowers/sdd/2026-08-18-autostart-options/`.
- [ ] 3. **Notification customization** — toggle to disable notifications entirely, vibrate on/off, sound style choice ("알림음" vs "알람음" i.e. notification sound vs alarm sound), and interactive buttons on the ongoing notification itself (play/pause/reset/close), not just the current single pause action. Touches `TimerService`/`WearTimerService`'s notification-building code and adds new settings.
- [ ] 4. **Daily goal** — user sets a target number of completed pomodoros per day; a notification fires when the goal is reached. Needs a persisted daily counter that resets at midnight (new concept, not covered by the existing `completedPomodoros`-since-last-long-break counter).
- [ ] 5. **Keep screen awake** — a toggle so the phone screen doesn't sleep while a session is running. Small, phone-only (`WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` or Compose's `keepScreenOn` equivalent).
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
