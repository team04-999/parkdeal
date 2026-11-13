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

상태: 결정됨, 구현 전.
