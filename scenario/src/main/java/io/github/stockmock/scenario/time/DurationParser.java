package io.github.stockmock.scenario.time;

import java.time.Duration;

/**
 * TODO(SCENARIO-03): 시나리오의 짧은 시간 문자열을 {@link Duration}으로 변환한다.
 *
 * <h2>입력</h2>
 * <p>{@code value}: 숫자와 단위가 붙은 null이 아닌 문자열.</p>
 *
 * <h2>지원 단위</h2>
 * <ul>
 *   <li>{@code 500ms} → 500 milliseconds</li>
 *   <li>{@code 1s} → 1 second</li>
 *   <li>{@code 10m} → 10 minutes</li>
 *   <li>{@code 24h} → 24 hours</li>
 * </ul>
 *
 * <h2>오류</h2>
 * <ul>
 *   <li>null, 빈 문자열, 단위 없는 값은 {@link IllegalArgumentException}</li>
 *   <li>0과 음수 시간은 {@link IllegalArgumentException}</li>
 *   <li>소수, 공백 포함, 알 수 없는 단위는 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * <p>이 클래스는 현재 시각을 조회하거나 대기하지 않는다.</p>
 */
public final class DurationParser {
    public Duration parse(String value) {
        throw new UnsupportedOperationException("TODO(SCENARIO-03): 시간 문자열 파싱");
    }
}
