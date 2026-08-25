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

- [x] `FillPlanProvider` 인터페이스를 core에 추가한다.
- [x] `SimulationEngine` 생성자로 주입한다.
- [x] 기존 하드코딩된 `fillRatio`, `fillDelay`를 제거한다.
- [x] 주문 접수 시 `FillPlan`을 한 번만 생성한다.
- [x] 고정 Provider를 사용한 엔진 테스트를 작성한다.

### CORE-03 취소와 계좌 불변식 보강

- [ ] 부분체결 후 미체결 전량 취소를 검증한다.
- [ ] 완료·취소·거부 주문의 재취소를 막는다.
- [ ] 사건 처리 후 계좌 불변식이 항상 검사되는지 확인한다.
- [ ] 같은 입력에서 같은 이벤트 로그가 생성되는지 확인한다.

## LS API 담당

상세 시작 방법은 [`adapter-ls/README.md`](adapter-ls/README.md)를 따른다.

### ADAPTER-01 주문 상태·미체결 조회

선행 조건: `CORE-01`

- [x] LS 공식 콘솔에서 대상 TR 요청·응답을 확인한다.
- [x] 민감 정보를 제거한 fixture를 저장한다.
- [x] `OrderStatusHandler`를 구현한다.
- [x] `EnginePort.query(OrderQuery)`만 사용한다.
- [x] 접수·부분체결·체결·취소 응답 테스트를 작성한다.
- [x] 공식 목록 입력과 주문이 없는 빈 목록 응답을 작성한다.

core의 단건 조회 계약은 유지하고, adapter가 발급한 숫자 LS 주문번호 목록을 순회해
공식 t0425 목록 응답을 만든다.

### ADAPTER-02 미체결 전량 취소

선행 조건: `CORE-01`

- [x] LS 공식 콘솔에서 취소 TR 요청·응답을 확인한다.
- [x] `CancelOrderHandler`를 구현한다.
- [x] `EnginePort.cancel(CancelOrder)`만 사용한다.
- [x] 부분체결 30주 이후 미체결 70주 취소 응답을 테스트한다.
- [x] 이미 체결·취소된 주문의 거부 응답을 테스트한다.

### ADAPTER-03 Mock 토큰 발급

- [x] 토큰 발급 URL과 응답 fixture를 확인한다.
- [x] 실제 보안 검증 없이 mock 토큰을 발급한다.
- [x] 외부에서 TTL 값을 전달받을 수 있게 한다.
- [x] 정상 발급과 잘못된 본문 테스트를 작성한다.

### ADAPTER-04 공통 LS 오류 봉투

- [x] `INVALID_REQUEST`를 LS 오류로 변환한다.
- [x] `ORDER_NOT_FOUND`를 LS 오류로 변환한다.
- [x] `INSUFFICIENT_FUNDS`를 LS 오류로 변환한다.
- [x] `ILLEGAL_ORDER_STATE`를 LS 오류로 변환한다.
- [x] core에 `rsp_cd`, TR, InBlock 용어를 추가하지 않는다.

`rsp_cd` 값은 LS 공식 콘솔 확인 전까지 임시값이다. 409와 429는 AGENTS.md 6절의 관측
목록에 없는 추정값이며 `ADAPTER-05` 확인 대상이다.

core가 아직 `CoreException`을 던지지 않는 경로는 `LsErrorMapper.classify()`가 예외 타입으로
임시 판정한다. 증거금 부족은 예외가 아니라 `OrderResult(REJECTED)`로 오므로 이 봉투에
도달하지 않고, 취소 실패는 도달하되 코드가 아닌 타입으로 판정된다. `CORE-03`에서 core가
`CoreException(INVALID_REQUEST / ORDER_NOT_FOUND / ILLEGAL_ORDER_STATE)`을 던지면
`classify()`의 타입 판정 갈래를 제거한다.

### ADAPTER-05 계약 fixture 테스트

- [x] 다섯 API fixture의 필드 이름과 JSON 구조를 비교한다.
- [x] 실제 값이 아니라 구조와 타입을 검증한다.
- [x] 계좌번호와 토큰 등 민감 정보가 없는지 확인한다.

완료 조건:

```bash
./gradlew :adapter-ls:test
```

## 시나리오 담당

상세 시작 방법은 [`scenario/README.md`](scenario/README.md)를 따른다.

### SCENARIO-01 YAML record 검토

- [x] `ScenarioSpec`과 예제 YAML의 필드가 일치하는지 확인한다.
- [ ] 모든 필드가 필요한지 팀장에게 확인한다. → 아래 질문 3개 대기
- [x] 알 수 없는 필드가 거부되는 테스트를 유지한다.
- [x] 실행 훅, 클래스 이름, URL 같은 실행 능력을 YAML에 추가하지 않는다.

`ScenarioCatalogTest`가 `scenarios/` 카탈로그 전체를 읽어 spec 일치를 검증한다. 이전에는
test resources fixture만 읽어서 카탈로그가 어긋나도 아무 테스트가 깨지지 않았다.

팀장 확인이 필요한 항목:

1. `seed`를 어떤 코드도 읽지 않는다. contracts.md가 "체결 시점에 난수를 사용하지 않는다"고
   정한 이상 결정론에 seed가 필요한지 불분명하다. 유지할지 결정이 필요하다.
2. `FillSpec.quantity`를 어떤 카탈로그 YAML도 쓰지 않는다. MVP를 `ratio`만으로 갈지
   확인이 필요하다. 유지한다면 `SCENARIO-07`에서 예제를 추가해야 한다.
3. `constraints`(`rate_limit`, `token_ttl`)를 쓰는 카탈로그 예제가 없다. `SCENARIO-05`
   정책을 구현해도 사용자가 참고할 YAML이 없으므로 `SCENARIO-07`에서 추가해야 한다.

### SCENARIO-02 의미 검증기 완성

- [x] `ratio`는 0 초과 1 이하만 허용한다.
- [x] `ratio`와 `quantity` 동시 사용을 거부한다. 둘 다 없는 경우도 거부한다.
- [x] 체결 비율 합계가 1을 넘으면 거부한다.
- [x] 음수 지연을 거부한다. 0, 소수, 단위 없는 값도 거부한다.
- [x] `rate_limit.per_sec < 1`을 거부한다.
- [x] `token_ttl <= 0`을 거부한다.
- [x] 각 규칙마다 테스트를 하나씩 작성한다.

검증기는 첫 오류에서 멈추지 않고 모든 문제를 모아 반환한다. 사용자가 시나리오를 한 번에
고칠 수 있어야 하기 때문이다.

6개 규칙 외에 `faults.response.on`과 `timing`의 열거값 검증을 추가했다. 검증하지 않으면
`PLACE_ORDR` 같은 오타가 `SCENARIO-06` 정책까지 그대로 흘러간다.

### SCENARIO-03 시간 문자열 파서

- [x] `500ms`, `1s`, `10s`, `24h`를 `Duration`으로 변환한다. `10m`도 지원한다.
- [x] 단위가 없는 문자열을 거부한다.
- [x] 음수 시간을 거부한다. 0, 소수, 공백 포함, 알 수 없는 단위, overflow도 거부한다.
- [x] 파싱 오류에 YAML 필드 경로를 포함한다. `SCENARIO-02`의 검증기가 붙인다.

`DurationParser`는 자기가 어느 필드에서 왔는지 모르므로 필드 경로를 만들 수 없다. 파서는
잘못된 값만 알리고, `ScenarioValidator`가 이를 잡아 `ValidationIssue(field, message)`로
경로를 붙인다. 마지막 항목은 `SCENARIO-02`에서 검증기가 파서를 호출할 때 충족된다.

### SCENARIO-04 FillPlanProvider 구현

선행 조건: `CORE-02`

- [x] `ScenarioFillPlanProvider`를 구현한다.
- [x] `ratio`를 최초 주문 수량 기준 실제 수량으로 바꾼다.
- [x] 모든 `after`를 주문 접수 시각 기준으로 해석한다.
- [x] 100주와 0.3에서 30주가 생성되는지 테스트한다.
- [x] 같은 시나리오와 seed에서 같은 계획이 생성되는지 테스트한다.

`origin/feat/core-02-fill-plan-provider`의 계약은 `FillPlan create(long orderQuantity)`
하나다. core는 시나리오 타입을 몰라야 하므로 `ExecutionSpec`을 인자로 받을 수 없다.
그래서 설정을 생성자로 올리고 `create(long)`만 남겼다. `CORE-02`가 머지되면
`implements FillPlanProvider`만 추가하면 된다.

`after` 파싱은 생성 시 한 번만 한다. `create`는 DES 엔진 스레드에서 주문마다 호출되므로
거기서 정규식을 돌리지 않는다.

내림 규칙은 core의 `FillPlan.partial`과 같다. 내림 결과가 0이면 `FillStep`이 0 수량을
거부하므로 최소 1주를 보장한다. 그 결과 **분할체결은 step 수 이상의 주문 수량을 요구한다.**
검증기는 주문 수량을 모르므로 이 조건은 `create`에서 잡고 메시지에 필요한 수량을 밝힌다.
난수를 쓰지 않아 `seed`는 사용하지 않는다.

### SCENARIO-05 운영 제약 정책

- [x] `RateLimitPolicy`를 구현한다.
- [x] `TokenExpiryPolicy`를 구현한다.
- [x] 시스템 시각 대신 전달받은 가상 시각을 사용한다.
- [x] 정책 클래스는 HTTP 응답이나 LS JSON을 생성하지 않는다.

rate limit 구간은 슬라이딩 윈도가 아니라 epoch-second 고정 구간이다. 평가와 카운트 증가를
`ConcurrentHashMap.compute`로 원자적으로 처리해 동시 요청에서 한도를 넘기지 않는다.
토큰 만료 경계는 만료 쪽이다. 정확히 `issuedAt + ttl`이면 이미 EXPIRED다.

### SCENARIO-06 응답 지연 정책

- [x] `PLACE_ORDER`와 `CANCEL` operation을 구분한다.
- [x] `BEFORE_COMMIT`, `AFTER_COMMIT`을 구분한다.
- [x] 지연 시간을 반환하는 정책만 구현한다.
- [x] 정책 클래스에서 `Thread.sleep()`을 호출하지 않는다.

operation과 timing이 모두 일치할 때만 지연을 반환한다. 실제 지연 적용은 조립 계층의
책임이므로 정책은 즉시 반환한다.

스텁 javadoc은 "null 입력은 IAE"였으나 `configuredRule`이 null이면 `Optional.empty()`를
반환하도록 바꿨다. faults 미설정은 오류가 아니라 "규칙 없음"이기 때문이다. operation과
timing의 null은 그대로 거부한다.

### SCENARIO-07 카탈로그

- [x] `normal-fill.yml` 기대 결과를 문서화한다.
- [x] `partial-fill.yml` 기대 결과를 문서화한다.
- [x] `response-delay-after-commit.yml` 기대 결과를 문서화한다.
- [x] 잘못된 YAML 예시와 검증 테스트를 추가한다.

`scenarios/README.md`에 세 시나리오의 시점별 현금·잠긴 현금·보유 수량·주문 상태와
"봇이 검증할 것"을 적었다. 잘못된 예시는 실행용이 아니므로 카탈로그가 아니라
`scenario/src/test/resources/invalid/`에 두고 `InvalidScenarioCatalogTest`가 각 파일의
보고 필드를 고정한다. 카탈로그에 두면 `SCENARIO-01`의 `ScenarioCatalogTest`가 이들을
유효한 시나리오로 검사해 깨진다.

`constraints` 예제는 `bad-constraints.yml`에만 있다. 정상 동작하는 `rate_limit`·`token_ttl`
카탈로그 예제는 `APP-01`이 시나리오를 서버에 연결한 뒤 추가하는 것이 낫다.

팀장 확인 필요: AGENTS.md 6절은 MVP faults를 "주문 거부, `AFTER_COMMIT` 응답 지연" 2종으로
정의하는데 `ScenarioSpec`에는 응답 지연만 있다. 주문 거부를 범위에서 뺄지 필드를 추가할지
결정이 필요하다. `SCENARIO-01`의 "모든 필드가 필요한지 팀장에게 확인한다"와 함께 정리한다.

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
