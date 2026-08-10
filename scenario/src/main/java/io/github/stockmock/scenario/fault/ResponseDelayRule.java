package io.github.stockmock.scenario.fault;

import io.github.stockmock.scenario.constraint.Operation;

import java.time.Duration;

/** 응답 지연 정책이 비교할 파싱 완료 규칙이다. */
public record ResponseDelayRule(Operation operation, FaultTiming timing, Duration delay) {
}
