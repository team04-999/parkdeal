# 빡딜 (ParkDeal)

**목적지 가게의 주차 혜택까지 계산해, 실제로 낼 돈이 가장 적은 주차장을 골라 주고 출차 때 혜택 등록을 챙겨 주는 Pleos Connect 앱.**

현대차그룹 차량용 앱 개발 공모전 출품작 (team04).

## 무엇을 하나

| 순간 | 빡딜이 하는 일 | 쓰는 Pleos SDK |
|---|---|---|
| 내비에 목적지 설정 | 목적지 가게의 주차 혜택(예: "이용 시 2시간 무료")과 주변 주차장 요금을 합쳐 **머무를 시간 동안 실제 낼 돈 + 걷는 거리**로 추천. 주행 중이라 화면 대신 음성으로 브리핑 | NaviHelper, TextToSpeech |
| 정차 후 | 주차 비교 화면에서 고른 주차장으로 내비 경로 변경 | NaviHelper |
| P단 진입 | 그 주차장으로 주차 기록 시작. 무료 시간이 15분 남으면 음성·알림 | Vehicle(기어), TextToSpeech |
| 출차 (P → D/R) | "○○에서 무료 주차 등록하셨나요? 등록하지 않으면 약 18,000원이 나와요" 음성·알림 | Vehicle(기어), TextToSpeech |
| 출차 확인 후 | 주차 기록을 쌓아 이번 달 낸 돈과 **혜택으로 아낀 금액**을 보여 줌 | — |

주행 중(주행 제한 상태)에는 고른 주차장 하나와 짧은 후보 목록만 보여주고, 첫 안내는 음성으로 한다. 정차하면 전체 비교 화면이 열린다.

## APK

[`apk/team04_parkdeal_release.apk`](apk/team04_parkdeal_release.apk) — 실행 가능한 release 빌드

```bash
adb install -r apk/team04_parkdeal_release.apk
adb shell am start -n com.parkdeal.app/.MainActivity
```

첫 실행 때 알림 권한을 허용한다.

## 테스트 환경과 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 시스템 이미지 | `system-images;android-34;pleos_car_emulator;arm64-v8a` (42dot, Android 14 / API 34) |
| 에뮬레이터 빌드 | `release_connect_v2.1.0` |
| 화면 규격 | 2560×1440, 160dpi |
| AVD 부팅 옵션 | **cold boot** (quick boot 에서는 VHAL 을 쓸 수 없어 기어를 읽지 못한다) |
| 앱 권한 | 기어 `pleos.car.permission.CAR_DRIVE`, 내비 `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`, 음성 `TTS_SERVICE`, 알림 `POST_NOTIFICATIONS` |
| 인터넷 | 있으면 주차 데이터 서버(`parkdeal-api.daeuk.cloud`)에서 최신 값을 받는다. 없으면 마지막으로 받은 값 → 앱에 넣은 데모 목적지 데이터 순으로 동작 |

### 시연 순서 (에뮬레이터)

1. 앱을 켠다. 내비 목적지가 없으면 데모 목적지(강남역 필앤필버거) 기준 주차 비교가 나온다
2. 내비에서 목적지를 설정하면 그 목적지로 추천이 바뀌고 음성 브리핑이 나온다
3. "1번으로 안내"를 누르면 내비 경로가 그 주차장으로 바뀐다
4. Virtual Console 에서 기어를 D → P 로 바꾸면 주차 기록이 시작된다
5. P → D 로 바꾸면 출차 알림과 음성 안내가 나온다

## 직접 빌드하기

필요한 것: JDK 17, Android SDK(API 35)

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # Android Studio 를 쓰면 자동 생성

./gradlew :core:test :app:testDebugUnitTest   # 단위 테스트
./gradlew :app:assembleDebug                  # debug APK
```

- Pleos SDK 는 `https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 받는다 (`settings.gradle.kts`)
- release 서명 키는 저장소에 없다. `~/.parkdeal-keys/signing.properties`(또는 `PARKDEAL_SIGNING` 경로)가 있으면 `assembleRelease` 가 서명한다

## 구조

| 모듈 | 내용 |
|---|---|
| `app/` | Android 앱 — Pleos SDK(Vehicle·NaviHelper·TextToSpeech), 화면, 포그라운드 서비스 |
| `core/` | 순수 Kotlin — 요금 계산, 추천, 주차 세션, 목적지 추적. Android 의존 없이 JVM 에서 테스트 |

설계 결정과 이유: [`docs/architecture.md`](docs/architecture.md)

## 데이터 출처

가게 주차 혜택과 주차장 정보는 **네이버 지도** 가게 정보를 정리한 것이다. 원문은 저장소에 넣지 않고, 구조화한 값(무료 시간, 요금 규칙, 좌표)만 쓴다.
자세한 내용: [`docs/data.md`](docs/data.md)

## 문서

- [공모전 규칙 정리](docs/contest.md)
- [Pleos 설계 가이드 중 따르는 것](docs/pleos-guidelines.md)
- [구조와 설계 결정](docs/architecture.md)
- [데이터](docs/data.md)
- [기여 방법](CONTRIBUTING.md)
