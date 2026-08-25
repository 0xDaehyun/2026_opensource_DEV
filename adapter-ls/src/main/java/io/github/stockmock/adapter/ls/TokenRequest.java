package io.github.stockmock.adapter.ls;

/**
 * Mock 토큰 발급 입력 계약이다. 필드 이름은
 * {@code src/test/resources/fixtures/token-issue.json}의 공식 파라미터
 * ({@code appkey}, {@code appsecretkey}, {@code grant_type}, {@code scope})와 대응한다.
 *
 * @param grantType LS 공식 fixture에서 확인한 grant type
 * @param appKey 애플리케이션 키. Mock에서는 형식만 검사한다.
 * @param appSecretKey 애플리케이션 비밀키. Mock에서는 형식만 검사한다.
 * @param scope 비어 있으면 공식 fixture 샘플 값인 {@code oob}로 채운다.
 */
public record TokenRequest(String grantType, String appKey, String appSecretKey, String scope) {
    public TokenRequest {
        if (scope == null || scope.isBlank()) {
            scope = "oob";
        }
    }

    public TokenRequest(String grantType, String appKey, String appSecretKey) {
        this(grantType, appKey, appSecretKey, "oob");
    }
}
