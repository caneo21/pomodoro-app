# Windows 네이티브 뽀모도로 앱 설계 (1단계: 독립 앱)

- 작성일: 2026-08-22
- 상태: 승인됨 (구현 계획 수립 단계로 전환)

## 배경 / 목표

기존 안드로이드 폰 앱([2026-08-11-pomodoro-android-design.md](2026-08-11-pomodoro-android-design.md))·갤럭시 워치 앱([2026-08-13-wear-os-companion-design.md](2026-08-13-wear-os-companion-design.md))에 이어, 데스크톱(Windows PC)에서도 쓸 수 있는 네이티브 뽀모도로 앱을 만든다.

전체 요청("스마트폰 앱·워치와 연동되어 작동하는 Windows 네이티브 앱")은 두 개의 독립적인 서브 프로젝트로 나뉜다:

1. **Windows 독립 앱** (이 문서) — 폰/워치와 무관하게 그 자체로 완결된 뽀모도로 타이머 앱을 C#/WinUI 3로 새로 만든다.
2. **폰 ↔ PC 동기화** (다음 단계, 별도 설계 문서) — 같은 Wi-Fi 상에서 폰과 PC 간 타이머 상태를 양방향 동기화한다. 워치는 이미 폰을 허브로 동기화되므로 별도 작업 없이 자동으로 3자 동기화 효과를 얻는다.

이 문서는 1단계만 다룬다. 1단계 산출물은 동기화 기능이 없어도 그 자체로 완전히 쓸 수 있는 제품이며, 2단계는 이 위에 얹는다.

## 기기 소유권 모델

워치와 동일한 원칙을 따른다: **PC는 폰의 원격 화면이 아니라 독립 실행형이다.** PC가 자체 `TimerEngine`(C# 포팅)을 소유하며, 폰이 없거나 꺼져 있어도 PC만으로 뽀모도로 세션을 시작·진행·종료할 수 있다. (2단계에서 동기화가 붙어도 이 전제는 유지된다 — 워치 설계와 같은 "연결은 부가 기능, 로컬 동작이 핵심 경로" 원칙.)

## 기술 스택

- **WinUI 3 (Windows App SDK) + .NET 8**, C#
- **MSIX 패키징** — Toast 알림, 시작 메뉴 등록, `Windows.Storage.ApplicationData` 로컬 설정 저장소를 별도 설정 없이 바로 쓸 수 있다.
- MVVM 패턴, `CommunityToolkit.Mvvm`으로 `ObservableObject`/`RelayCommand` 사용
- 트레이 아이콘: `H.NotifyIcon.WinUI` (커뮤니티 패키지 — WinUI 3에 트레이 아이콘 API가 기본 제공되지 않음)

**검토했지만 채택하지 않은 대안:**
- *WPF*: 더 성숙하지만 Microsoft가 장기적으로 미는 최신 네이티브 스택이 아니고, 이번이 신규 프로젝트라 레거시를 택할 이유가 없음.
- *.NET MAUI*: 크로스플랫폼(Windows/Android/iOS 공용) 지향이라 나중에 폰 앱까지 다시 만든다면 코드 공유가 가능하지만, 기존 Kotlin 폰/워치 앱을 유지하는 현재 상황과는 맞지 않음. 순수 Windows 타겟이면 WinUI 3가 더 가볍고 직접적.

## 모듈 구조 / 프로젝트 구성

```
PomodoroWindows.sln
  PomodoroWindows.Core     (순수 .NET 클래스 라이브러리, WinUI 의존성 없음)
    - TimerEngine, TimerState, TimerPhase       (Kotlin :core 버전을 동작 그대로 C#로 재구현)
    - TimerDefaults, ColorTheme, DailyGoalProgress/DailyGoalTracker
    - NotificationContent                        (문구 매핑, 순수 함수)

  PomodoroWindows            (WinUI 3 앱 프로젝트, MSIX 패키징)
    - App.xaml / MainWindow.xaml                 (Frame 네비게이션 호스트)
    - ViewModels/TimerViewModel, SettingsViewModel
    - Views/TimerPage.xaml, Views/SettingsPage.xaml
    - Services/TimerTickService                  (DispatcherQueueTimer 기반 1초 틱)
    - Services/SettingsStore                     (ApplicationData.LocalSettings 래퍼)
    - Services/NotificationService                (AppNotificationManager 래퍼)
    - Services/TrayIconService                    (H.NotifyIcon 래퍼)
```

`PomodoroWindows.Core`를 별도 프로젝트로 분리하는 이유는 워치의 `:core` 모듈과 같다 — WinUI 의존성 없는 순수 로직이라야 유닛 테스트가 빠르고, 2단계(동기화)에서 `SyncPayload`/`SyncCoordinator`를 이식할 때도 이 프로젝트에 추가하면 된다.

## 기능 범위

폰 `TimerSettings`를 기준으로 그대로 포팅:

- 3단계 뽀모도로 사이클 (FOCUS → SHORT_BREAK/LONG_BREAK, `pomodorosUntilLongBreak` 주기)
- 자동시작 옵션 2종 (자동 휴식 시작 / 자동 다음 뽀모도로 시작)
- 화면 꺼짐 방지 — Windows에서는 `SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED)`로 실행 중일 때만 절전 방지, 폰의 `keepScreenOn` 토글과 동일 UX
- 일일 목표 (자정 롤오버 카운터, 목표 달성 알림)
- 컬러 테마 4종 (DEFAULT/OCEAN/FOREST/LAVENDER) — 폰의 `PomodoroPalette` 라이트/다크 값을 그대로 `Windows.UI.Color`로 이식, WinUI 3 앱 테마(라이트/다크) 전환에 반응
- 알림 사용 / 알람음 사용 토글

**제외 (폰 대비 축소):**
- **진동 토글 삭제** — PC에 햅틱 하드웨어가 없어 설정 자체가 무의미함.
- **온보딩 6페이지 플로우 생략** — 설명용 콘텐츠이고 기능 동작에 영향이 없어 1단계 범위에서 제외. 필요해지면 별도 하위 작업으로 추가 가능(YAGNI).

## 타이머 화면 구성

- 중앙에 남은 시간(mm:ss) + 원형 프로그레스 링 — 폰과 동일한 시각 언어, 컬러 테마의 `primary`/`longBreakAccent` 반영
- 현재 모드 텍스트: "집중" / "휴식" / "긴 휴식"
- 시작/일시정지 토글 버튼, 리셋 버튼
- 완료한 뽀모도로 개수 및 (활성화 시) 일일 목표 진행도 표시
- 설정 화면 진입 버튼 (Frame 네비게이션으로 `SettingsPage`로 전환, 폰의 `SettingsScreen`과 동일한 항목 구성 — 위 "기능 범위"에서 진동/온보딩 항목만 제외)

## 백그라운드 동작 / 트레이

- 창 닫기(X 버튼)를 `Window.Closed` 대신 `AppWindow.Closing`에서 가로채 `e.Cancel = true` 후 창을 숨김 (`AppWindow.Hide()`) — 프로세스는 트레이에 상주.
- `TimerTickService`가 창의 표시 여부와 무관하게 1초 간격으로 `TimerEngine.tick(now)` 호출 — 워치/폰의 절대 종료 시각(`endTimestamp`) 기반 계산을 그대로 재사용하므로 백그라운드에서도 드리프트 없음.
- 트레이 아이콘 좌클릭: 창 복원. 우클릭 메뉴: 시작/일시정지 토글, 리셋, "열기", "종료"(완전 종료는 이 메뉴에서만 가능).
- 트레이 아이콘 툴팁에 남은 시간 표시(선택적 개선, 1단계 필수는 아님).

## 알림

- **세션 종료 시**: `Microsoft.Windows.AppNotifications.AppNotificationManager`로 Toast 발송. 제목은 `NotificationContent.sessionEndTitle`을 그대로 이식.
- **일일 목표 달성 시**: 동일하게 Toast, `dailyGoalReachedTitle` 문구 이식.
- `notificationsEnabled = false`면 위 두 Toast를 스킵 (진행 중 알림에 해당하는 것은 트레이 아이콘 자체이므로 별도 스킵 대상 없음 — 폰의 "항상 표시되는 진행 알림" 요구사항이 트레이 상주로 대체됨).
- `useAlarmSound` 켜짐: Toast에 `<audio src="ms-winsoundevent:Notification.Looping.Alarm">` 지정. 꺼짐(기본값, 폰과 동일): 기본 알림음.

## 설정 저장

`Windows.Storage.ApplicationData.Current.LocalSettings` (key-value, 패키지드 앱 표준 저장소 — 폰의 DataStore와 대응). `SettingsStore` 서비스가 `TimerSettings`와 동일한 필드 집합(진동 제외)을 읽고 쓴다.

## 테스트 계획

- `PomodoroWindows.Core`의 `TimerEngine`/`DailyGoalTracker`/`NotificationContent`는 xUnit으로 유닛 테스트 작성 — 기존 Kotlin `TimerEngineTest`/`DailyGoalTrackerTest`의 케이스를 동일하게 이식해 동작 일치를 보증한다 (경계 통과, 자동시작 온/오프, `adoptRemote` 없이도 재사용 가능한 부분부터).
- `SettingsStore`, `NotificationService`, `TrayIconService`는 인터페이스로 감싸 `TimerViewModel` 유닛 테스트에서는 페이크로 대체.
- WinUI 3 UI 자체는 자동 UI 테스트 범위에 넣지 않고(플랫폼 UI 테스트 셋업 비용이 큼), 수동 검증으로 대체: 타이머 시작→트레이로 축소→백그라운드에서 세션 종료→Toast 확인, 절전 방지 동작 확인, 테마 전환(라이트/다크) 확인.

## 범위 제외 (이번 범위 아님)

- 폰 ↔ PC 동기화 (2단계, 별도 설계 문서)
- 워치와의 직접 통신 (워치는 계속 폰을 허브로만 동기화)
- 온보딩 플로우
- Microsoft Store 배포/코드 서명 자동화 (수동 사이드로드 MSIX로 충분, 배포 파이프라인은 범위 밖)
- macOS/Linux 데스크톱 앱
