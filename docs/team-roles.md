# 3인 팀 역할 안내

## 우리가 함께 만드는 것

실제 계좌 없이 주식 프로그램의 주문 상태 처리와 장애 대응을 시험할 수 있는
**LS증권 호환 로컬 Mock 서버**를 만든다.

대회 데모는 다음 흐름을 완성하는 것이 목표다.

```text
토큰 발급 → 초기 잔고 조회 → 100주 매수 → 30주 부분체결 확인
→ 미체결 70주 취소 → 최종 잔고 확인
```

## 역할 한눈에 보기

| 담당 | 작업 모듈 | 한 문장 역할 |
|---|---|---|
| Core·통합 담당 | `core`, `app` | 주문·계좌 상태를 처리하고 모든 모듈을 최종 연결한다. |
| LS API 담당 | `adapter-ls` | LS 요청을 core 명령으로, core 결과를 LS 응답으로 변환한다. |
| Scenario 담당 | `scenario` | YAML로 체결 조건과 장애 조건을 설정할 수 있게 한다. |

의존 관계는 다음과 같다.

```text
app ──→ adapter-ls ──→ core
 └────→ scenario ─────→ core
```

각 담당자는 원칙적으로 자기 모듈만 수정한다. 다른 모듈의 공개 기능이 부족하면 직접
고치지 말고 Core·통합 담당에게 요청한다.

---

## 1. Core·통합 담당

### 작업 위치

- `core/src/main/java`
- `core/src/test/java`
- `app/src/main/java`
- `app/src/test/java`

### 역할

Mock 서버의 실제 거래 상태를 책임진다. API 모양이나 YAML 형식과 관계없이 주문,
체결, 취소, 잔고가 항상 올바르게 처리되도록 만든다. 마지막에는 다른 두 모듈을
`app`에서 조립한다.

### 알아야 할 것

- Java record, interface, enum, `CompletableFuture`
- 주문 상태: `ACCEPTED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`
- 매수 주문 시 현금 잠금, 체결 시 차감, 취소 시 미체결 금액 반환
- 모든 상태 변경과 조회가 단일 DES 이벤트 큐를 지나야 한다는 규칙
- `core`에는 Spring, YAML, LS의 TR·InBlock·OutBlock 용어를 넣지 않는다는 규칙

### 구현할 것

1. `OrderQuery`, `OrderView`, `EnginePort.query(OrderQuery)`를 구현한다.
2. `FillPlanProvider`를 만들고 엔진의 체결 비율·시간 하드코딩을 제거한다.
3. 접수, 부분체결, 전체체결, 취소 상태 전이를 검증한다.
4. 부분체결 후 미체결 수량만 취소되고 잠긴 현금만 반환되는지 검증한다.
5. YAML에서 만든 체결 계획을 `app`에서 엔진에 주입한다.
6. 토큰부터 최종 잔고까지 전체 데모 통합 테스트를 작성한다.

세부 작업 번호: `CORE-01`~`CORE-03`, `APP-01`~`APP-02`

### 구현하지 않을 것

- LS 요청·응답 JSON을 직접 만들지 않는다.
- Scenario 담당자의 YAML 파서와 정책을 대신 구현하지 않는다.
- 시스템 현재 시각이나 임의 체결 비율을 core에 하드코딩하지 않는다.

### 완료 기준

```bash
./gradlew :core:test :app:test
```

- 같은 입력과 seed에서 같은 주문 결과와 이벤트 순서가 나온다.
- 부분체결과 취소 후 잔고·보유수량·잠긴 현금이 모두 일치한다.
- 다른 담당자는 core 내부 객체가 아닌 `EnginePort`만 사용한다.

---

## 2. LS API 담당

### 작업 위치

- `adapter-ls/src/main/java`
- `adapter-ls/src/test/java`
- `adapter-ls/src/test/resources`의 LS 응답 fixture

### 역할

사용자의 주식 프로그램이 LS OpenAPI를 호출하는 것처럼 요청할 수 있게 한다.
LS 형식과 증권사 중립적인 core 사이의 번역기 역할이다.

### 알아야 할 것

- Spring Controller와 JSON 요청·응답의 기본 구조
- LS의 TR 코드, InBlock, OutBlock, 오류 봉투 개념
- `TrHandler`와 `LsTrDispatcher`의 역할
- Handler는 거래 상태를 직접 계산하지 않고 `EnginePort`를 호출한다는 규칙
- 실제 비밀키, 계좌번호, 토큰을 fixture나 Git에 올리면 안 된다는 규칙

### 구현할 것

1. Mock 토큰 발급 API와 TTL 응답을 구현한다.
2. 주문 상태·미체결 조회 `OrderStatusHandler`를 구현한다.
3. 미체결 전량 취소 `CancelOrderHandler`를 구현한다.
4. core 오류를 공통 LS 오류 응답으로 바꾸는 `LsErrorMapper`를 구현한다.
5. 기존 매수 주문과 잔고 조회를 포함한 다섯 기능의 요청·응답 테스트를 작성한다.
6. LS 공식 콘솔에서 확인한 구조를 민감 정보가 없는 fixture로 남긴다.

세부 작업 번호: `ADAPTER-01`~`ADAPTER-05`

### 구현하지 않을 것

- 주문 상태, 체결 수량, 잔고를 Handler에서 직접 계산하지 않는다.
- `Account`, `Order`, `SimulationEngine` 같은 core 내부 객체를 직접 사용하지 않는다.
- 확인하지 않은 LS 필드와 TR 코드를 추측해서 확정하지 않는다.
- core 또는 scenario 모듈을 직접 수정하지 않는다.

### 완료 기준

```bash
./gradlew :adapter-ls:test
```

- 토큰, 잔고, 매수, 주문 조회, 취소의 정상·오류 테스트가 통과한다.
- 각 Handler는 `EnginePort`만 통해 거래 기능을 호출한다.
- LS 요청·응답 필드가 fixture와 일치하고 민감 정보가 없다.

---

## 3. Scenario 담당

### 작업 위치

- `scenario/src/main/java`
- `scenario/src/test/java`
- `scenarios`의 예제 YAML

### 역할

사용자가 Java 코드를 수정하지 않고 YAML만 바꿔 정상 체결 방식, 토큰 만료,
호출 제한, 응답 지연을 선택할 수 있게 한다.

### 알아야 할 것

- YAML과 Java record의 필드 매핑
- `Duration`, `Instant`, enum의 기본 사용법
- 정상적인 체결 계획인 `execution`과 의도적인 장애인 `faults`의 차이
- 시스템 시각 대신 전달받은 가상 시각으로 판정해야 한다는 규칙
- 정책은 결과만 반환하며 `Thread.sleep()`이나 HTTP 응답 생성을 하지 않는다는 규칙

### 구현할 것

1. `ScenarioSpec`과 YAML 필드가 일치하는지 확인한다.
2. 비율, 수량, 지연, rate limit, token TTL의 잘못된 값을 검증한다.
3. `500ms`, `1s`, `10m`, `24h`를 처리하는 `DurationParser`를 구현한다.
4. YAML의 체결 설정을 `FillPlan`으로 바꾸는 `ScenarioFillPlanProvider`를 구현한다.
5. `TokenExpiryPolicy`와 `RateLimitPolicy`를 구현한다.
6. 요청 종류와 적용 시점에 맞는 지연을 반환하는 `ResponseDelayPolicy`를 구현한다.
7. 정상체결, 부분체결, 응답지연 예제 YAML과 기대 결과를 작성한다.

세부 작업 번호: `SCENARIO-01`~`SCENARIO-07`

### 구현하지 않을 것

- 주문이나 계좌 상태를 직접 변경하지 않는다.
- LS JSON 또는 HTTP 상태 코드를 만들지 않는다.
- 정책 안에서 `Thread.sleep()`을 호출하지 않는다.
- YAML에 셸 실행, 외부 URL 호출, Java 클래스 실행 기능을 넣지 않는다.
- core 또는 adapter-ls 모듈을 직접 수정하지 않는다.

### 완료 기준

```bash
./gradlew :scenario:test
```

- 잘못된 YAML은 어떤 필드가 잘못됐는지 명확한 오류를 반환한다.
- 100주와 `ratio: 0.3`을 입력하면 30주 체결 계획이 만들어진다.
- 같은 설정과 seed에서는 항상 같은 결과가 나온다.
- 정책 클래스가 Spring, HTTP, LS 형식에 의존하지 않는다.

---

## 공통 작업 방법

### 시작할 때

```bash
./gradlew clean test
git switch -c feat/<TODO번호>-<작업명>
```

1. [`../TODO.md`](../TODO.md)에서 본인 작업 번호와 선행 조건을 확인한다.
2. [`contracts.md`](contracts.md)에서 모듈 사이 입력·출력 의미를 확인한다.
3. 코드에서 본인 작업 번호를 검색한다. 예: `TODO(ADAPTER-01)`.
4. 테스트 한 개를 먼저 작성하고 구현을 시작한다.
5. 담당 모듈 테스트와 전체 테스트를 실행한 뒤 PR을 올린다.

### PR에 포함할 것

- 완료한 TODO 번호와 기능 설명
- 입력과 출력 예시
- 정상 상황 테스트와 실패 상황 테스트
- 실행한 Gradle 명령과 결과
- 아직 확인이 필요한 LS 필드 또는 설계 질문

하나의 PR에는 하나의 TODO만 넣는다.

### 막혔을 때

30분 이상 막히면 모듈 경계를 임의로 변경하지 말고 다음 내용을 공유한다.

```text
TODO 번호:
하려던 일:
입력값:
기대한 결과:
실제 결과:
시도한 방법:
관련 오류 또는 테스트:
```

## 권장 개발 순서

세 역할이 동시에 시작하되, 의존성이 있는 작업만 다음 순서를 따른다.

```text
Core:     CORE-01 ─────────────→ CORE-02 ─────────────→ APP-01, APP-02
API:      ADAPTER-03, 04 ──────→ ADAPTER-01, 02, 05
Scenario: SCENARIO-01, 02, 03 ─→ SCENARIO-05, 06 ─────→ SCENARIO-04, 07
```

- API 담당은 `CORE-01` 전에도 토큰과 오류 변환을 구현할 수 있다.
- Scenario 담당은 `CORE-02` 전에도 YAML 검증, 시간 파서, 제약 정책을 구현할 수 있다.
- Core 담당은 두 계약을 먼저 제공한 뒤 통합 작업을 진행한다.

## 대회 MVP에서 하지 않는 것

- WebSocket 체결 통보와 통보 중복·유실
- 매도, 정정 주문, 다른 증권사 adapter
- 실제 시장 데이터와 투자 전략 평가
- 데이터베이스 저장과 실행 중 시나리오 교체
- 복잡한 관리자 화면

새 기능이 필요해 보여도 먼저 다섯 API 데모를 완성한 뒤 논의한다.
