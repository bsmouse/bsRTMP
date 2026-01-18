# DCCL (Digital Camera Connection Live)

안드로이드 기반의 실시간 RTMP 스트리밍 및 플레이어 애플리케이션입니다.

## 🚀 주요 기능
* **RTMP 송출 (Publish):** 전/후면 카메라를 이용한 실시간 방송 송출
* **RTMP 재생 (Play):** Media3를 이용한 저지연(Low Latency) 스트리밍 재생
* **백그라운드 유지:** 포그라운드 서비스를 통한 앱 종료 방지 및 송출 유지
* **환경 설정:** 사용자 지정 송출/재생 URL 저장 기능

## 🛠 기술 스택
* **Language:** Kotlin
* **Media3:** 저지연 라이브 재생 (ExoPlayer 통합 버전)
* **RootEncoder (Pedro):** RTMP 송출 엔진
* **SharedPreferences:** 설정값 저장 관리

## ⚙️ 설정 방법
1. 앱 실행 후 'Settings' 메뉴 진입
2. 본인의 RTMP 서버 주소 입력 (Publish, Play 각각 입력)
3. 'Save' 버튼 클릭 후 송출 또는 재생 시작

## ⚠️ 주의 사항
* 릴리스 빌드 시 `proguard-rules.pro`에 Media3 관련 규칙이 추가되어야 합니다.
* 백그라운드 동작을 위해 '배터리 최적화 예외' 설정이 필요할 수 있습니다.