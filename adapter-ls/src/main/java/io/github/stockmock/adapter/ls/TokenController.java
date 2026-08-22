package io.github.stockmock.adapter.ls;

import java.time.Duration;
import java.util.UUID;

/**
 * LS 형태의 Mock 토큰 발급 endpoint다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code request}: null이 아닌 {@link TokenRequest}</li>
 *   <li>{@code configuredTtl}: 시나리오에서 전달된 0보다 큰 토큰 유효기간</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <ul>
 *   <li>{@code accessToken}: 비어 있지 않은 Mock 토큰</li>
 *   <li>{@code tokenType}: {@code Bearer}</li>
 *   <li>{@code expiresInSeconds}: TTL을 초로 변환한 양수</li>
 * </ul>
 *
 * <h2>경계와 오류</h2>
 * <ul>
 *   <li>요청, grant type, app key, secret 중 하나라도 없으면 {@link LsRequestException}</li>
 *   <li>TTL이 0 또는 음수이면 {@link IllegalArgumentException}</li>
 *   <li>실제 키 인증, 암호화, 외부 네트워크 호출은 하지 않는다.</li>
 * </ul>
 *
 * <p>구현 완료 후에만 Spring Controller 어노테이션과 공식 URL을 추가한다.
 * JSON 필드명은 공식 LS fixture로 검증한다.</p>
 */
public final class TokenController {
    public TokenResponse issue(TokenRequest request, Duration configuredTtl) {
        if (request == null) {
            throw new LsRequestException("요청 본문이 없습니다.");
        }
        requireNonBlank(request.grantType(), "grant_type");
        requireNonBlank(request.appKey(), "appkey");
        requireNonBlank(request.appSecretKey(), "appsecretkey");
        if (configuredTtl == null || configuredTtl.isZero() || configuredTtl.isNegative()) {
            throw new IllegalArgumentException("token TTL은 0보다 커야 합니다.");
        }

        String accessToken = "mock-" + UUID.randomUUID();
        return new TokenResponse(accessToken, "Bearer", configuredTtl.toSeconds());
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new LsRequestException(fieldName + "이(가) 없습니다.");
        }
    }
}
