# 모듈 간 계약

세 모듈이 같은 의미로 개발하기 위한 합의 문서다. 계약을 변경하는 PR은 다른 두 담당자의 리뷰를 받는다.

## 의존 방향

```text
app → adapter-ls → core ← scenario
```

- `core`는 Spring, YAML, LS 용어를 모른다.
- `adapter-ls`는 core의 공개 명령과 조회 타입만 사용한다.
- `scenario`는 core의 `FillPlanProvider` 같은 정책 포트를 구현한다.
- `app`은 모듈을 조립하고 업무 규칙을 갖지 않는다.

## 주문 명령

```java
public record PlaceOrder(
    String clientOrderId,
    Symbol symbol,
    Side side,
    long qty,
    long price
) {}
```

- `qty` 단위는 주이며 1 이상이다.
- `price` 단위는 1주당 원화이며 1 이상이다.
- `submit()`은 주문 접수 또는 거부 시점에 완료한다. 체결 완료까지 기다리지 않는다.
- 주문번호는 core가 만든다. adapter가 LS 형식으로 변환한다.

## 취소 명령

MVP에서는 대상 주문의 미체결 수량 전체를 취소한다. 부분 취소는 지원하지 않는다.

- 부분체결 30/100주 주문을 취소하면 70주가 취소된다.
- 체결된 30주는 되돌리지 않는다.
- 미체결 70주의 잠긴 현금만 반환한다.

## 조회

- 계좌 조회와 주문 조회 모두 DES 큐를 통과한다.
- 주문 조회는 주문 상태, 전체 수량, 체결 수량, 미체결 수량, 가격을 반환한다.
- 취소된 주문도 조회할 수 있다.
- 존재하지 않는 주문은 공통 오류로 반환하고 adapter가 LS 오류로 바꾼다.

## FillPlan

- `FillPlan`은 주문 접수 시점에 정확히 한 번 생성한다.
- 모든 `after`는 주문 접수 시각 기준이다.
- 모든 `ratio`는 최초 주문 수량 기준이다.
- ratio는 접수 시 실제 수량으로 바뀌며 체결 시점에 난수를 사용하지 않는다.
- 체결 계획의 총수량은 주문 수량을 넘을 수 없다.

예:

```yaml
execution:
  fills:
    - after: 1s
      ratio: 0.3
    - after: 5s
      ratio: 0.7
```

100주 주문의 계획은 `1초 뒤 30주`, `5초 뒤 70주`다.

## 설정 분류

```text
constraints: 평상시 운영 규칙(rate limit, token TTL)
execution:   정상적인 체결 결과(전량체결, 부분체결, 분할체결)
faults:      의도적으로 주입하는 장애(거부, 응답 지연)
```

부분체결 자체는 장애가 아니다. 사용자가 선택할 수 있는 정상 체결 시나리오다.

## 응답 지연

`AFTER_COMMIT` 응답 지연은 다음 의미다.

```text
core 주문 접수 완료
→ 주문번호 생성 및 현금 잠금 완료
→ HTTP 응답만 지연
```

시나리오 정책은 지연 시간을 반환하고 실제 HTTP 지연은 조립 계층에서 적용한다.

## 시간

- core에서 `Instant.now()`와 `System.currentTimeMillis()`를 직접 호출하지 않는다.
- 정책은 가상 시각을 전달받아 판단한다.
- 동일 시각 사건은 `seq`가 작은 순서대로 실행한다.

## 오류 책임

```text
core:       INVALID_REQUEST, ORDER_NOT_FOUND 같은 중립 오류
adapter-ls: rsp_cd, rsp_msg 같은 LS 오류 봉투
scenario:   field path와 의미 검증 오류
```

core에는 `rsp_cd`, TR, InBlock, OutBlock 문자열이 등장하면 안 된다.

Adapter는 core 예외의 메시지 문자열을 비교하지 않고 `CoreException.code()`를 기준으로
증권사 오류 봉투를 만든다. 예를 들어 `ORDER_NOT_FOUND`는 LS 주문 없음 오류로 변환한다.
