# 구조와 설계 결정

2차 PT 기술 Q&A에서 팀이 설명할 구조와, 그렇게 정한 이유를 남긴다.
결정마다 날짜, 고른 것, 버린 것, 이유를 적는다.

## 결정 기록

### 기어는 Pleos Vehicle SDK로 읽는다 (2026-11-13)

**고른 것**: Pleos Vehicle SDK의 `GearSelectionListener` 로 기어 변경을 받는다.

**버린 것**: AAOS 표준 `CarPropertyManager`(`VehiclePropertyIds.GEAR_SELECTION`).
Pleos 설계 가이드는 기어 같은 표준 차량 데이터에 이 API를 **권장**한다([`pleos-guidelines.md`](pleos-guidelines.md) 4절).

**이유**

- 공모전 개발 범위가 공개된 Pleos Playground SDK·API다. 쓸 수 있는 SDK에 Vehicle이 명시돼 있다([`contest.md`](contest.md)). 표준 API는 이 범위에 드는지 해석이 갈릴 수 있다
- 평가 항목 "기술 활용성"은 Pleos SDK를 핵심 기능에 썼는지를 본다. 앱의 핵심인 "P단 진입 → 주차 세션 시작"을 Vehicle SDK로 만든다
- 프로토타입에서 Virtual Console 기어 조작을 이 리스너로 받는 것까지 확인했다
- 가이드의 표준 API 우선은 권장이고, 가이드는 SDK API를 계속 지원한다고 적고 있다

**표준 API 권장에 대한 대응**

- `core/` 에 기어 입력 인터페이스(`GearSource`)를 두고, `app/` 에서 Vehicle SDK로 구현한다
- 주차 세션 로직은 인터페이스만 본다. JVM 테스트에서 가짜 기어 소스로 P → D 전환을 검증한다
- 양산 차량 탑재 때는 구현체만 `CarPropertyManager` 로 바꾼다

**지킬 것**

- 기어는 리스너로만 받는다. 폴링하지 않는다 (프로토타입은 5초 폴링을 함께 썼다)
- 쓰기 전에 Vehicle SDK로 지원 여부를 확인하고, 지원하지 않으면 사용자가 직접 주차를 시작하게 한다
- 기어는 주차 세션 시작·종료 판단에만 쓴다. 주행 중 화면 제한은 `CarUxRestrictions` 로 판단한다

상태: 구현됨 (2026-11-14). `core/.../parking/ParkingTracker.kt`(`GearSource`, 기어 → 주차 시작·출차),
`app/.../parking/VehicleGearSource.kt`(Vehicle SDK 리스너). 기어를 읽을 수 없으면 `ParkingTracker.startNow()` 로 직접 시작한다.

### 포그라운드 서비스는 할 일이 있을 때만 돈다 (2026-11-14)

**고른 것**: 주차할 후보 주차장을 정했거나 주차 중일 때만 `ParkingService` 를 띄운다. 출차 안내(알림 + 음성)를 마치면 스스로 멈춘다.

**버린 것**: 앱을 켜면 항상 도는 서비스 + 5초 기어 폴링(프로토타입).

**이유**

- 주행 중에는 시스템이 앱 화면을 가린다. 화면이 가려진 동안에도 P 진입·출차를 받으려면 서비스가 필요하다
- 가이드는 백그라운드 타이머·폴링을 줄이라고 한다([`pleos-guidelines.md`](pleos-guidelines.md) 3절). 무료 종료 경고도 주기적으로 깨지 않고 타이머 한 번으로 건다
- 주차 상태는 SharedPreferences 에 JSON 으로 저장한다. 주차 중 프로세스가 죽어도 다시 뜨면 이어받는다(에뮬레이터에서 강제 종료로 확인)

**TTS**: 읽기 전에 오디오 포커스(`GAIN_TRANSIENT_MAY_DUCK`, 내비 안내 용도)를 받고 180ms 뒤에 읽는다. 바로 읽으면 첫 음절이 잘린다. 끝나면 포커스를 돌려준다.
