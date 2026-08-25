package io.github.stockmock.scenario.fault;

import io.github.stockmock.scenario.constraint.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(SCENARIO-06) 테스트 목록:
 * <ul>
 *   <li>PLACE_ORDER와 CANCEL을 구분한다.</li>
 *   <li>BEFORE_COMMIT과 AFTER_COMMIT을 구분한다.</li>
 *   <li>operation과 timing이 모두 맞을 때만 지연을 반환한다.</li>
 * </ul>
 */
class ResponseDelayPolicyTest {
    private static final Duration THREE_SECONDS = Duration.ofSeconds(3);

    private final ResponseDelayPolicy policy = new ResponseDelayPolicy();

    @Test
    void returnsTheDelayWhenOperationAndTimingBothMatch() {
        ResponseDelayRule rule =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, THREE_SECONDS);

        assertThat(policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, rule))
                .contains(THREE_SECONDS);
    }

    @Test
    void distinguishesPlaceOrderFromCancel() {
        ResponseDelayRule rule =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, THREE_SECONDS);

        assertThat(policy.delayFor(Operation.CANCEL, FaultTiming.AFTER_COMMIT, rule)).isEmpty();
    }

    @Test
    void distinguishesBeforeCommitFromAfterCommit() {
        ResponseDelayRule rule =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, THREE_SECONDS);

        assertThat(policy.delayFor(Operation.PLACE_ORDER, FaultTiming.BEFORE_COMMIT, rule)).isEmpty();
    }

    @Test
    void appliesACancelRuleOnlyToCancel() {
        ResponseDelayRule rule =
                new ResponseDelayRule(Operation.CANCEL, FaultTiming.BEFORE_COMMIT, THREE_SECONDS);

        assertThat(policy.delayFor(Operation.CANCEL, FaultTiming.BEFORE_COMMIT, rule))
                .contains(THREE_SECONDS);
        assertThat(policy.delayFor(Operation.PLACE_ORDER, FaultTiming.BEFORE_COMMIT, rule)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void matchesEveryOperationAgainstItsOwnRule(Operation operation) {
        ResponseDelayRule rule =
                new ResponseDelayRule(operation, FaultTiming.AFTER_COMMIT, THREE_SECONDS);

        assertThat(policy.delayFor(operation, FaultTiming.AFTER_COMMIT, rule)).contains(THREE_SECONDS);
    }

    @Test
    void returnsEmptyWhenNoRuleIsConfigured() {
        assertThat(policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, null))
                .isEqualTo(Optional.empty());
    }

    @Test
    void rejectsNullOperationOrTiming() {
        ResponseDelayRule rule =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, THREE_SECONDS);

        assertThatThrownBy(() -> policy.delayFor(null, FaultTiming.AFTER_COMMIT, rule))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayFor(Operation.PLACE_ORDER, null, rule))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARuleWithANonPositiveDelay() {
        ResponseDelayRule zero =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, Duration.ZERO);
        ResponseDelayRule negative = new ResponseDelayRule(
                Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, Duration.ofSeconds(-1));

        assertThatThrownBy(() -> policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, negative))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARuleWithMissingFields() {
        ResponseDelayRule noDelay =
                new ResponseDelayRule(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, null);

        assertThatThrownBy(() -> policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, noDelay))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 정책은 지연 시간을 반환만 한다. 실제 지연은 조립 계층이 적용한다. 이 테스트는
     * delayFor가 즉시 반환하는지 확인해 Thread.sleep 회귀를 막는다.
     */
    @Test
    void neverSleepsWhileDeciding() {
        ResponseDelayRule rule = new ResponseDelayRule(
                Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, Duration.ofSeconds(30));

        long startNanos = System.nanoTime();
        policy.delayFor(Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, rule);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(1_000);
    }
}
