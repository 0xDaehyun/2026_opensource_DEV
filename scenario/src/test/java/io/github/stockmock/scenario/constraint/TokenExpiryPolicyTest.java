package io.github.stockmock.scenario.constraint;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(SCENARIO-05) 테스트 목록:
 * <ul>
 *   <li>TTL 이내는 VALID, 이후는 EXPIRED다.</li>
 *   <li>정확히 만료 시각과 같으면 EXPIRED다.</li>
 *   <li>발급 시각보다 이른 현재 시각, 0 이하 TTL, null을 거부한다.</li>
 * </ul>
 */
class TokenExpiryPolicyTest {
    private static final Instant ISSUED = Instant.parse("2026-01-02T00:00:00Z");

    private final TokenExpiryPolicy policy = new TokenExpiryPolicy();

    @Test
    void reportsValidBeforeTheTtlElapses() {
        assertThat(policy.evaluate(ISSUED, ISSUED, Duration.ofSeconds(10)))
                .isEqualTo(TokenStatus.VALID);
        assertThat(policy.evaluate(ISSUED, ISSUED.plusSeconds(9), Duration.ofSeconds(10)))
                .isEqualTo(TokenStatus.VALID);
        assertThat(policy.evaluate(ISSUED, ISSUED.plusMillis(9_999), Duration.ofSeconds(10)))
                .isEqualTo(TokenStatus.VALID);
    }

    /** 경계는 만료 쪽이다. issuedAt + ttl 시점에 이미 EXPIRED다. */
    @Test
    void reportsExpiredExactlyAtTheBoundary() {
        assertThat(policy.evaluate(ISSUED, ISSUED.plusSeconds(10), Duration.ofSeconds(10)))
                .isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    void reportsExpiredAfterTheTtlElapses() {
        assertThat(policy.evaluate(ISSUED, ISSUED.plusSeconds(11), Duration.ofSeconds(10)))
                .isEqualTo(TokenStatus.EXPIRED);
        assertThat(policy.evaluate(ISSUED, ISSUED.plus(Duration.ofDays(1)), Duration.ofHours(24)))
                .isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    void rejectsACurrentTimeBeforeIssuance() {
        assertThatThrownBy(() -> policy.evaluate(ISSUED, ISSUED.minusSeconds(1), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> policy.evaluate(ISSUED, ISSUED, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(ISSUED, ISSUED, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> policy.evaluate(null, ISSUED, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(ISSUED, null, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(ISSUED, ISSUED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 정책은 전달받은 가상시각만 본다. 시스템 시계를 읽으면 이 단언이 깨진다. */
    @Test
    void judgesOnlyByTheSuppliedVirtualTime() {
        Instant farFuture = Instant.parse("2999-01-01T00:00:00Z");

        assertThat(policy.evaluate(farFuture, farFuture, Duration.ofSeconds(1)))
                .isEqualTo(TokenStatus.VALID);
    }
}
