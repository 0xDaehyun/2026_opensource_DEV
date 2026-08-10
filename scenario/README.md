# scenario 시작 가이드

## 역할

순수 데이터 YAML을 읽고 검증하며 core가 사용할 체결 계획과 요청 정책을 만든다. 주문이나 계좌 상태를 직접 수정하지 않는다.

## 준비된 골격

- `ScenarioSpec`: YAML 입력 구조
- `ScenarioLoader`: unknown field를 거부하는 strict loader
- `ScenarioValidator`: 이름과 초기 현금 검증 예제
- `ScenarioLoaderTest`: 정상 YAML과 unknown field 테스트 예제
- `scenarios/`: 사용자가 실행할 카탈로그

## 첫 작업

1. [`../TODO.md`](../TODO.md)의 `SCENARIO-02`를 읽는다.
2. 검증 규칙 하나의 실패 테스트를 먼저 작성한다.
3. `ScenarioValidator`에 해당 규칙을 작은 메서드로 구현한다.
4. 테스트가 통과하면 다음 규칙으로 이동한다.

```bash
./gradlew :scenario:test
```

## 데이터 처리 순서

```text
YAML
→ ScenarioLoader
→ ScenarioSpec
→ ScenarioValidator
→ ScenarioFillPlanProvider 또는 정책
→ core가 실행
```

## 하지 말 것

- YAML에서 클래스 이름을 받아 실행
- 셸·스크립트·네트워크 훅 추가
- `Thread.sleep()`으로 지연 구현
- `Order`나 `Account` 직접 수정
- LS JSON 또는 `rsp_cd` 생성
- `Instant.now()`로 정책 시간 판단
