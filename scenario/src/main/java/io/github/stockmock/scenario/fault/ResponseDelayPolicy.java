package io.github.stockmock.scenario.fault;

import io.github.stockmock.scenario.constraint.Operation;

import java.time.Duration;
import java.util.Optional;

/**
 * 현재 요청에 적용할 응답 지연을 선택한다.
 *
 * <h2>판정</h2>
 * <ul>
 *   <li>operation과 timing이 모두 일치: 규칙의 delay를 담은 Optional</li>
 *   <li>하나라도 불일치, 또는 규칙이 없음: {@link Optional#empty()}</li>
 * </ul>
 *
 * <p>이 클래스는 {@code Thread.sleep()}, HTTP 지연, core 상태 변경을 직접 수행하지 않는다.
 * 지연 시간을 반환할 뿐이고 실제 지연은 조립 계층이 적용한다.</p>
 *
 * <p>{@code AFTER_COMMIT}은 core 처리가 끝나 주문번호 생성과 현금 잠금이 완료된 뒤
 * HTTP 응답 반환만 늦춘다는 의미다. 주문 자체는 이미 접수된 상태다.</p>
 */
public final class ResponseDelayPolicy {
    public Optional<Duration> delayFor(
            Operation operation,
            FaultTiming timing,
            ResponseDelayRule configuredRule
    ) {
        if (operation == null || timing == null) {
            throw new IllegalArgumentException("operation과 timing이 필요합니다");
        }
        if (configuredRule == null) {
            return Optional.empty();
        }
        requireUsableRule(configuredRule);

        boolean matches = configuredRule.operation() == operation && configuredRule.timing() == timing;
        return matches ? Optional.of(configuredRule.delay()) : Optional.empty();
    }

    /** 검증기를 통과하지 않은 규칙이 조용히 무시되지 않도록 여기서도 막는다. */
    private void requireUsableRule(ResponseDelayRule rule) {
        if (rule.operation() == null || rule.timing() == null || rule.delay() == null) {
            throw new IllegalArgumentException("지연 규칙에 operation, timing, delay가 모두 필요합니다");
        }
        if (rule.delay().isZero() || rule.delay().isNegative()) {
            throw new IllegalArgumentException("지연 시간은 0보다 커야 합니다: " + rule.delay());
        }
    }
}
