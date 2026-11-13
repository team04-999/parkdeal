# ParkDeal

Pleos Connect(현대차그룹 AAOS)용 주차 도우미 앱. 목적지 주변 주차장을 **실제로 낼 돈과 걷는 거리** 기준으로
추천하고, 가게 주차 혜택과 출차 리마인드까지 챙깁니다.

현대차그룹 차량용 앱 개발 공모전 출품작.

## 실행 방법

<!-- 프로젝트 뼈대 PR 에서 채운다 -->

## 테스트 환경과 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 시스템 이미지 | `system-images;android-34;pleos_car_emulator;arm64-v8a` (Android 14 / API 34) |
| 에뮬레이터 빌드 | `release_connect_v2.1.0` |
| 화면 규격 | 2560×1440, 160dpi |
| AVD 부팅 옵션 | cold boot (quick boot 에서는 VHAL 을 쓸 수 없다) |

<!-- 권한·설정 절차는 프로젝트 뼈대 PR 에서 채운다 -->

## APK

`apk/` 폴더 (1차 제출 전 추가)

## 문서

- [공모전 규칙 정리](docs/contest.md)
- [기여 방법](CONTRIBUTING.md)
