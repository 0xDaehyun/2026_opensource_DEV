package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mock 토큰 발급 출력 계약이다. JSON 필드명은
 * {@code src/test/resources/fixtures/token-issue.json}에서 확인한
 * 공식 응답 필드({@code access_token}, {@code scope}, {@code token_type},
 * {@code expires_in})와 정확히 일치한다.
 *
 * @param accessToken 후속 Bearer 인증에 사용할 토큰 문자열
 * @param scope 요청 scope를 그대로 반환한 값
 * @param tokenType 항상 {@code Bearer}
 * @param expiresInSeconds 발급 시점부터 만료까지의 초 단위 양수
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds) {
}
