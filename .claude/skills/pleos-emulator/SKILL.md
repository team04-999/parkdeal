---
name: pleos-emulator
description: Use when running, testing, or demoing the app on the Pleos Connect emulator — booting the AVD, changing gear (P/R/N/D), checking behavior while driving, or when Virtual Console returns emulator_inactive / 503, gear commands do nothing, or com.android.car / systemui keep crashing after boot.
---

# Pleos Connect 에뮬레이터 조작

## 핵심

- **cold boot 만 쓴다.** quick boot(스냅샷)에서는 VHAL 을 쓸 수 없고 차량 서비스가 크래시 루프에 빠질 수 있다.
- **기어는 Virtual Console(에뮬레이터 안 :8088)로 바꾼다.** 하트비트가 3초 안에 없으면 명령이 `emulator_inactive`(503)로 거부되므로 `scripts/gear.sh` 를 쓴다.
- **HTTP 200 은 성공이 아니다.** `GET /vehicle/state` 의 `gear.actual.value` 로 실제 기어를 확인한다(스크립트가 한다).
- **이 HTTP API 는 공식 문서에 없다.** [Virtual Console 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)는
  브라우저 UI(Emulation Controls)만 다룬다. 에뮬레이터 버전이 바뀌면 깨질 수 있으니 엔드포인트·필드는
  `vc.sh catalog` 로 그때그때 확인한다.
- 다른 클라이언트(열어 둔 Virtual Console 브라우저 창)도 같은 상태를 바꾼다. 시나리오를 돌리기 전에 `vc.sh get` 으로 시작 상태를 본다.

## 빠른 참조

| 할 일 | 명령 |
|---|---|
| cold boot 로 띄우고 부팅 완료까지 대기 | `bash .claude/skills/pleos-emulator/scripts/boot.sh` |
| 기어 변경 + 확인 | `bash .claude/skills/pleos-emulator/scripts/gear.sh Park\|Reverse\|Neutral\|Drive` |
| 상태 읽기 | `bash .claude/skills/pleos-emulator/scripts/vc.sh get body.door_locks.all` (경로 없으면 전체) |
| 상태 쓰기 | `bash .claude/skills/pleos-emulator/scripts/vc.sh set body doors_locked=true` |
| 쓸 수 있는 기능 찾기 | `bash .claude/skills/pleos-emulator/scripts/vc.sh catalog seat` (63개: 문·좌석·조명·공조·배터리…) |
| 설치·실행 | `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.parkdeal.app/.MainActivity` |
| 스크린샷 | `adb exec-out screencap -p > shot.png` (2560×1440) |
| 앱 데이터 보기 | `adb shell run-as com.parkdeal.app --user $(adb shell am get-current-user \| tr -d '\r') ls files` — AAOS 는 앱이 사용자 10 으로 돈다. `--user` 없이 보면 빈 폴더(사용자 0)를 본다 |
| 앱 데이터 지우기 | `adb shell pm clear --user $(adb shell am get-current-user \| tr -d '\r') com.parkdeal.app` |
| 오프라인 | `adb shell cmd connectivity airplane-mode enable` / `disable` — **켜면 Virtual Console 브리지가 끊긴다**(아래 표) |
| 앱 로그 | `adb logcat -v time --pid=$(adb shell pidof com.parkdeal.app)` |
| 알림 확인 | `adb shell dumpsys notification --noredact \| grep -A3 com.parkdeal.app` |
| 차량 상태 전체 | `adb forward tcp:8088 tcp:8088 && curl -s localhost:8088/vehicle/state` |

## 주차 시나리오에 쓰는 상태

| 상태 | 읽기 (`vc.sh get`) | 쓰기 (`vc.sh set`) |
|---|---|---|
| 기어 | `gear.actual.value` | `gear.sh Park` |
| 문 잠금(전체) | `body.door_locks.all` | `body doors_locked=true` |
| 앞좌석 왼쪽 문 열림 | `body.doors.front_left` | `body door_fl=true` (`door_fr`·`door_rl`·`door_rr`) |
| 운전석 탑승 | `seat.occupied.driver` | `seat area=driver kind=occupied value=false` |
| 속도·페달 | `controls.speed_kph`, `controls.brake_pct` | **PUT** `/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0` (`gear.sh Park` 가 먼저 한다) |

## 주행 중 확인

ParkDeal 은 `distractionOptimized` 를 선언한다. 주행 제한 중에는 줄인 화면(고른 곳 + 짧은 목록)이 보여야 한다.
선언하지 않은 앱은 "주행 중에는 사용할 수 없습니다"로 막힌다.
주행 제한을 걸려면 D단에서 속도를 올린다: 하트비트를 유지하면서
`PUT /vehicle/controls?speed=30&accelerator=20&brake=0&steering=0`. 현재 주행 규제 상태는
`adb shell dumpsys car_service | grep "UXR:"` 로 본다 (`DO: true UxR: …` 면 규제 중).
Pleos 문서의 `cmd hmg_car_service … driving_regulation on|off` 는 실차용이라 에뮬레이터에는 없다.

## 자주 겪는 문제

| 증상 | 원인 · 해결 |
|---|---|
| 기어 명령이 503 `emulator_inactive` | 하트비트 없음 → `gear.sh` 사용 |
| `curl localhost:8088` 연결 거부 | `adb forward tcp:8088 tcp:8088` 안 함 (에뮬레이터 재시작 때마다 다시) |
| 부팅 후 com.android.car·systemui 크래시 반복 | 스냅샷 부팅 → `adb emu kill` 후 `boot.sh` 로 cold boot |
| 앱의 기어 리스너가 조용히 멈춤 | `ai.pleos.service` 가 재시작되면 리스너가 끊긴다. 폴링으로 메우지 말고(가이드: 폴링 금지) 서비스 재연결 때 리스너를 다시 등록한다 |
| 비행기 모드를 켰다 끈 뒤 모든 쓰기가 503 `emulator_inactive`, `vc.sh get controls.emulator_active` 가 `false` | 차량 신호 브리지(DDS)가 끊겼고 저절로 붙지 않는다. `adb emu kill` 후 `boot.sh`. 오프라인 확인은 마지막에 하고 끝나면 다시 띄운다 |
| cold boot 직후 내비가 경로 요청을 무시 | 부팅 직후 일시 현상. 잠시 뒤 다시 요청 |

AVD 설정 확인: `~/.android/avd/<AVD>.avd/config.ini` 에 `fastboot.forceColdBoot=yes`.
