# 설치 방법

새 버전은 [Releases 페이지](https://github.com/caneo21/pomodoro-app/releases/latest)에 올라와요.
앱이 새 버전을 발견하면 "새 버전 vX.Y.Z이 있어요" 알림이 뜨고, **다운로드**를 누르면 이 페이지가 열려요.

## 휴대폰 (Android)

1. `pomodoro-phone-<버전>.apk`를 휴대폰에서 받아요.
2. 받은 파일을 열고, "출처를 알 수 없는 앱" 설치를 브라우저(또는 파일 앱)에 허용해요.
3. **설치**를 눌러요.

**처음 한 번만:** 예전에 다른 방법(개발용 빌드 등)으로 설치한 앱이 있으면 서명이 달라서 업데이트가 안 돼요.
기존 앱을 삭제한 뒤 설치하세요. 이때 설정과 기록이 초기화돼요. 그 다음부터는 새 APK를 그냥 설치하면 업데이트돼요.

## Windows

1. 한 폴더에 아래 파일을 모두 받아요. 대부분의 PC는 `x64`, ARM 노트북(Snapdragon 등)은 `arm64`예요.
   - `PomodoroWindows-<버전>-x64.msix`
   - `WindowsAppRuntime-x64.msix`
   - `PomodoroWindows.cer`
   - `Install.ps1`
   - `Install.cmd`
2. `Install.cmd`를 더블클릭해요.
3. 처음 설치할 때만 관리자 권한 창이 떠요. **예**를 누르면 인증서를 등록하고 설치를 이어가요.
4. 시작 메뉴에서 **Pomodoro**를 실행해요.

**업데이트할 때:** 새 버전 파일들을 받아서 다시 `Install.cmd`를 실행하면 돼요 (관리자 창은 다시 뜨지 않아요).
새 `.msix`만 더블클릭해서 설치해도 돼요.

**왜 `Install.cmd`인가요?** 인터넷에서 받은 `.ps1` 파일은 Windows 기본 설정이 실행을 막아요.
`Install.cmd`는 이 스크립트 하나만 그 제한 없이 실행해 줘서, 설정을 따로 바꿀 필요가 없어요.

**인증서는 뭔가요?** 이 앱은 스토어를 거치지 않아서 직접 만든 인증서로 서명돼 있어요.
`Install.ps1`이 그 인증서를 이 PC의 "신뢰할 수 있는 사용자" 저장소에 한 번 등록해요.

## 워치 (Wear OS)

워치는 Play 스토어 없이 설치하려면 ADB가 필요해요.

1. 워치에서 **설정 → 시스템 → 정보 → 소프트웨어 정보**에서 소프트웨어 버전을 7번 눌러 개발자 옵션을 켜요.
2. **개발자 옵션 → ADB 디버깅**과 **무선 디버깅**을 켜고, 표시되는 IP 주소와 포트를 확인해요.
3. PC에서 (Android SDK의 `platform-tools` 필요):
   ```
   adb pair <IP>:<페어링 포트>
   adb connect <IP>:<포트>
   adb install pomodoro-watch-<버전>.apk
   ```

워치 앱은 휴대폰 앱과 같은 키로 서명돼 있어서, 둘 다 이 Releases 버전이면 서로 동기화돼요.
