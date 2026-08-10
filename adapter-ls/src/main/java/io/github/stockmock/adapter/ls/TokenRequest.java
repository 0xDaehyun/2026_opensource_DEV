package io.github.stockmock.adapter.ls;

/**
 * Mock 토큰 발급 입력 계약이다.
 *
 * @param grantType LS 공식 fixture에서 확인한 grant type
 * @param appKey 애플리케이션 키. Mock에서는 형식만 검사한다.
 * @param appSecretKey 애플리케이션 비밀키. Mock에서는 형식만 검사한다.
 */
public record TokenRequest(String grantType, String appKey, String appSecretKey) {
}
