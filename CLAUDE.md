# ParkDeal 작업 규칙

Pleos Connect(현대차그룹 AAOS) 주차 도우미 앱. 현대차그룹 차량용 앱 개발 공모전 출품작이다.
**이 저장소는 공개되고 심사위원이 커밋 기록까지 본다.** 아래 규칙은 그 전제에서 나왔다.

## 소통

- 사용자에게 보이는 글은 전부 한국어로 쓴다 (커밋·PR·문서·주석 포함). 코드 식별자는 영어.

## 커밋과 PR

- 형식과 절차는 [`CONTRIBUTING.md`](CONTRIBUTING.md)를 따른다. 요약:
  - `type(scope): 한국어 요약` — 예: `feat(parking): 출차 시 예상 요금 안내`
  - `main` 에 직접 커밋하지 않는다. `feat/…`·`fix/…` 브랜치 → PR → squash merge
  - PR 본문에 **왜** 바꿨는지와 **어떻게 확인했는지**를 쓴다
- 커밋·PR 에 `Co-Authored-By` 등 AI 공동 작성자 표시를 넣지 않는다.
- 커밋 author 를 `-c user.name/email` 로 덮어쓰지 않는다. 이 레포의 로컬 git 설정
  (GitHub noreply 주소)을 그대로 쓴다.

## 공개 저장소에 넣지 않는 것

- 비밀값: API 키, 서명 키(`*.jks`, `*.keystore`), `local.properties`
- 데이터 원문: 가게·주차 정보 수집 원문. 앱에 넣는 데이터는 가공한 구조화 값만
- 서버 코드와 인프라 세부: 서버는 별도 비공개 레포다. 여기에는 API 주소와 응답 형식만 둔다

## 구조

```
app/      Android 앱 — Pleos SDK, 화면, 포그라운드 서비스, Gleo
core/     순수 Kotlin — 요금 계산, 추천, 주차 세션 (Android 의존 없음, JVM 단위 테스트)
docs/     공모전 규칙, 제품, 구조, 데이터 문서
apk/      심사용 release APK (공모전 제출 요건)
scripts/  데이터·빌드 보조 스크립트
```

## 개발

- 새 기능과 버그 수정은 실패하는 테스트부터 쓴다. 로직은 `core/` 에 두고 JVM 테스트로 검증한다.
- 2차 PT 기술 Q&A 에서 팀이 구조와 로직을 직접 설명해야 한다. 설계 결정은 PR 본문과
  `docs/architecture.md` 에 남겨 설명 자료가 되게 한다.
- 대외 공개 SDK 만 쓴다: Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation.
  Gleo(AI App Schema) 연동은 팀이 승인을 요청할 수 없으므로 없어도 동작해야 한다.
- 주행 중(D단) 화면 사용이 제한된다. 주행 중 안내는 TTS·알림으로 한다.
- 에뮬레이터: AVD 는 cold boot 로 설정한다 (quick boot 에서는 VHAL 을 쓸 수 없다).
