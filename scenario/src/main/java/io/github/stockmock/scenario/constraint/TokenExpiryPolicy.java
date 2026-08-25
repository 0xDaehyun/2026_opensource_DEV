package io.github.stockmock.scenario.constraint;

import java.time.Duration;
import java.time.Instant;

/**
 * 가상시각을 기준으로 토큰 만료 여부를 판단한다.
 *
 * <h2>판정</h2>
 * <ul>
 *   <li>{@code currentVirtualTime < issuedAt + ttl}: {@link TokenStatus#VALID}</li>
 *   <li>{@code currentVirtualTime >= issuedAt + ttl}: {@link TokenStatus#EXPIRED}</li>
 * </ul>
 *
 * <p>경계는 만료 쪽이다. 정확히 만료 시각이면 이미 EXPIRED다.</p>
 *
 * <p>토큰 문자열, HTTP 상태, LS 오류 봉투는 만들지 않으며 시스템 현재 시각을 직접 조회하지
 * 않는다. 판단에 쓰는 시간은 전부 인자로 받는다.</p>
 */
public final class TokenExpiryPolicy {
    public TokenStatus evaluate(Instant issuedAt, Instant currentVirtualTime, Duration ttl) {
        if (issuedAt == null || currentVirtualTime == null || ttl == null) {
            throw new IllegalArgumentException("발급 시각, 현재 시각, TTL이 모두 필요합니다");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("토큰 TTL은 0보다 커야 합니다: " + ttl);
        }
        if (currentVirtualTime.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                    "현재 시각이 발급 시각보다 이릅니다: " + currentVirtualTime + " < " + issuedAt);
        }

        Instant expiresAt = issuedAt.plus(ttl);
        return currentVirtualTime.isBefore(expiresAt) ? TokenStatus.VALID : TokenStatus.EXPIRED;
    }
}
