# adapter-ls 시작 가이드

## 역할

LS 요청을 core 명령으로 바꾸고 core 결과를 LS 응답으로 바꾼다. 주문이나 계좌 상태를 직접 변경하지 않는다.

## 현재 예제

- `CashBuyOrderHandler`: 주문 Handler 예제
- `BalanceQueryHandler`: 조회 Handler 예제
- `LsTrDispatcherTest`: 새 Handler를 등록하고 테스트하는 최소 예제

새 Handler에는 `@Component`를 붙이면 `LsTrDispatcher`가 자동 등록한다. 디스패처 생성자에 직접 추가하지 않는다.

```java
@Component
final class ExampleHandler implements TrHandler {
    private final EnginePort engine;

    ExampleHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "확인한_TR_코드";
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        // 1. 필수 필드 검사
        // 2. core 명령 또는 조회 생성
        // 3. EnginePort 호출
        // 4. LS OutBlock 변환
        throw new UnsupportedOperationException("TODO");
    }
}
```

## 첫 작업

1. [`../TODO.md`](../TODO.md)의 `ADAPTER-01`을 읽는다.
2. LS 공식 콘솔에서 요청·응답을 확인한다.
3. 민감 정보를 제거한 fixture를 `src/test/resources/fixtures`에 저장한다.
4. `LsTrDispatcherTest` 패턴으로 Handler 단위 테스트부터 작성한다.
5. Handler를 구현한다.
6. 아래 명령으로 확인한다.

```bash
./gradlew :adapter-ls:test
```

## 하지 말 것

- `Account`, `Order`, `SimulationEngine` 직접 접근
- 현금·체결 수량 직접 계산
- core에 LS 필드 추가
- 확인하지 않은 응답 필드 추측
- 하나의 Controller에 모든 TR 로직 작성
