package io.github.stockmock.adapter.ls;

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
}
