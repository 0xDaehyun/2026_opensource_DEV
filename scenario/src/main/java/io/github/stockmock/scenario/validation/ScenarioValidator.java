package io.github.stockmock.scenario.validation;

import io.github.stockmock.scenario.spec.ScenarioSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 의미 검증기의 시작점이다. TODO(SCENARIO-02)의 규칙을 이 클래스에 작은 메서드로 추가한다.
 */
public final class ScenarioValidator {
    public List<ValidationIssue> validate(ScenarioSpec spec) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (spec == null) {
            return List.of(new ValidationIssue("scenario", "시나리오 본문이 필요합니다"));
        }
        if (spec.scenario() == null || spec.scenario().isBlank()) {
            issues.add(new ValidationIssue("scenario", "시나리오 이름이 필요합니다"));
        }
        if (spec.account() == null || spec.account().cash() == null) {
            issues.add(new ValidationIssue("account.cash", "초기 현금이 필요합니다"));
        } else if (spec.account().cash() < 0) {
            issues.add(new ValidationIssue("account.cash", "초기 현금은 음수일 수 없습니다"));
        }

        // TODO(SCENARIO-02): ratio/quantity 상호 배타, 비율 합계, 지연 문자열, rate limit, token TTL을 검증한다.
        return List.copyOf(issues);
    }
}
