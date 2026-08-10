package io.github.stockmock.scenario.spec;

import java.util.List;

/**
 * YAML 시나리오의 입력 데이터 구조다.
 *
 * <p>이 타입은 YAML을 표현할 뿐 주문이나 계좌 상태를 변경하지 않는다.
 * 시간 문자열을 {@code Duration}으로 바꾸고 체결 수량을 계산하는 일은 후속 정책 클래스가 담당한다.</p>
 */
public record ScenarioSpec(
        String scenario,
        AccountSpec account,
        ConstraintsSpec constraints,
        ExecutionSpec execution,
        FaultsSpec faults,
        Long seed
) {
    public record AccountSpec(Long cash) {
    }

    public record ConstraintsSpec(RateLimitSpec rateLimit, String tokenTtl) {
    }

    public record RateLimitSpec(Integer perSec) {
    }

    public record ExecutionSpec(List<FillSpec> fills) {
    }

    /** ratio와 quantity 중 정확히 하나만 사용한다. */
    public record FillSpec(String after, Double ratio, Long quantity) {
    }

    public record FaultsSpec(ResponseFaultSpec response) {
    }

    public record ResponseFaultSpec(String on, String timing, String delay) {
    }
}
