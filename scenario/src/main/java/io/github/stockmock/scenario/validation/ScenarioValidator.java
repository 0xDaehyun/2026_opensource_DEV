package io.github.stockmock.scenario.validation;

import io.github.stockmock.scenario.constraint.Operation;
import io.github.stockmock.scenario.fault.FaultTiming;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.time.DurationParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * YAML을 읽은 뒤 의미 수준의 오류를 모은다.
 *
 * <p>첫 오류에서 멈추지 않고 모든 문제를 모아 반환한다. 사용자가 시나리오를 고칠 때 한 번에
 * 전부 보는 편이 낫기 때문이다.</p>
 *
 * <p>{@link DurationParser}는 잘못된 값만 알리고 자기가 어느 필드에서 왔는지 모른다.
 * YAML 경로는 이 클래스가 {@link ValidationIssue}로 붙인다.</p>
 */
public final class ScenarioValidator {
    private final DurationParser durationParser;

    public ScenarioValidator() {
        this(new DurationParser());
    }

    public ScenarioValidator(DurationParser durationParser) {
        this.durationParser = Objects.requireNonNull(durationParser, "durationParser");
    }

    public List<ValidationIssue> validate(ScenarioSpec spec) {
        if (spec == null) {
            return List.of(new ValidationIssue("scenario", "시나리오 본문이 필요합니다"));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        validateName(spec, issues);
        validateAccount(spec, issues);
        validateConstraints(spec.constraints(), issues);
        validateExecution(spec.execution(), issues);
        validateFaults(spec.faults(), issues);
        return List.copyOf(issues);
    }

    private void validateName(ScenarioSpec spec, List<ValidationIssue> issues) {
        if (spec.scenario() == null || spec.scenario().isBlank()) {
            issues.add(new ValidationIssue("scenario", "시나리오 이름이 필요합니다"));
        }
    }

    private void validateAccount(ScenarioSpec spec, List<ValidationIssue> issues) {
        if (spec.account() == null || spec.account().cash() == null) {
            issues.add(new ValidationIssue("account.cash", "초기 현금이 필요합니다"));
        } else if (spec.account().cash() < 0) {
            issues.add(new ValidationIssue("account.cash", "초기 현금은 음수일 수 없습니다"));
        }
    }

    private void validateConstraints(ScenarioSpec.ConstraintsSpec constraints, List<ValidationIssue> issues) {
        if (constraints == null) {
            return;
        }
        if (constraints.rateLimit() != null) {
            Integer perSec = constraints.rateLimit().perSec();
            if (perSec == null || perSec < 1) {
                issues.add(new ValidationIssue("constraints.rate_limit.per_sec",
                        "초당 허용 호출 수는 1 이상이어야 합니다: " + perSec));
            }
        }
        if (constraints.tokenTtl() != null) {
            positiveDuration(constraints.tokenTtl(), "constraints.token_ttl", issues);
        }
    }

    private void validateExecution(ScenarioSpec.ExecutionSpec execution, List<ValidationIssue> issues) {
        if (execution == null || execution.fills() == null || execution.fills().isEmpty()) {
            return;
        }

        List<ScenarioSpec.FillSpec> fills = execution.fills();
        for (int index = 0; index < fills.size(); index++) {
            validateFill(fills.get(index), "execution.fills[" + index + "]", issues);
        }
        validateRatioSum(fills, issues);
    }

    private void validateFill(ScenarioSpec.FillSpec fill, String path, List<ValidationIssue> issues) {
        boolean hasRatio = fill.ratio() != null;
        boolean hasQuantity = fill.quantity() != null;

        if (hasRatio == hasQuantity) {
            issues.add(new ValidationIssue(path,
                    hasRatio ? "ratio와 quantity는 동시에 쓸 수 없습니다"
                             : "ratio 또는 quantity 중 하나가 필요합니다"));
            return;
        }

        if (hasRatio && (fill.ratio() <= 0 || fill.ratio() > 1)) {
            issues.add(new ValidationIssue(path + ".ratio",
                    "체결 비율은 0 초과 1 이하여야 합니다: " + fill.ratio()));
        }
        if (hasQuantity && fill.quantity() <= 0) {
            issues.add(new ValidationIssue(path + ".quantity",
                    "체결 수량은 0보다 커야 합니다: " + fill.quantity()));
        }
        positiveDuration(fill.after(), path + ".after", issues);
    }

    /**
     * 비율 합계가 1을 넘으면 주문 수량보다 많이 체결된다. 수량 지정 step은 주문 수량을 알아야
     * 판단할 수 있으므로 여기서는 비율만 더한다.
     */
    private void validateRatioSum(List<ScenarioSpec.FillSpec> fills, List<ValidationIssue> issues) {
        double sum = fills.stream()
                .map(ScenarioSpec.FillSpec::ratio)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        // 0.3 + 0.7이 부동소수점에서 1을 살짝 넘으므로 여유를 둔다.
        if (sum > 1.0 + 1e-9) {
            issues.add(new ValidationIssue("execution.fills",
                    "체결 비율의 합은 1을 넘을 수 없습니다: " + sum));
        }
    }

    private void validateFaults(ScenarioSpec.FaultsSpec faults, List<ValidationIssue> issues) {
        if (faults == null || faults.response() == null) {
            return;
        }
        ScenarioSpec.ResponseFaultSpec response = faults.response();
        enumValue(response.on(), Operation.class, "faults.response.on", issues);
        enumValue(response.timing(), FaultTiming.class, "faults.response.timing", issues);
        positiveDuration(response.delay(), "faults.response.delay", issues);
    }

    /** 파서의 오류 메시지에 YAML 경로를 붙여 사용자가 어느 줄을 고칠지 알게 한다. */
    private void positiveDuration(String value, String path, List<ValidationIssue> issues) {
        if (value == null) {
            issues.add(new ValidationIssue(path, "시간 값이 필요합니다"));
            return;
        }
        try {
            Duration parsed = durationParser.parse(value);
            if (parsed.isZero() || parsed.isNegative()) {
                issues.add(new ValidationIssue(path, "시간은 0보다 커야 합니다: " + value));
            }
        } catch (IllegalArgumentException invalid) {
            issues.add(new ValidationIssue(path, invalid.getMessage()));
        }
    }

    private <E extends Enum<E>> void enumValue(
            String value, Class<E> type, String path, List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(new ValidationIssue(path, "값이 필요합니다"));
            return;
        }
        boolean known = Arrays.stream(type.getEnumConstants())
                .anyMatch(constant -> constant.name().equals(value));
        if (!known) {
            issues.add(new ValidationIssue(path, "알 수 없는 값입니다: " + value
                    + ". 허용: " + Arrays.toString(type.getEnumConstants())));
        }
    }
}
