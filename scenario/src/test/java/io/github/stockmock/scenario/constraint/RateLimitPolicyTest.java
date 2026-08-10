package io.github.stockmock.scenario.constraint;

/**
 * TODO(SCENARIO-05) 테스트 목록:
 * <ul>
 *   <li>perSecond=2에서 1·2번째 ALLOW, 3번째 RATE_LIMITED</li>
 *   <li>다음 1초 구간에서 다시 ALLOW</li>
 *   <li>clientId와 operation별 독립 카운트</li>
 *   <li>잘못된 입력과 동시 요청 경계 검증</li>
 * </ul>
 */
class RateLimitPolicyTest {
}
