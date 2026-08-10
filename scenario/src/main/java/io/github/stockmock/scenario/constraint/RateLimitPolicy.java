package io.github.stockmock.scenario.constraint;

import java.time.Instant;

/**
 * TODO(SCENARIO-05): 호출자와 operation별 1초 고정 구간 rate limit을 구현한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code clientId}: 호출자를 구분하는 비어 있지 않은 문자열</li>
 *   <li>{@code operation}: null이 아닌 {@link Operation}</li>
 *   <li>{@code currentVirtualTime}: 요청이 도착한 가상시각</li>
 *   <li>{@code perSecond}: 같은 1초 구간에 허용할 요청 수, 1 이상</li>
 * </ul>
 *
 * <h2>출력과 상태 변경</h2>
 * <ul>
 *   <li>해당 구간의 {@code 1..perSecond}번째 요청: {@link RateLimitDecision#ALLOW}</li>
 *   <li>{@code perSecond + 1}번째 이후 요청: {@link RateLimitDecision#RATE_LIMITED}</li>
 *   <li>다음 epoch-second 구간에서는 새 카운트를 사용한다.</li>
 *   <li>clientId 또는 operation이 다르면 별도 카운트를 사용한다.</li>
 * </ul>
 *
 * <h2>경계와 오류</h2>
 * <ul>
 *   <li>perSecond가 1보다 작으면 {@link IllegalArgumentException}</li>
 *   <li>null/빈 clientId, null operation/time은 {@link IllegalArgumentException}</li>
 *   <li>동시 요청에서도 허용 건수가 초과되지 않도록 평가와 카운트 증가를 원자적으로 처리한다.</li>
 * </ul>
 *
 * <p>RATE_LIMITED 요청은 엔진으로 전달하지 않는다. 이 클래스는 HTTP 또는 LS 응답을 만들지 않는다.</p>
 */
public final class RateLimitPolicy {
    public RateLimitDecision evaluate(
            String clientId,
            Operation operation,
            Instant currentVirtualTime,
            int perSecond
    ) {
        throw new UnsupportedOperationException("TODO(SCENARIO-05): rate limit 판단");
    }
}
