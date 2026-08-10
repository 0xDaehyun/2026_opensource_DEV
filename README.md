# stock-mock-server

LS증권 OpenAPI의 HTTP/JSON 형태에 맞춘 상태 기반 로컬 거래소 목 서버의 M1 스파이크입니다.
현재 범위는 현물 매수 주문(`CSPAT00601`)과 잔고 조회(`t0424`), 30% 부분체결입니다.

## 팀원이라면 먼저 읽기

1. [`docs/team-roles.md`](docs/team-roles.md)에서 자기 역할과 담당 모듈을 확인합니다.
2. [`TODO.md`](TODO.md)에서 담당 작업과 선행 조건을 확인합니다.
3. [`docs/contracts.md`](docs/contracts.md)에서 모듈 간 데이터 의미를 확인합니다.
4. [`CONTRIBUTING.md`](CONTRIBUTING.md)의 브랜치·PR 규칙을 따릅니다.
5. API 담당은 [`adapter-ls/README.md`](adapter-ls/README.md), 시나리오 담당은
   [`scenario/README.md`](scenario/README.md)에서 시작합니다.

```bash
./gradlew clean test
```

위 명령이 성공하기 전에는 기능 개발을 시작하지 않습니다.

## 모듈

```text
core        주문·계좌·DES 엔진. 팀장 담당
adapter-ls  LS 요청·응답 변환. API 담당
scenario    YAML 로딩·검증·실행 정책. 시나리오 담당
app         Spring Boot 조립과 통합 테스트. 팀장 담당
```

의존 방향은 `app → adapter-ls → core ← scenario`입니다. `scenario` 골격은 현재 독립적으로
빌드되며, `FillPlanProvider` 계약이 준비된 뒤 `app`에서 core에 연결합니다.

## 실행

Java 21 이상에서 다음 명령을 실행합니다.

```bash
./gradlew :app:bootRun
```

주문:

```bash
curl -s http://localhost:8080/stock/order \
  -H 'Content-Type: application/json' \
  -d '{"CSPAT00601InBlock1":{"AcntNo":"12345678901","IsuNo":"005930","OrdQty":100,"OrdPrc":70000,"BnsTpCode":"2","clientOrderId":"demo-1"}}'
```

5초 후 잔고 조회:

```bash
curl -s http://localhost:8080/stock/accno \
  -H 'Content-Type: application/json' \
  -d '{"t0424InBlock":{"accno":"12345678901"}}'
```

초기 현금은 1천만 원입니다. 7만 원에 100주를 주문하면 700만 원이 잠기고, 5초 뒤 30주가
부분체결되어 `janqty=30`, `mamt=4900000`이 됩니다.

## 검증

```bash
./gradlew test
```

`core`는 Spring에 의존하지 않습니다. 주문·조회·예약 체결은 모두 단일 DES 스레드의
우선순위 큐를 지나며, 동일 시각 사건은 생성 순번으로 정렬됩니다. `attached` 모드는 실제 봇을
위해 1배속으로 실행하고, 테스트의 `headless` 모드는 예약 시각으로 즉시 이동합니다.

## M1 제한

- 정정/취소 HTTP TR, 토큰, rate limit, 시장 시간, YAML 시나리오와 통보 채널은 후속 마일스톤 범위입니다.
- LS 응답 필드는 공개 콘솔 fixture를 확보한 뒤 계약 테스트로 정확히 고정해야 합니다. 현재 응답은
  M1 연결에 필요한 최소 필드입니다.
- 현재 로컬 자동매매 프로젝트에는 실제 API 호출 코드가 없어 base URL 교체 연결 시험은 아직 불가능합니다.
- `scenario` 모듈은 팀 개발을 위한 loader·record·validator 시작 골격이며 아직 실행 엔진에 연결되지 않았습니다.
