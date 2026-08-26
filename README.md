# stock-mock-server

LS증권 OpenAPI의 HTTP/JSON 형태에 맞춘 상태 기반 로컬 Mock 거래소 서버입니다. 실제 계좌 없이
토큰 발급, 잔고 조회, 매수 주문, 부분체결 조회, 미체결 취소와 장애 대응을 반복 검증할 수 있습니다.

고정 JSON을 반환하는 스텁이 아니라 주문·계좌 상태를 유지하는 이산 사건 시뮬레이터이며,
사용자가 선택한 YAML에 따라 체결 방식, 토큰 TTL, 호출 제한과 응답 지연이 달라집니다.

## 1분 실행

Docker Desktop을 실행한 뒤 저장소 루트에서 다음 명령을 실행합니다.

```bash
docker compose up --build -d
```

브라우저에서 [http://localhost:8080](http://localhost:8080)을 열면 됩니다. 대시보드의 데모
버튼은 Mock 토큰을 자동 발급받아 실제 LS 호환 API를 호출합니다.

```bash
# 로그 확인
docker compose logs -f

# 종료 및 상태 초기화
docker compose down
```

최초 빌드는 Java·Gradle 이미지를 내려받기 때문에 시간이 걸리지만 이후에는 Docker 캐시를
사용합니다. 서버를 다시 시작하면 계좌, 주문과 이벤트 순번이 초기화됩니다.

## API 전체 흐름

### 1. 토큰 발급

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/oauth2/token \
  -d 'grant_type=client_credentials' \
  -d 'appkey=demo-app' \
  -d 'appsecretkey=demo-secret' \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

echo "$TOKEN"
```

이후 `/stock/**` 요청에는 발급된 Bearer 토큰이 필요합니다.

### 2. 초기 잔고 조회

```bash
curl -s http://localhost:8080/stock/accno \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"t0424InBlock":{"accno":"12345678901"}}'
```

### 3. 100주 매수

```bash
curl -s http://localhost:8080/stock/order \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"CSPAT00601InBlock1":{"AcntNo":"12345678901","IsuNo":"005930","OrdQty":100,"OrdPrc":70000,"BnsTpCode":"2","clientOrderId":"demo-1"}}'
```

응답의 `CSPAT00601OutBlock2.OrdNo`가 Mock LS 주문번호입니다. 기본 시나리오는 1초 뒤 30주를
부분체결합니다.

### 4. 주문 상태·미체결 조회

```bash
curl -s http://localhost:8080/stock/accno \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"t0425InBlock":{"expcode":"005930","chegb":"0","medosu":"0","sortgb":"2","cts_ordno":" "}}'
```

### 5. 미체결 70주 취소

아래 `OrgOrdNo`를 매수 응답의 주문번호로 바꿉니다.

```bash
curl -s http://localhost:8080/stock/order \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"CSPAT00801InBlock1":{"AcntNo":"12345678901","OrgOrdNo":1,"IsuNo":"005930","OrdQty":70}}'
```

최종 잔고는 현금 7,900,000원, 잠긴 현금 0원, 삼성전자 30주입니다.

## YAML 시나리오

내장 시나리오는 다음과 같습니다.

| 경로 | 동작 |
|---|---|
| `basic/normal-fill.yml` | 1초 뒤 100% 체결 |
| `basic/partial-fill.yml` | 1초 뒤 30% 부분체결 |
| `hazards/response-delay-after-commit.yml` | 주문 반영 후 HTTP 응답을 3초 지연 |
| `hazards/rate-limit.yml` | 같은 요청 종류를 초당 2회로 제한 |
| `hazards/token-expiry.yml` | 발급 2초 뒤 토큰 만료 |

Compose 실행 시 환경 변수로 내장 시나리오를 선택합니다.

```bash
MOCK_SCENARIO=classpath:scenarios/hazards/rate-limit.yml \
  docker compose up --build -d
```

사용자 YAML 파일을 실행할 수도 있습니다.

```bash
docker compose build
docker run --rm -p 8080:8080 \
  -v "$PWD/my-scenario.yml:/config/scenario.yml:ro" \
  -e MOCK_SCENARIO=/config/scenario.yml \
  stock-mock-server:local
```

서버는 시작할 때 YAML의 unknown 필드와 의미 오류를 검사합니다. 오류가 하나라도 있으면 어떤
필드를 고쳐야 하는지 모두 표시하고 시작을 중단합니다. 자세한 스키마와 예시는
[`scenarios/README.md`](scenarios/README.md)를 참고합니다.

## 공개 이미지

`main` 브랜치가 갱신되면 GitHub Actions가 다음 GHCR 이미지를 발행합니다.

```bash
docker run --rm -p 8080:8080 ghcr.io/0xdaehyun/stock-mock-server:latest
```

패키지가 비공개로 생성된 경우 저장소의 Packages 설정에서 Public으로 변경해야 익명 사용자가
로그인 없이 받을 수 있습니다.

## 로컬 개발

Java 21 이상에서 실행합니다.

```bash
./gradlew clean test
./gradlew :app:bootRun
```

다른 내장 시나리오를 선택하려면 다음과 같이 실행합니다.

```bash
./gradlew :app:bootRun \
  --args='--mock.scenario=classpath:scenarios/hazards/response-delay-after-commit.yml'
```

Docker와 `bootRun`은 모두 기본 포트 `8080`을 사용하므로 동시에 실행할 수 없습니다.

## 모듈

```text
core        주문·계좌·단일 스레드 DES 엔진
adapter-ls  LS InBlock/OutBlock 변환과 오류 봉투
scenario    YAML 로딩·검증, 체결·제약·지연 정책
app         Spring Boot 조립, 정책 적용과 정적 대시보드
```

의존 방향은 `app → adapter-ls → core ← scenario`이며 `core`는 Spring과 증권사 용어에
의존하지 않습니다.

팀 개발 방법은 [`docs/team-roles.md`](docs/team-roles.md), 작업 현황은 [`TODO.md`](TODO.md),
모듈 계약은 [`docs/contracts.md`](docs/contracts.md), 기여 방법은
[`CONTRIBUTING.md`](CONTRIBUTING.md)를 참고합니다.

## 현재 MVP 범위

- 지원: Mock 토큰, 현물 매수, 잔고, 주문 상태·미체결, 미체결 전량 취소
- 실행 정책: 전량체결·부분체결·분할체결
- 운영 제약: 토큰 만료, operation별 rate limit
- 장애: `BEFORE_COMMIT`·`AFTER_COMMIT` 응답 지연
- 보류: 매도·정정, WebSocket 체결통보, 통보 유실·중복, 시장 시간·시세 재생, 데이터 영속화

이 도구는 전략의 수익성을 평가하지 않습니다. 자동매매 프로그램이 부분체결, 취소, 만료,
호출 제한과 타임아웃을 올바르게 처리하는지 검증합니다.

## License

[Apache License 2.0](LICENSE)
