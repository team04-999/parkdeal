---
name: pleos-emulator
description: Use when running, testing, or demoing the app on the Pleos Connect emulator — booting the AVD, changing gear (P/R/N/D), checking behavior while driving, or when Virtual Console returns emulator_inactive / 503, gear commands do nothing, or com.android.car / systemui keep crashing after boot.
---

# Pleos Connect 에뮬레이터 조작

## 핵심

- **cold boot 만 쓴다.** quick boot(스냅샷)에서는 VHAL 을 쓸 수 없고 차량 서비스가 크래시 루프에 빠질 수 있다.
- **기어는 Virtual Console(에뮬레이터 안 :8088)로 바꾼다.** 하트비트가 3초 안에 없으면 명령이 `emulator_inactive`(503)로 거부되므로 `scripts/gear.sh` 를 쓴다.
- **HTTP 200 은 성공이 아니다.** `GET /vehicle/state` 의 `gear.actual.value` 로 실제 기어를 확인한다(스크립트가 한다).

## 빠른 참조

| 할 일 | 명령 |
|---|---|
| cold boot 로 띄우고 부팅 완료까지 대기 | `bash .claude/skills/pleos-emulator/scripts/boot.sh` |
| 기어 변경 + 확인 | `bash .claude/skills/pleos-emulator/scripts/gear.sh Park\|Reverse\|Neutral\|Drive` |
| 설치·실행 | `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.parkdeal.app/.MainActivity` |
| 스크린샷 | `adb exec-out screencap -p > shot.png` (2560×1440) |
| 앱 로그 | `adb logcat -v time --pid=$(adb shell pidof com.parkdeal.app)` |
| 알림 확인 | `adb shell dumpsys notification --noredact \| grep -A3 com.parkdeal.app` |
| 차량 상태 전체 | `adb forward tcp:8088 tcp:8088 && curl -s localhost:8088/vehicle/state` |

## 주행 중 확인

D단에서는 시스템이 화면을 막는다("주행 중에는 사용할 수 없습니다"). adb 로 설치한 앱은 `distractionOptimized` 를
선언해도 막힐 수 있다. 주행 중 동작은 화면 대신 **알림과 TTS** 로 확인한다.

## 자주 겪는 문제

| 증상 | 원인 · 해결 |
|---|---|
| 기어 명령이 503 `emulator_inactive` | 하트비트 없음 → `gear.sh` 사용 |
| `curl localhost:8088` 연결 거부 | `adb forward tcp:8088 tcp:8088` 안 함 (에뮬레이터 재시작 때마다 다시) |
| 부팅 후 com.android.car·systemui 크래시 반복 | 스냅샷 부팅 → `adb emu kill` 후 `boot.sh` 로 cold boot |
| 앱의 기어 리스너가 조용히 멈춤 | `ai.pleos.service` 가 재시작되면 리스너가 끊긴다. 앱에서 주기적으로 현재 기어를 다시 읽는다 |
| cold boot 직후 내비가 경로 요청을 무시 | 부팅 직후 일시 현상. 잠시 뒤 다시 요청 |

AVD 설정 확인: `~/.android/avd/<AVD>.avd/config.ini` 에 `fastboot.forceColdBoot=yes`.
