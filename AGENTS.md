# AGENTS.md — 주식 프로그램 개발용 Mock 서버

> 이 문서는 코딩 에이전트(Codex 등)가 이 저장소에서 작업하기 위한 단일 진입점이다.
> 프로젝트 맥락, 아키텍처 제약, 구현해야 할 작업, 코딩 규칙이 모두 여기에 있다.
> **작업 시작 전 3장(아키텍처 제약)과 8장(하지 말 것)을 반드시 읽을 것.**

---

## 1. 프로젝트 개요

### 1.1 무엇을 만드는가

증권사 OpenAPI(LS증권 우선)와 엔드포인트가 호환되는 **로컬 목(mock) 거래소 서버**다. 고정 응답 스텁이 아니라 주문 생명주기(접수·부분체결·체결·취소)와 계좌 정합성이 실제로 동작하는 **에뮬레이터형 목**으로, 자동매매 프로그램은 접속 주소(base URL) 한 줄만 바꾸면 코드 수정 없이 그대로 연결된다.

핵심은 **YAML 시나리오 기반 장애 주입**이다. 부분체결·주문 거부·응답 지연·통보 유실 같은 실패 상황을 재현 가능하게(seed 고정) 일으켜 봇의 견고성을 검증한다. Docker 실행과 CI 연동을 지원한다.

### 1.2 왜 만드는가

자동매매 프로그램은 금전이 걸린 코드임에도 장 운영 시간 제약과 실패 상황 재현 불가로 사실상 테스트가 불가능하다. 증권사 API와 호환되는 상태 기반 목 거래소 서버와 장애 주입 기능을 오픈소스로 제공해, 계좌 없이도 자동매매 코드의 실패 대응 능력을 배포 전에 검증할 수 있게 한다.

### 1.3 기대 효과 (설계 판단의 기준)

기능 우선순위가 애매할 때 아래 네 가지에 기여하는지로 판단한다.

1. **실계좌 손실 없는 반복 검증** — 부분체결·통보 유실 같은 실패 상황을 손실 없이 반복 재현. 실전에서 수업료를 치르며 버그를 발견하던 관행을 배포 전 자동 테스트로 대체
2. **CI 회귀 테스트 문화 이식** — 장 운영 시간과 모의투자 계좌 제약에 묶여 있던 증권 연동 코드에, 커밋마다 장애 시나리오가 자동 실행되는 회귀 테스트를 도입
3. **진입장벽 해소** — 계좌 개설이나 API 신청 없이 `docker run` 한 줄로 주문 API 체험 가능
4. **커뮤니티 사고 사례집** — 실전 장애 사례를 YAML 시나리오로 축적하는 카탈로그 구조. 코드를 모르는 사용자도 기여 가능

장기적으로는 결제·본인인증 등 테스트가 어려운 다른 국내 대외기관 연동 도메인으로 확장 가능한 테스트 인프라의 기반이 된다.

### 1.4 검증 대상의 정확한 정의

이 도구가 검증하는 것은 **수익률이나 손실이 아니라 봇의 상태 처리·판단 로직**이다.

| 검증한다 | 검증하지 않는다 |
|---|---|
| 부분체결(30/100주)을 전량 체결로 오인하는가 | 전략이 수익을 내는가 |
| 통보 유실 시 내부 장부가 어긋난 채 굴러가는가 | 시장 예측이 맞는가 |
| 타임아웃 시 중복 주문을 내는가 | 백테스트 성능 |
| rate limit 초과 시 백오프하는가 | |

### 1.5 프로젝트 현황

| 항목 | 값 |
|---|---|
| 저장소명 | `stock-mock-server` |
| 대회 | 2026 오픈소스 개발자대회, 일반 부문 / 자유과제 |
| 제출 마감 | **2026-08-27** |
| 팀 | 3명 (백엔드 1, 프론트 1, 문서·기획 1) |
| 언어/빌드 | Java 21, Gradle (Kotlin DSL) |
| 라이선스 | Apache-2.0 |
| 현재 단계 | M1 스파이크 완료, 3인 병렬 개발 골격 준비 단계 |

---

## 2. 시스템의 정체: 이산 사건 시뮬레이터(DES)

겉은 Spring Boot 웹 서버지만, 심장은 **DES(Discrete-Event Simulation) 엔진**이다. 시스템에서 일어나는 모든 일이 "시각이 찍힌 사건"으로 통일된다.

- 봇의 주문 요청 → 사건 (현재 시각)
- "5초 뒤 30% 부분체결" → 사건 (미래 시각에 예약)
- 시세 틱 → 예약된 사건들
- 통보 발송/유실 → 사건

```java
// 엔진의 핵심 — 시각순 우선순위 큐를 도는 단일 루프
PriorityQueue<SimEvent> queue;   // (virtualTime, seq) 순 정렬

void loop() {
    while (running) {
        SimEvent e = queue.poll();      // 가장 이른 사건
        clock.advanceTo(e.time());      // 가상시계를 그 시각으로
        e.apply(state);                 // 상태 변경 (이 스레드에서만)
        eventLog.append(e.toRecord());  // 기록
    }
}
```

이 구조 하나가 가상시계·단일 작성자·이벤트 로그를 통합한다. 결정론, 가속 재생, 정합성 증명이 모두 여기서 파생된다.

---

## 3. 아키텍처 제약 (위반 금지)

### ADR — 확정된 설계 결정

| # | 결정 | 기각된 대안과 이유 |
|---|---|---|
| 1 | **단일 스레드 DES 루프** — 모든 상태 변경은 엔진 스레드 하나에서 순차 처리 | 락 기반 멀티스레드: 정합성 증명 비용. 목 서버가 자체 레이스를 겪으면 제품 신뢰 붕괴 |
| 2 | **FillPlan은 주문 접수 시점에 확정** — 체결 계획(언제 몇 주)을 접수 순간 통째로 결정하고 큐에 예약 | 체결 시점 난수: RNG 소비 순서가 사건 도착 타이밍에 흔들려 결정론 붕괴 |
| 3 | **조회도 명령 큐 경유** — `query()`도 사건으로 직렬화 | 직접 상태 읽기: 락 필요. 큐 경유 시 락 제로 + 항상 정합한 스냅샷 |
| 4 | **core는 증권사 중립** — LS 지식은 `adapter-ls`에만 | core에 `LS`, `TR`, `InBlock`, `rsp_cd` 단어가 등장하면 설계 위반. 스펙 드리프트를 어댑터 한 층에 격리 |
| 5 | **constraints(운영 규칙)와 faults(장애) 분리** | 하나로 합치기: 개념도 사용 패턴도 다름. 제약은 프리셋으로 상시 적용, 장애는 시나리오마다 교체 |
| 6 | **시나리오 DSL은 순수 데이터** — 셸 실행·네트워크 호출·스크립트 훅 없음 | 실행 훅: LLM 생성 YAML의 신뢰 문제 + 폭발 반경 확대 |
| 7 | **core는 Spring 의존 금지** — 순수 Java + JUnit만 | build.gradle.kts로 강제. 급할 때 무너지지 않도록 |

### 모듈 구조와 의존 방향

```
stock-mock-server/
├── core/         # 순수 Java, 의존성 없음(JUnit 제외)
│   ├── account/  #   계좌: 예수금·보유·미체결·정합성
│   ├── order/    #   주문 상태 머신
│   ├── fill/     #   체결 엔진, FillPlan
│   ├── market/   #   시세 재생
│   ├── event/    #   이벤트 로그 (단일 진실 원천)
│   └── clock/    #   가상 시계
├── scenario/     # YAML 파싱, 장애 스케줄링, validate. 의존: core
├── adapter-ls/   # TR 디스패처, InBlock/OutBlock 변환. 의존: core, spring-web
├── notify/       # 체결통보 발송. 의존: core
├── app/          # Spring Boot 조립 + 대시보드 서빙. 의존: 전부
├── dashboard/    # React + Vite + ECharts (별도 빌드 → app 리소스로 내장)
├── scenarios/    # YAML 카탈로그 (basic/, hazards/)
└── docs/         # adr/, diagrams/, glossary.md
```

의존 방향: `app → adapter-ls → core ← scenario`. **core가 아무것도 의존하지 않는 것**이 이 구조의 핵심이다.

### 요청 파이프라인 (계층 순서 = 실서버의 물리적 순서)

```
봇 요청
 → [adapter] TR 디스패치, InBlock 파싱
 → [C1 인증]       토큰 유효?          (constraints.token_ttl)    → 401 봉투
 → [C2 rate limit] 호출 제한 초과?     (constraints.rate_limit)   → 거부 봉투
 → [F1 장애-요청층] "n번째부터 거부", 응답 지연                    → 거부/지연
 → [C3 시장 규칙]  장 운영 시간?       (constraints.market_hours) → 거부 봉투
 → [엔진 큐] 증거금 검사 → 상태 전이 → FillPlan 예약 → 이벤트 발행
 → [F2 장애-체결층] fill_ratio/fill_delay (FillPlan에 이미 반영)
 → 응답 봉투 반환
 ... 가상시계 경과 후 체결 사건 발생 ...
 → [notify] 통보 생성 → [F3 장애-전달층] drop/duplicate/reorder → 발송
```

- C1·C2는 **엔진 앞**에 위치 (rate limit 초과 요청은 엔진에 도달하지 않음 — 실서버와 동일)
- F3는 발송기 데코레이터로 구현 → `DropDecorator(DuplicateDecorator(realSender))` 식 조합이 공짜

### 시간 모델 (중요 함정)

가속 재생과 실제 봇은 모순한다(봇은 벽시계로 산다). **모드 분리**로 해결:

| 모드 | 시계 | 용도 |
|---|---|---|
| `attached` (봇 연결) | 배속 1.0 고정 | 킬러 데모, 수동 테스트. 가상시계 역할은 가속이 아니라 결정론적 스케줄링 |
| `headless` (봇 없이) | 임의 배속/즉시 점프 | 시나리오 validate, `expect_events` 검증, CI |

`attached` 모드에서는 봇 측 타이밍이 통제 밖이므로 완전 재현이 불가능하다. README에 정직하게 명시할 것.

### 결정론 규율

1. 난수는 **접수 시점에 소진** (FillPlan 확정)
2. 동시각 사건의 tie-break는 `seq`
3. `attached` 모드의 한계는 문서화

---

## 4. 도메인 명세

### 4.1 주문 상태 전이표 (= core의 명세 = 단위 테스트 목록)

| 현재 상태 \ 사건 | fill(q) | cancel | modify(정정) | reject |
|---|---|---|---|---|
| `ACCEPTED` | q<qty → `PARTIALLY_FILLED` / q=qty → `FILLED` | `CANCELLED` (잠금 전액 해제) | 잔량 재계산, `ACCEPTED` 유지 | `REJECTED` |
| `PARTIALLY_FILLED` | 누적<qty → `PARTIALLY_FILLED` / 누적=qty → `FILLED` | `CANCELLED` (**미체결분만** 해제) | **미체결분만** 정정 | ✗ 불가 |
| `FILLED` | ✗ 예외 | ✗ 예외 | ✗ 예외 | ✗ |
| `CANCELLED` / `REJECTED` | ✗ 예외 | ✗ 예외 | ✗ 예외 | ✗ |

- ✗는 core에서 **불법 전이로 차단**(예외 발생), adapter가 LS 규약의 거부 응답으로 번역
- `PARTIALLY_FILLED` 행이 이 프로젝트의 핵심 — 팀장의 자동취소 프로그램이 검증하려던 지점

### 4.2 계좌 불변식

```
I1. cash >= 0, lockedCash >= 0 항상
I2. 매수 접수 시 lockedCash += 주문금액
    체결 시 lockedCash → 차감 반영
    취소 시 lockedCash → cash 복원
I3. 초기현금 = cash + lockedCash + Σ(매수체결금액) - Σ(매도대금)
I4. position.qty = Σ(매수체결) - Σ(매도체결), 음수 금지 (MVP는 공매도 없음)
```

**엔진은 매 사건 처리 후 `assertConsistent()`로 I3을 자체 검사하고, 깨지면 즉시 크래시한다.** 목 자체가 틀리면 사용자가 자기 봇을 의심하게 되므로, "엔진의 장부는 절대 틀리지 않는다"가 제품 신뢰의 근간이다.

### 4.3 모듈 간 계약

```java
// core의 유일한 공개 인터페이스 — adapter는 이것만 안다
public interface EnginePort {
    CompletableFuture<OrderResult> submit(PlaceOrder cmd);
    CompletableFuture<OrderResult> cancel(CancelOrder cmd);
    CompletableFuture<AccountView> query(AccountQuery q);
}

// 명령 — 증권사 중립 언어 (LS 용어 등장 시 설계 위반)
public record PlaceOrder(String clientOrderId, Symbol symbol,
                         Side side, long qty, long price) {}
public record CancelOrder(String clientOrderId, String targetOrderId, long qty) {}

// 이벤트 — 대시보드·리포트·검증이 모두 이것에서 파생
public record EventRecord(long seq, Instant virtualTime, String type,
                          String orderId, Map<String, Object> payload) {}
```

이벤트 타입(최소): `ORDER_ACCEPTED`, `ORDER_REJECTED`, `PARTIAL_FILL`, `FILL`, `ORDER_CANCELLED`, `NOTIFY_SENT`, `NOTIFY_DROPPED`, `RATE_LIMITED`, `TOKEN_EXPIRED`, `MARKET_TICK`

---

## 5. 시나리오 스키마 (v1)

```yaml
scenario: partial_fill_delay

account:
  cash: 10000000

constraints:              # 운영 제약 — 평상시 적용되는 규칙
  rate_limit: { per_sec: 2 }
  token_ttl: 10s

execution:                # 정상 체결 결과 — 부분체결 자체는 장애가 아님
  fills:
    - { after: 1s, ratio: 0.3 }

faults:                   # 의도적으로 발생시키는 시스템 장애
  response:
    on: PLACE_ORDER
    timing: AFTER_COMMIT
    delay: 3s

seed: 42
```

### 대회 MVP 범위 (확장 금지)

- **API 5기능**: 토큰, 잔고, 현물 매수, 주문 상태·미체결 조회, 미체결 전량 취소
- **constraints 2종**: `rate_limit`, `token_ttl`
- **execution**: 전량체결, 부분체결, 분할체결을 접수 시점 FillPlan으로 확정
- **faults**: 주문 거부, `AFTER_COMMIT` 응답 지연
- **상태 초기화**: 서버 시작 시 초기화, 실행 중 시나리오 교체와 DB 영속화 없음
- **통보**: 대회 MVP는 폴링 방식. WebSocket·통보 유실·중복은 제출 이후 로드맵

### 시나리오 검증 파이프라인 (LLM 생성·커뮤니티 PR 대응)

원칙: **LLM을 신뢰하지 말고, 신뢰가 필요 없는 구조를 만든다.**

1. **스키마 검증** — 타입·enum 확인, unknown 필드 거부(strict). `validate` 서브커맨드
2. **의미 린트** — `fill_ratio: 1.3` 같은 도메인 모순, 발생 불가능한 `after:` 조건 탐지
3. **드라이런** — `headless` 모드로 봇 없이 실행해 `expect_events`가 실제 발생하는지 확인
4. **결정론** — 3단 통과 시나리오는 seed 고정으로 영구히 동일 동작

이 파이프라인은 커뮤니티 PR 검증 CI와 동일물이다.

---

## 6. LS증권 API 실측 사실 (adapter-ls 구현 근거)

개발자 콘솔(`openapi.ls-sec.co.kr`)에서 확인한 사실:

- **TR 코드 디스패치**: 여러 TR이 **같은 URL을 공유**하고 요청 바디의 키(`t3102InBlock`, `t3202InBlock`)로 동작이 갈린다
  → adapter는 "컨트롤러 N개"가 아니라 **URL 소수 + TR 디스패처** 구조여야 한다
- **응답 봉투**: `{ "rsp_cd": "00000", "rsp_msg": "조회완료", "tXXXXOutBlock": [...] }`
- **에러 코드**: 400, 401, 404, 405, 500, 503
- **레거시 전문 흔적**: OutBlock이 고정 길이 청크 배열로 오고 경계에서 멀티바이트 문자가 깨짐 → 전문 시스템 위에 REST를 씌운 구조
- **공식 테스트베드는 단건 호출 콘솔**(Swagger UI 유사)일 뿐 — 봇 연결·상태 연결·장애 주입 없음

```java
// adapter-ls 디스패처 구조 (예시)
@PostMapping("/stock/order")
public LsEnvelope handle(@RequestBody JsonNode body) {
    String trCode = extractTrCode(body);       // "CSPAT00601InBlock1" → CSPAT00601
    TrHandler handler = registry.get(trCode);  // TR별 핸들러 등록제
    return handler.handle(body);               // InBlock 파싱 → core 명령 → OutBlock 조립
}
```

### 클론 범위

| 요소 | 클론 여부 | 비고 |
|---|---|---|
| HTTP + JSON, URL 경로 | ✅ 정확히 | 봇이 직접 만짐 |
| TR 봉투 (InBlock/OutBlock, rsp_cd) | ✅ 정확히 | 봇의 파서가 뜯음 |
| 인증 플로우 (토큰 발급 → Bearer) | ✅ 형태만 | 아무 토큰이나 발급, 만료 시나리오만 재현. 실제 보안 구현 불필요 |
| 통보 채널 | ⚠️ 봇이 쓰는 부분집합만 | **방식 미확정 — 9장 참조** |
| TLS·IP·방화벽 | ❌ | localhost라 무의미 |
| 실제 지연 특성 | ❌ | 재현이 아니라 **조작**이 제품 |
| 증권사 내부(원장·KRX·매칭엔진) | ❌ | 봇이 관측 불가 |

**호환성 판정 기준**: "실제 봇이 눈치 못 채면 충분하다." 감이 아니라 **계약 테스트**로 만든다 — 실서버/콘솔에서 받은 실제 응답 JSON을 fixture로 저장하고 목의 응답과 필드 구조를 대조.

---

## 7. 구현 작업 목록

### M1 — 스파이크 (1주차, 최우선)

목표: **여정 하나를 끝까지 관통하는 세로 절단면**. 기능을 넓히지 말 것.

- [ ] Gradle 멀티모듈 골격 (`core`, `app`만 우선 생성)
- [ ] `VirtualClock` — 가상시계 (모든 시간 접근은 이것 경유, `System.currentTimeMillis()` 직접 호출 금지)
- [ ] `SimEvent` + 우선순위 큐 + 단일 스레드 DES 루프
- [ ] `Order` 엔티티 + 상태 전이 메서드 (`fill`, `cancel`) — setter 금지, 전이 규칙을 메서드로 강제
- [ ] `Account` — cash / lockedCash / positions, `assertConsistent()`
- [ ] `FillPlan` — 접수 시점 체결 계획 확정 (MVP는 `fill_ratio` 하드코딩)
- [ ] LS TR 2개: 현물 매수 주문, 잔고 조회 (`adapter-ls` 최소 구현)
- [ ] **팀장의 실제 자동취소 프로그램을 base URL만 바꿔 연결 → 잔고 조회 성공** ← M1 완료 판정 기준

### M2 — 설계 확정 (2주차)

- [ ] LS 개발자 콘솔에서 TR 5종(주문·정정취소·잔고·현재가·토큰) InBlock/OutBlock JSON 추출 → 스펙 표 문서화
- [ ] `EventRecord` JSON 스키마 확정 → 대시보드 담당이 목업으로 병렬 착수 가능
- [ ] 시나리오 YAML 스키마 v1 확정 + `ScenarioSpec` record 트리
- [ ] ADR 문서 7건, Mermaid 다이어그램 3종(컨테이너/상태전이/시퀀스)
- [ ] 계약 테스트 골격 (실응답 fixture 대조)

### M3 — 병렬 구현 (3~4주차)

**core (백엔드/팀장)**
- [ ] 상태 전이표 전 항목 구현 + 단위 테스트 (전이표의 모든 칸이 테스트 1개)
- [ ] 계좌 불변식 I1~I4 + 매 사건 자체 검증
- [ ] 체결 엔진: 확률적 체결 모델, 부분체결 분할
- [ ] 시세 재생기 (가격 배열 + tick_interval)

**adapter-ls (팀원 A)**
- [ ] 토큰·잔고·매수·주문 상태·취소의 5개 기능
- [ ] TR 디스패처와 Handler 등록 구조
- [ ] LS 응답 봉투 조립기와 오류 코드 매핑
- [ ] 공식 응답 fixture 기반 계약 테스트

**scenario (팀원 B)**
- [ ] strict YAML 파서와 의미 검증기
- [ ] `ScenarioFillPlanProvider`
- [ ] constraints: rate limit, token TTL
- [ ] faults: 주문 거부, AFTER_COMMIT 응답 지연 정책
- [ ] basic/hazards 시나리오 카탈로그

세부 작업 번호와 선행 조건은 저장소 루트의 `TODO.md`를 따른다.

### M4 — 통합·정비 (5~7주차)

- [ ] **킬러 데모 시나리오 확보**: 100주 주문 → 30주 부분체결 → 미체결 70주 취소 → 잔고 정합성 확인
- [ ] AFTER_COMMIT 응답 지연으로 요청 결과 불확실성 재현
- [ ] 시나리오 카탈로그 5종 이상 (`basic/`, `hazards/`)
- [ ] Docker 이미지 + `docker run` 한 줄 실행
- [ ] Testcontainers 모듈 (`@Container BrokerMockContainer`)
- [ ] GitHub Actions: 빌드·테스트 + **셀프 데모**(목 위에서 예제 봇을 돌려 시나리오 통과 확인)
- [ ] README (5분 Quickstart: docker run → curl 주문 → 잔고 변화 확인 → 봇 연결 → 시나리오 적용)
- [ ] CONTRIBUTING.md — **시나리오 기여 트랙을 1순위로**
- [ ] 도메인 용어집(부분체결·미체결·정정·증거금), 결과보고서, 3분 시연 영상

---

## 8. 하지 말 것 (범위 밖)

- 실시장 데이터 리플레이 (시나리오 고정 가격 배열만)
- 수익률·전략 성능 평가 (백테스팅 영역 — 명확히 우리 범위 아님)
- 증권사 내부 시스템 클론 (원장, KRX 연동, 호가 매칭 엔진)
- 실제 보안 구현 (인증은 형태만)
- **API 커버리지 확장** — TR 5종 고정. 깊이(상태 정합·장애 주입)에 전부 투자
- 공매도, 파생, 해외주식
- 호가 단위·가격 제한폭 (로드맵)
- **core에 Spring 도입**, adapter 지식(LS/TR/InBlock)의 core 유출
- 대시보드가 사용자 환경에 Node 요구 (정적 번들로 jar 내장할 것 — 기능테스트 감점 방지)
- 시나리오 DSL에 실행 능력(셸·네트워크·스크립트 훅) 부여
- 조기 최적화 (정합성 검증은 풀 검사로 시작, 프로파일 후 증분 검증 고려)

---

## 9. 미해결 항목

1. LS 개발자 콘솔에서 MVP 5기능의 실제 요청·응답 fixture 확보
2. LS OpenAPI 이용약관 확인 (공개 문서 기반 인터페이스 호환 구현의 적법성)
3. 경쟁물 재검색: GitHub "증권 mock", "KIS mock", "broker simulator korea"
4. WebSocket 체결통보는 대회 이후 지원 범위와 프로토콜을 재검토

---

## 10. 코딩 규칙

### 일반

- Java 21, Gradle Kotlin DSL, 멀티모듈
- 커밋 메시지: 한국어 + Conventional Commits (`feat: 부분체결 시 미체결분만 취소되도록 구현`)
- 브랜치: `feat/`, `fix/`, `docs/` 접두사. PR 필수, 리뷰 1인
- **PR은 모듈 경계를 넘지 않는다** (core PR과 adapter PR 분리) — 3인 병렬 작업의 전제

### 코드

- **기존 구조·변수명·파일명을 유지**한다. 임의로 타입·파일을 추가하지 않는다
- 수정 시 전체 실행 가능한 코드를 제시하고, 변경 부분을 명시한다
- 도메인 객체는 setter 금지 — 상태 전이는 반드시 의미 있는 메서드로 (`order.fill(30)`, `order.cancel()`)
- `System.currentTimeMillis()` / `Instant.now()` 직접 호출 금지 → `VirtualClock` 주입
- core 클래스에 Spring 어노테이션 금지
- 테스트: JUnit 5 + AssertJ. **전이표의 모든 칸이 테스트 1개**, 불변식 I1~I4 각각 테스트

### 테스트 우선순위

1. 상태 전이표 (불법 전이가 예외를 던지는지 포함)
2. 계좌 불변식 (특히 부분체결 후 취소 시 lockedCash 복원)
3. 결정론 (같은 seed → 같은 이벤트 시퀀스)
4. 계약 테스트 (LS 실응답 fixture와 필드 구조 대조)

---

## 11. 참고: 방어 논리 (설계 판단이 흔들릴 때)

| 흔한 의문 | 답 |
|---|---|
| "증권사 모의투자로 되지 않나?" | 모의투자는 **성공을 연습하는 곳**(정상 체결만). 봇을 죽이는 건 실패 케이스인데 그걸 일으켜주는 곳은 없다. 계좌 신청 필요, 호출 제한 낮음, 장 시간 종속, CI 불가 |
| "WireMock에 JSON 물리면?" | 스텁은 **상태가 없다**. "주문→잔고 차감→3초 뒤 부분체결→미체결분만 취소" 시퀀스는 상태 머신 없이 표현 불가. 우리가 만드는 건 **주문 생명주기 엔진 + 장애 주입 DSL** |
| "실제 API와 다르면?" | 완벽 재현이 목표가 아니라 **봇의 로직 테스트**가 목표. LocalStack도 AWS 완벽 복제가 아니다. 호환성은 계약 테스트로 증명 |
| "백테스팅과 뭐가 다른가?" | 백테스팅은 수익성, 우리는 **견고성**. 백테스팅은 봇을 재작성해야 하지만 우리는 실제 봇을 그대로 붙인다 |
| "수익률도 검증되나?" | 아니다. **범위를 스스로 긋는 것이 신뢰의 근거** |

---

## 12. 이 문서의 사용법

- 새 기능을 제안하기 전에 **8장(하지 말 것)** 을 확인한다
- 설계 결정을 바꾸려면 **3장 ADR**의 기각 사유를 먼저 읽는다
- 작업 착수는 **7장 구현 작업 목록**의 현재 마일스톤부터
- 응답은 한국어로, 결론 → 이유 → 예시 → 바로 쓸 수 있는 코드 → 주의사항 순으로
- 무조건 긍정하지 말 것. 단점·리스크·기존 도구와 겹치는 부분은 직설적으로 지적할 것
