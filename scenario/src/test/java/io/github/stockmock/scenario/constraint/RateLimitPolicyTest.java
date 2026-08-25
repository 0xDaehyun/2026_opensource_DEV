package io.github.stockmock.scenario.constraint;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(SCENARIO-05) 테스트 목록:
 * <ul>
 *   <li>1초 구간에서 perSecond번째까지 ALLOW, 그 이후 RATE_LIMITED다.</li>
 *   <li>다음 epoch-second 구간에서 카운트가 초기화된다.</li>
 *   <li>clientId 또는 operation이 다르면 별도 카운트를 쓴다.</li>
 *   <li>동시 요청에서도 허용 건수를 넘지 않는다.</li>
 * </ul>
 */
class RateLimitPolicyTest {
    private static final Instant START = Instant.parse("2026-01-02T00:00:00Z");

    private final RateLimitPolicy policy = new RateLimitPolicy();

    @Test
    void allowsUpToTheLimitThenRejects() {
        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 2))
                .isEqualTo(RateLimitDecision.ALLOW);
        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 2))
                .isEqualTo(RateLimitDecision.ALLOW);
        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 2))
                .isEqualTo(RateLimitDecision.RATE_LIMITED);
        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 2))
                .isEqualTo(RateLimitDecision.RATE_LIMITED);
    }

    /** 같은 epoch-second 안이면 밀리초가 달라도 같은 구간이다. */
    @Test
    void treatsTheWholeEpochSecondAsOneWindow() {
        policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 1);

        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START.plusMillis(999), 1))
                .isEqualTo(RateLimitDecision.RATE_LIMITED);
    }

    @Test
    void resetsTheCountInTheNextSecond() {
        policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 1);
        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 1))
                .isEqualTo(RateLimitDecision.RATE_LIMITED);

        assertThat(policy.evaluate("bot-1", Operation.PLACE_ORDER, START.plusSeconds(1), 1))
                .isEqualTo(RateLimitDecision.ALLOW);
    }

    @Test
    void countsEachClientSeparately() {
        policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 1);

        assertThat(policy.evaluate("bot-2", Operation.PLACE_ORDER, START, 1))
                .isEqualTo(RateLimitDecision.ALLOW);
    }

    @Test
    void countsEachOperationSeparately() {
        policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 1);

        assertThat(policy.evaluate("bot-1", Operation.CANCEL, START, 1))
                .isEqualTo(RateLimitDecision.ALLOW);
    }

    @Test
    void rejectsALimitBelowOne() {
        assertThatThrownBy(() -> policy.evaluate("bot-1", Operation.PLACE_ORDER, START, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate("bot-1", Operation.PLACE_ORDER, START, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> policy.evaluate(null, Operation.PLACE_ORDER, START, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate("  ", Operation.PLACE_ORDER, START, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate("bot-1", null, START, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate("bot-1", Operation.PLACE_ORDER, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 평가와 카운트 증가가 원자적이지 않으면 동시 요청에서 허용 건수를 넘긴다.
     * 100건을 동시에 보내도 ALLOW는 정확히 limit개여야 한다.
     */
    @Test
    void neverExceedsTheLimitUnderConcurrentRequests() throws Exception {
        int limit = 10;
        int requests = 100;

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            var tasks = IntStream.range(0, requests)
                    .<Callable<RateLimitDecision>>mapToObj(index ->
                            () -> policy.evaluate("bot-1", Operation.PLACE_ORDER, START, limit))
                    .collect(Collectors.toList());

            long allowed = pool.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .filter(RateLimitDecision.ALLOW::equals)
                    .count();

            assertThat(allowed).isEqualTo(limit);
        }
    }
}
