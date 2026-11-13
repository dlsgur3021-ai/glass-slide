# Glass Slide v0.4: 폴드8 자동 펼침 테스트

## 목표
갤럭시 Z 폴드8을 접은 상태에서 펼칠 때 라이브 배경화면의 반투명 유리막이 양옆으로 열리는 연출을 **시험**하는 앱입니다.

> **중요**: 삼성의 시스템 전환 애니메이션을 바꾸지는 않습니다. 홈 화면 아이콘도 흐려지지 않습니다. 폴드8 실기기에서 트리거 성공 여부는 미검증입니다.

## Android Studio 없이 APK 만드는 순서

1. GitHub 로그인 → `https://github.com/new`에서 `GlassSlide`라는 **비공개(Private) 또는 공개(Public)** 저장소를 만듭니다. `Add a README file` 체크를 해도 됩니다.
2. 저장소 페이지에서 **Add file → Upload files**로 이동합니다. ZIP을 풀고 그 안의 **파일과 폴더 모두** 업로드합니다. 특히 `.github/workflows/build-apk.yml`이 저장소에 포함돼야 합니다. (숨김 폴더 `.github`를 찾기 어려우면 GitHub에서 직접 `Add file → Create new file`로 `.github/workflows/build-apk.yml`을 추가하고, 제공된 동일한 내용을 붙여넣습니다.)
3. **Commit changes**를 누릅니다. `main` 또는 `master` 브랜치에서 진행해야 합니다.
4. 상단 **Actions → Build Glass Slide APK**를 누릅니다. 처음이라면 Actions 사용을 활성화하고, 자동 빌드가 시작되지 않았으면 **Run workflow → Run workflow**를 누릅니다.
5. 초록색 체크가 표시되면 해당 실행 화면 아래쪽 **Artifacts → GlassSlide-v0.4-debug-APK**를 다운로드합니다.
6. 내려받은 ZIP 안의 `app-debug.apk`를 폴드8으로 옮겨 설치합니다. 폰에서 파일 관리자에서 APK를 열고 안내에 따라 해당 앱에만 '알 수 없는 앱 설치 허용'을 켭니다. 테스트 후 허용을 다시 해제하세요.

### 설치 후 테스트
1. Glass Slide 앱 실행 → **유리 슬라이드 재생**으로 그래픽 확인.
2. **라이브 배경화면 적용**을 눌러 홈 화면에 설정. 가능하면 커버 및 내부 홈 화면에서 동일 라이브 배경화면을 사용하세요.
3. 접은 폴드8의 커버 홈 화면을 확인하고 펼쳐보세요.
4. 앱에서 **기록 새로고침** → `CLOSED → OPEN`, `애니메이션 시작` 기록이 있는지 확인하세요.

### 한계 / 주의
- 기기가 앱에 힌지 각도 센서나 일관된 화면 상태를 제공하지 않으면 자동 실행이 안 될 수 있습니다.
- 기기마다 라이브 배경화면 프로세스가 화면 전환 동안 재시작돼 이벤트가 유실될 수 있습니다.
- Android Studio 없이 GitHub Actions에서 SDK와 Gradle을 설치해 빌드합니다. 이 ZIP 자체는 APK가 아닙니다.
- GitHub의 디버그 APK는 실행할 때마다 임시 디버그 키로 서명될 수 있으므로 이후 버전을 덮어 설치할 수 없으면 기존 앱을 삭제하고 재설치해야 합니다.
- 개인 사진은 앱 내부에 저장되고 인터넷 전송 기능은 없습니다.

## 빌드 구성
- Android Gradle Plugin 8.13.2 / Gradle 8.13 / Kotlin Gradle Plugin 2.2.20 / JDK 17
- Android SDK 36, 최소 Android 10(API 29), 앱 대상 35
- Workflows: `.github/workflows/build-apk.yml` (Actions 자동 업로드)
- AndroidX WindowManager 1.5.1

이 프로젝트는 GitHub Actions 실 빌드 전이므로 **APK 생성 성공을 보증하지 않습니다**. Actions 오류 로그가 나오면 해당 로그로 다음 수정을 진행할 수 있습니다.
