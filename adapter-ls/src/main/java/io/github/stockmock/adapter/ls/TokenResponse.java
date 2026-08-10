package io.github.stockmock.adapter.ls;

/**
 * Mock 토큰 발급 출력 계약이다. JSON 필드명은 공식 fixture를 확인한 뒤 Controller에서 확정한다.
 *
 * @param accessToken 후속 Bearer 인증에 사용할 토큰 문자열
 * @param tokenType 항상 {@code Bearer}
 * @param expiresInSeconds 발급 시점부터 만료까지의 초 단위 양수
 */
public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
