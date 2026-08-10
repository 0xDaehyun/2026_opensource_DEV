package io.github.stockmock.scenario.fault;

import io.github.stockmock.scenario.constraint.Operation;

import java.time.Duration;
import java.util.Optional;

/**
 * TODO(SCENARIO-06): 현재 요청에 적용할 응답 지연을 선택한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code operation}: 현재 처리 중인 요청 종류</li>
 *   <li>{@code timing}: 현재 요청 파이프라인 위치</li>
 *   <li>{@code configuredRule}: YAML을 파싱하고 검증한 단일 지연 규칙</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <ul>
 *   <li>operation과 timing이 모두 일치: 규칙의 양수 delay를 포함한 Optional</li>
 *   <li>하나라도 불일치: {@link Optional#empty()}</li>
 * </ul>
 *
 * <h2>경계와 오류</h2>
 * <ul>
 *   <li>null 입력은 {@link IllegalArgumentException}</li>
 *   <li>0 또는 음수 delay 규칙은 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * <p>이 클래스는 {@code Thread.sleep()}, HTTP 지연, core 상태 변경을 직접 수행하지 않는다.
 * AFTER_COMMIT은 core 처리가 완료된 뒤 응답 반환만 늦춘다는 의미다.</p>
 */
public final class ResponseDelayPolicy {
    public Optional<Duration> delayFor(
            Operation operation,
            FaultTiming timing,
            ResponseDelayRule configuredRule
    ) {
        throw new UnsupportedOperationException("TODO(SCENARIO-06): 응답 지연 선택");
    }
}
