# 팀 개발 TODO

이 문서는 fork한 팀원이 가장 먼저 읽는 작업 목록이다. 작업을 시작하기 전에
[`docs/contracts.md`](docs/contracts.md)와 담당 모듈의 `README.md`를 읽는다.

## 0. 처음 한 번만

- [ ] Java 21 이상을 설치한다.
- [ ] `./gradlew clean test`가 성공하는지 확인한다.
- [ ] `./gradlew :app:bootRun`으로 서버가 실행되는지 확인한다.
- [ ] 본인 담당 TODO 번호로 브랜치를 만든다. 예: `feat/adapter-01-order-status`
- [ ] 하나의 PR에는 하나의 TODO만 포함한다.

## 작업 순서

```text
CORE-01 ──┬── ADAPTER-01
          └── ADAPTER-02

CORE-02 ───── SCENARIO-04 ───── APP-01

SCENARIO-01 → SCENARIO-02 → SCENARIO-03
ADAPTER-03, ADAPTER-04는 독립적으로 진행 가능
```

## Core 담당 — 팀장

팀원이 기다리지 않도록 `CORE-01`, `CORE-02`를 먼저 병합한다.

### CORE-01 주문 조회 계약

- [x] `OrderQuery`를 추가한다.
- [x] `OrderView`를 추가한다.
- [x] `EnginePort.query(OrderQuery)`를 추가한다.
- [x] 조회도 DES 큐를 통과하게 한다.
- [x] 접수·부분체결·체결·취소 상태 조회 테스트를 작성한다.

완료 조건:

```bash
./gradlew :core:test
```

### CORE-02 FillPlanProvider 계약

- [ ] `FillPlanProvider` 인터페이스를 core에 추가한다.
- [ ] `SimulationEngine` 생성자로 주입한다.
- [ ] 기존 하드코딩된 `fillRatio`, `fillDelay`를 제거한다.
- [ ] 주문 접수 시 `FillPlan`을 한 번만 생성한다.
- [ ] 고정 Provider를 사용한 엔진 테스트를 작성한다.

### CORE-03 취소와 계좌 불변식 보강

- [ ] 부분체결 후 미체결 전량 취소를 검증한다.
- [ ] 완료·취소·거부 주문의 재취소를 막는다.
- [ ] 사건 처리 후 계좌 불변식이 항상 검사되는지 확인한다.
- [ ] 같은 입력에서 같은 이벤트 로그가 생성되는지 확인한다.

## LS API 담당

상세 시작 방법은 [`adapter-ls/README.md`](adapter-ls/README.md)를 따른다.

### ADAPTER-01 주문 상태·미체결 조회

선행 조건: `CORE-01`

- [ ] LS 공식 콘솔에서 대상 TR 요청·응답을 확인한다.
- [ ] 민감 정보를 제거한 fixture를 저장한다.
- [ ] `OrderStatusHandler`를 구현한다.
- [ ] `EnginePort.query(OrderQuery)`만 사용한다.
- [ ] 접수·부분체결·체결·취소 응답 테스트를 작성한다.
- [ ] 존재하지 않는 주문의 LS 오류 응답을 작성한다.

### ADAPTER-02 미체결 전량 취소

선행 조건: `CORE-01`

- [ ] LS 공식 콘솔에서 취소 TR 요청·응답을 확인한다.
- [ ] `CancelOrderHandler`를 구현한다.
- [ ] `EnginePort.cancel(CancelOrder)`만 사용한다.
- [ ] 부분체결 30주 이후 미체결 70주 취소 응답을 테스트한다.
- [ ] 이미 체결·취소된 주문의 거부 응답을 테스트한다.

### ADAPTER-03 Mock 토큰 발급

- [ ] 토큰 발급 URL과 응답 fixture를 확인한다.
- [ ] 실제 보안 검증 없이 mock 토큰을 발급한다.
- [ ] 외부에서 TTL 값을 전달받을 수 있게 한다.
- [ ] 정상 발급과 잘못된 본문 테스트를 작성한다.

### ADAPTER-04 공통 LS 오류 봉투

- [ ] `INVALID_REQUEST`를 LS 오류로 변환한다.
- [ ] `ORDER_NOT_FOUND`를 LS 오류로 변환한다.
- [ ] `INSUFFICIENT_FUNDS`를 LS 오류로 변환한다.
- [ ] `ILLEGAL_ORDER_STATE`를 LS 오류로 변환한다.
- [ ] core에 `rsp_cd`, TR, InBlock 용어를 추가하지 않는다.

### ADAPTER-05 계약 fixture 테스트

- [ ] 다섯 API fixture의 필드 이름과 JSON 구조를 비교한다.
- [ ] 실제 값이 아니라 구조와 타입을 검증한다.
- [ ] 계좌번호와 토큰 등 민감 정보가 없는지 확인한다.

완료 조건:

```bash
./gradlew :adapter-ls:test
```

## 시나리오 담당

상세 시작 방법은 [`scenario/README.md`](scenario/README.md)를 따른다.

### SCENARIO-01 YAML record 검토

- [ ] `ScenarioSpec`과 예제 YAML의 필드가 일치하는지 확인한다.
- [ ] 모든 필드가 필요한지 팀장에게 확인한다.
- [ ] 알 수 없는 필드가 거부되는 테스트를 유지한다.
- [ ] 실행 훅, 클래스 이름, URL 같은 실행 능력을 YAML에 추가하지 않는다.

### SCENARIO-02 의미 검증기 완성

- [ ] `ratio`는 0 초과 1 이하만 허용한다.
- [ ] `ratio`와 `quantity` 동시 사용을 거부한다.
- [ ] 체결 비율 합계가 1을 넘으면 거부한다.
- [ ] 음수 지연을 거부한다.
- [ ] `rate_limit.per_sec < 1`을 거부한다.
- [ ] `token_ttl <= 0`을 거부한다.
- [ ] 각 규칙마다 테스트를 하나씩 작성한다.

### SCENARIO-03 시간 문자열 파서

- [x] `500ms`, `1s`, `10s`, `24h`를 `Duration`으로 변환한다. `10m`도 지원한다.
- [x] 단위가 없는 문자열을 거부한다.
- [x] 음수 시간을 거부한다. 0, 소수, 공백 포함, 알 수 없는 단위, overflow도 거부한다.
- [ ] 파싱 오류에 YAML 필드 경로를 포함한다. → `SCENARIO-02`로 옮긴다.

`DurationParser`는 자기가 어느 필드에서 왔는지 모르므로 필드 경로를 만들 수 없다. 파서는
잘못된 값만 알리고, `ScenarioValidator`가 이를 잡아 `ValidationIssue(field, message)`로
경로를 붙인다. 마지막 항목은 `SCENARIO-02`에서 검증기가 파서를 호출할 때 충족된다.

### SCENARIO-04 FillPlanProvider 구현

선행 조건: `CORE-02`

- [ ] `ScenarioFillPlanProvider`를 구현한다.
- [ ] `ratio`를 최초 주문 수량 기준 실제 수량으로 바꾼다.
- [ ] 모든 `after`를 주문 접수 시각 기준으로 해석한다.
- [ ] 100주와 0.3에서 30주가 생성되는지 테스트한다.
- [ ] 같은 시나리오와 seed에서 같은 계획이 생성되는지 테스트한다.

### SCENARIO-05 운영 제약 정책

- [ ] `RateLimitPolicy`를 구현한다.
- [ ] `TokenExpiryPolicy`를 구현한다.
- [ ] 시스템 시각 대신 전달받은 가상 시각을 사용한다.
- [ ] 정책 클래스는 HTTP 응답이나 LS JSON을 생성하지 않는다.

### SCENARIO-06 응답 지연 정책

- [ ] `PLACE_ORDER`와 `CANCEL` operation을 구분한다.
- [ ] `BEFORE_COMMIT`, `AFTER_COMMIT`을 구분한다.
- [ ] 지연 시간을 반환하는 정책만 구현한다.
- [ ] 정책 클래스에서 `Thread.sleep()`을 호출하지 않는다.

### SCENARIO-07 카탈로그

- [ ] `normal-fill.yml` 기대 결과를 문서화한다.
- [ ] `partial-fill.yml` 기대 결과를 문서화한다.
- [ ] `response-delay-after-commit.yml` 기대 결과를 문서화한다.
- [ ] 잘못된 YAML 예시와 검증 테스트를 추가한다.

완료 조건:

```bash
./gradlew :scenario:test
```

## 통합 담당 — 팀장

### APP-01 시나리오 조립

선행 조건: `CORE-02`, `SCENARIO-04`

- [ ] 시작 시 YAML 경로를 설정으로 받는다.
- [ ] `ScenarioLoader`와 `ScenarioValidator`를 실행한다.
- [ ] 검증 실패 시 서버 시작을 중단하고 오류를 표시한다.
- [ ] `ScenarioFillPlanProvider`를 `SimulationEngine`에 주입한다.
- [ ] 서버 시작 시 계좌·주문·event seq가 초기화되는지 확인한다.

### APP-02 MVP 전체 흐름 테스트

- [ ] 토큰 발급
- [ ] 초기 잔고 10,000,000원 조회
- [ ] 70,000원에 100주 매수
- [ ] 30주 부분체결 조회
- [ ] 미체결 70주 취소
- [ ] 최종 현금 7,900,000원 확인
- [ ] 잠긴 현금 0원 확인
- [ ] 보유 수량 30주 확인

## 이번 대회 MVP에서 보류

아래 작업은 별도 합의 없이 시작하지 않는다.

- WebSocket 체결통보
- 통보 유실·중복
- 다른 증권사 adapter
- 매도·정정
- 실제 시장 데이터
- 데이터베이스 영구 저장
- 실행 중 시나리오 교체
- 복잡한 대시보드
