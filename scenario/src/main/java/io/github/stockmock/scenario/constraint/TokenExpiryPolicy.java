package io.github.stockmock.scenario.constraint;

import java.time.Duration;
import java.time.Instant;

/**
 * TODO(SCENARIO-05): 가상시각을 기준으로 토큰 만료 여부를 판단한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code issuedAt}: 토큰이 발급된 가상시각</li>
 *   <li>{@code currentVirtualTime}: 검사 시점의 가상시각</li>
 *   <li>{@code ttl}: 0보다 큰 토큰 유효기간</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <ul>
 *   <li>{@code currentVirtualTime < issuedAt + ttl}: {@link TokenStatus#VALID}</li>
 *   <li>{@code currentVirtualTime >= issuedAt + ttl}: {@link TokenStatus#EXPIRED}</li>
 * </ul>
 *
 * <h2>경계와 오류</h2>
 * <ul>
 *   <li>정확히 만료 시각과 같으면 EXPIRED</li>
 *   <li>현재 시각이 발급 시각보다 이전이면 {@link IllegalArgumentException}</li>
 *   <li>TTL이 0 또는 음수이면 {@link IllegalArgumentException}</li>
 *   <li>null 입력은 허용하지 않는다.</li>
 * </ul>
 *
 * <p>토큰 문자열, HTTP 상태, LS 오류 봉투는 만들지 않으며 시스템 현재 시각을 직접 조회하지 않는다.</p>
 */
public final class TokenExpiryPolicy {
    public TokenStatus evaluate(Instant issuedAt, Instant currentVirtualTime, Duration ttl) {
        throw new UnsupportedOperationException("TODO(SCENARIO-05): 토큰 만료 판단");
    }
}
