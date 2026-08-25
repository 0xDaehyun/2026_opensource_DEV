package io.github.stockmock.scenario.constraint;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 호출자와 operation별로 1초 고정 구간 rate limit을 판단한다.
 *
 * <h2>판정</h2>
 * <ul>
 *   <li>해당 구간의 {@code 1..perSecond}번째 요청: {@link RateLimitDecision#ALLOW}</li>
 *   <li>{@code perSecond + 1}번째 이후: {@link RateLimitDecision#RATE_LIMITED}</li>
 *   <li>다음 epoch-second 구간에서는 새 카운트를 쓴다.</li>
 *   <li>clientId 또는 operation이 다르면 별도 카운트를 쓴다.</li>
 * </ul>
 *
 * <p>구간은 슬라이딩 윈도가 아니라 epoch-second 고정 구간이다. 같은 초 안이면 밀리초가
 * 달라도 같은 구간으로 센다.</p>
 *
 * <p>RATE_LIMITED 요청은 엔진으로 전달하지 않는다. 이 클래스는 HTTP 또는 LS 응답을 만들지
 * 않으며 시스템 현재 시각을 직접 조회하지 않는다.</p>
 */
public final class RateLimitPolicy {
    private final Map<Key, Window> windows = new ConcurrentHashMap<>();

    public RateLimitDecision evaluate(
            String clientId,
            Operation operation,
            Instant currentVirtualTime,
            int perSecond
    ) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId가 필요합니다");
        }
        if (operation == null || currentVirtualTime == null) {
            throw new IllegalArgumentException("operation과 현재 시각이 필요합니다");
        }
        if (perSecond < 1) {
            throw new IllegalArgumentException("초당 허용 호출 수는 1 이상이어야 합니다: " + perSecond);
        }

        long second = currentVirtualTime.getEpochSecond();
        // compute는 키 단위로 원자적이다. 평가와 증가를 나누면 동시 요청에서 한도를 넘긴다.
        Window window = windows.compute(new Key(clientId, operation), (key, current) ->
                current == null || current.epochSecond() != second
                        ? new Window(second, 1)
                        : new Window(second, current.count() + 1));

        return window.count() <= perSecond ? RateLimitDecision.ALLOW : RateLimitDecision.RATE_LIMITED;
    }

    private record Key(String clientId, Operation operation) {
    }

    /** 카운트가 int 범위를 넘지 않도록 한도를 넘어선 뒤에는 더 올리지 않는다. */
    private record Window(long epochSecond, int count) {
        Window {
            count = Math.min(count, Integer.MAX_VALUE - 1);
        }
    }
}
