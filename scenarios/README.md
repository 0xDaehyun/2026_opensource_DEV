# 시나리오 카탈로그

목 서버의 동작을 YAML로 고르는 곳이다. Java 코드를 고치지 않고 체결 방식과 장애 조건을
바꿀 수 있다.

```bash
./gradlew :app:bootRun \
  --args='--mock.scenario=classpath:scenarios/basic/partial-fill.yml'
```

서버는 시작 시 파일을 strict 모드로 읽고 의미 검증을 수행합니다. 오류가 있으면 모든 필드
경로를 출력하고 시작을 중단합니다.

## 설정 분류

```text
constraints   평상시 운영 규칙 (rate limit, token TTL)
execution     정상적인 체결 결과 (전량체결, 부분체결, 분할체결)
faults        의도적으로 주입하는 장애 (응답 지연)
```

부분체결은 장애가 아니다. 사용자가 고를 수 있는 정상 체결 시나리오다.

## 시간 표기

`500ms`, `1s`, `10m`, `24h` 형태만 쓴다. 단위 없는 값(`3`), 소수(`1.5s`), 0과 음수는
검증에서 거부된다.

`execution.fills[].after`는 모두 **주문 접수 시각 기준**이다. 누적이 아니다.

```yaml
fills:
  - after: 1s     # 접수 1초 뒤
  - after: 5s     # 접수 5초 뒤 (1+5=6초가 아니다)
```

## 분할체결과 주문 수량

`ratio`는 내림하되 최소 1주를 보장한다. 그래서 **분할체결 시나리오는 step 수 이상의 주문
수량이 필요하다.** step이 2개인 시나리오에 1주를 주문하면 계획을 세울 수 없어 주문이
거부된다. 검증기는 주문 수량을 모르므로 이 조건은 실행 시점에만 드러난다.

```text
fills 2개 시나리오
  1주 주문   → 거부 ("step 2개, 주문 1주")
  2주 주문   → 1주 + 1주
  100주 주문 → 30주 + 70주
```

---

## basic/normal-fill.yml — 전량체결

```yaml
execution:
  fills:
    - after: 1s
      ratio: 1.0
```

70,000원에 100주를 매수하면:

| 시점 | 현금 | 잠긴 현금 | 보유 수량 | 주문 상태 |
|---|---|---|---|---|
| 접수 직후 | 3,000,000 | 7,000,000 | 0 | `ACCEPTED` |
| 1초 뒤 | 3,000,000 | 0 | 100 | `FILLED` |

봇이 검증할 것: 전량체결 통보를 받고 내부 장부를 100주로 맞추는가.

## basic/partial-fill.yml — 부분체결

```yaml
execution:
  fills:
    - after: 1s
      ratio: 0.3
```

같은 주문에서:

| 시점 | 현금 | 잠긴 현금 | 보유 수량 | 주문 상태 |
|---|---|---|---|---|
| 접수 직후 | 3,000,000 | 7,000,000 | 0 | `ACCEPTED` |
| 1초 뒤 | 3,000,000 | 4,900,000 | 30 | `PARTIALLY_FILLED` |

30주가 체결되어 2,100,000원이 빠지고 미체결 70주의 4,900,000원이 잠긴 채 남는다.

봇이 검증할 것: **30주 부분체결을 전량체결로 오인하지 않는가.** 이 프로젝트가 잡으려는
대표적인 버그다. 미체결 70주를 취소하면 잠긴 현금이 전액 돌아와야 한다.

## hazards/response-delay-after-commit.yml — 응답 지연

```yaml
faults:
  response:
    on: PLACE_ORDER
    timing: AFTER_COMMIT
    delay: 3s
```

`AFTER_COMMIT`은 다음을 뜻한다.

```text
core 주문 접수 완료
→ 주문번호 생성과 현금 잠금 완료
→ HTTP 응답만 3초 지연
```

**주문은 이미 성립했는데 봇은 3초 동안 결과를 모른다.** 체결은 `partial-fill`과 같이
1초 뒤 30주다. 즉 봇이 응답을 받는 시점에는 이미 부분체결이 끝나 있다.

봇이 검증할 것: 타임아웃으로 판단해 **같은 주문을 다시 내지 않는가.** 재요청하면
`중복 clientOrderId` 거부를 받거나, clientOrderId를 바꿨다면 의도치 않은 이중 주문이 된다.

## hazards/rate-limit.yml — 호출 제한

같은 토큰과 operation 조합에서 1초 동안 두 요청까지 허용하고 세 번째부터 HTTP 429와
`rsp_cd=42900`을 반환합니다. 제한된 요청은 엔진에 도달하지 않습니다.

봇이 검증할 것: 호출 제한 응답을 받았을 때 즉시 반복 호출하지 않고 다음 구간까지
백오프하는가.

## hazards/token-expiry.yml — 토큰 만료

발급한 Mock 토큰이 2초 뒤 만료됩니다. 만료되거나 발급 기록이 없는 토큰으로 `/stock/**`을
호출하면 HTTP 401과 `rsp_cd=40100`을 반환합니다.

봇이 검증할 것: 401 응답 후 토큰을 다시 발급받고 원래 요청을 안전하게 재시도하는가.

---

## 검증 실패 예시

`ScenarioValidator`가 어떤 오류를 어떻게 보고하는지 보여주는 예시다. 실행용이 아니라
검증 동작을 고정하는 예시이므로 이 카탈로그가 아니라
`scenario/src/test/resources/invalid/`에 둔다. 여기에 두면 사용자가 실수로 실행하게 되고
카탈로그 검증 테스트도 이 파일들을 유효한 시나리오로 검사한다.

| 파일 | 거부 사유 | 보고 필드 |
|---|---|---|
| `ratio-out-of-range.yml` | `ratio`가 1을 넘음 | `execution.fills[0].ratio`, `execution.fills` |
| `ratio-and-quantity.yml` | `ratio`와 `quantity` 동시 사용 | `execution.fills[0]` |
| `ratio-sum-above-one.yml` | 비율 합계가 1 초과 | `execution.fills` |
| `bad-duration.yml` | 단위 없는 시간 문자열 | `execution.fills[0].after` |
| `bad-constraints.yml` | `per_sec < 1`, `token_ttl <= 0` | `constraints.*` |

검증기는 첫 오류에서 멈추지 않고 모든 문제를 모아 보고한다.
`InvalidScenarioCatalogTest`가 각 파일이 어떤 필드를 보고하는지 고정한다.

## 시나리오를 추가할 때

- 순수 데이터만 넣는다. 클래스 이름, 셸 명령, URL은 넣지 않는다. 로더가 거부한다.
- 새 필드를 넣기 전에 `ScenarioSpec`을 먼저 확인한다. 알 수 없는 필드는 거부된다.
- `ScenarioCatalogTest`(SCENARIO-01)가 이 디렉터리 전체를 읽으므로, 파일을 추가하면 자동으로
  검증된다.
- 검증 실패를 보여주는 예시는 여기가 아니라 `scenario/src/test/resources/invalid/`에 넣는다.
