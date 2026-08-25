package io.github.stockmock.adapter.ls;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(ADAPTER-03) 테스트 목록:
 * <ul>
 *   <li>정상 요청은 비어 있지 않은 토큰, Bearer, 양수 expiresIn을 반환한다.</li>
 *   <li>TTL 10초는 expiresInSeconds 10으로 반환한다.</li>
 *   <li>null/빈 필수 필드는 LsRequestException을 발생시킨다.</li>
 *   <li>0 또는 음수 TTL은 IllegalArgumentException을 발생시킨다.</li>
 * </ul>
 */
class TokenControllerTest {
    private final TokenController controller = new TokenController();

    @Test
    void issueWithValidRequestReturnsBearerToken() {
        TokenResponse response = controller.issue(validRequest(), Duration.ofSeconds(60));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isPositive();
    }

    @Test
    void issueRespectsConfiguredTtlInSeconds() {
        TokenResponse response = controller.issue(validRequest(), Duration.ofSeconds(10));

        assertThat(response.expiresInSeconds()).isEqualTo(10);
    }

    @Test
    void issueRejectsMissingGrantType() {
        TokenRequest request = new TokenRequest(null, "app-key", "app-secret");

        assertThatThrownBy(() -> controller.issue(request, Duration.ofSeconds(60)))
                .isInstanceOf(LsRequestException.class);
    }

    @Test
    void issueRejectsBlankAppKey() {
        TokenRequest request = new TokenRequest("client_credentials", "  ", "app-secret");

        assertThatThrownBy(() -> controller.issue(request, Duration.ofSeconds(60)))
                .isInstanceOf(LsRequestException.class);
    }

    @Test
    void issueRejectsMissingAppSecretKey() {
        TokenRequest request = new TokenRequest("client_credentials", "app-key", null);

        assertThatThrownBy(() -> controller.issue(request, Duration.ofSeconds(60)))
                .isInstanceOf(LsRequestException.class);
    }

    @Test
    void issueRejectsZeroTtl() {
        assertThatThrownBy(() -> controller.issue(validRequest(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issueRejectsNegativeTtl() {
        assertThatThrownBy(() -> controller.issue(validRequest(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TokenRequest validRequest() {
        return new TokenRequest("client_credentials", "app-key", "app-secret");
    }
}
