package io.github.stockmock.scenario.validation;

import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.AccountSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ConstraintsSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ExecutionSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FaultsSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FillSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.RateLimitSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ResponseFaultSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TODO(SCENARIO-02): 규칙마다 실패 테스트를 하나씩 둔다. */
class ScenarioValidatorTest {
    private final ScenarioValidator validator = new ScenarioValidator();

    @Test
    void acceptsAValidScenario() {
        assertThat(validator.validate(scenario(
                new ExecutionSpec(List.of(new FillSpec("1s", 0.3, null))),
                new ConstraintsSpec(new RateLimitSpec(2), "10s"),
                new FaultsSpec(new ResponseFaultSpec("PLACE_ORDER", "AFTER_COMMIT", "3s")))))
                .isEmpty();
    }

    @Test
    void reportsMissingNameAndNegativeCash() {
        ScenarioSpec spec = new ScenarioSpec(" ", new AccountSpec(-1L), null, validExecution(), null, 42L);

        assertThat(validator.validate(spec))
                .extracting(ValidationIssue::field)
                .containsExactly("scenario", "account.cash");
    }

    // ------------------------------------------------------------- 규칙 1

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, 2.0})
    void rejectsARatioOutsideZeroToOne(double ratio) {
        assertThat(fillIssues(new FillSpec("1s", ratio, null)))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("execution.fills[0].ratio"));
    }

    @Test
    void acceptsARatioOfExactlyOne() {
        assertThat(fillIssues(new FillSpec("1s", 1.0, null))).isEmpty();
    }

    // ------------------------------------------------------------- 규칙 2

    @Test
    void rejectsRatioAndQuantityTogether() {
        assertThat(fillIssues(new FillSpec("1s", 0.3, 30L)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.field()).isEqualTo("execution.fills[0]");
                    assertThat(issue.message()).contains("ratio", "quantity");
                });
    }

    @Test
    void rejectsNeitherRatioNorQuantity() {
        assertThat(fillIssues(new FillSpec("1s", null, null)))
                .singleElement()
                .satisfies(issue -> assertThat(issue.field()).isEqualTo("execution.fills[0]"));
    }

    @Test
    void rejectsANonPositiveQuantity() {
        assertThat(fillIssues(new FillSpec("1s", null, 0L)))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("execution.fills[0].quantity"));
    }

    // ------------------------------------------------------------- 규칙 3

    @Test
    void rejectsRatiosSummingAboveOne() {
        ExecutionSpec execution = new ExecutionSpec(List.of(
                new FillSpec("1s", 0.6, null),
                new FillSpec("5s", 0.6, null)));

        assertThat(validator.validate(scenario(execution, null, null)))
                .singleElement()
                .satisfies(issue -> assertThat(issue.field()).isEqualTo("execution.fills"));
    }

    @Test
    void acceptsRatiosSummingToExactlyOne() {
        ExecutionSpec execution = new ExecutionSpec(List.of(
                new FillSpec("1s", 0.3, null),
                new FillSpec("5s", 0.7, null)));

        assertThat(validator.validate(scenario(execution, null, null))).isEmpty();
    }

    // ------------------------------------------------------------- 규칙 4

    @ParameterizedTest
    @ValueSource(strings = {"-1s", "0s", "3", "1.5s", "3초", ""})
    void rejectsAnInvalidFillDelay(String after) {
        assertThat(fillIssues(new FillSpec(after, 0.3, null)))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("execution.fills[0].after"));
    }

    @Test
    void rejectsAnInvalidResponseDelay() {
        FaultsSpec faults = new FaultsSpec(new ResponseFaultSpec("PLACE_ORDER", "AFTER_COMMIT", "-3s"));

        assertThat(validator.validate(scenario(null, null, faults)))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("faults.response.delay"));
    }

    /** SCENARIO-03에서 넘어온 항목이다. 파서는 값만 알리고 검증기가 YAML 경로를 붙인다. */
    @Test
    void attachesTheYamlFieldPathToAParsingFailure() {
        assertThat(fillIssues(new FillSpec("3초", 0.3, null)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.field()).isEqualTo("execution.fills[0].after");
                    assertThat(issue.message()).contains("3초");
                });
    }

    // ------------------------------------------------------------- 규칙 5

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsARateLimitBelowOne(int perSec) {
        ConstraintsSpec constraints = new ConstraintsSpec(new RateLimitSpec(perSec), null);

        assertThat(validator.validate(scenario(null, constraints, null)))
                .singleElement()
                .satisfies(issue -> assertThat(issue.field()).isEqualTo("constraints.rate_limit.per_sec"));
    }

    // ------------------------------------------------------------- 규칙 6

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-24h", "24", "하루"})
    void rejectsANonPositiveOrUnparsableTokenTtl(String tokenTtl) {
        ConstraintsSpec constraints = new ConstraintsSpec(null, tokenTtl);

        assertThat(validator.validate(scenario(null, constraints, null)))
                .singleElement()
                .satisfies(issue -> assertThat(issue.field()).isEqualTo("constraints.token_ttl"));
    }

    // ------------------------------------------ faults 열거값 (6개 규칙 외 추가)

    @Test
    void rejectsAnUnknownOperationOrTiming() {
        FaultsSpec faults = new FaultsSpec(new ResponseFaultSpec("PLACE_ORDR", "AFTER_COMMITT", "3s"));

        assertThat(validator.validate(scenario(null, null, faults)))
                .extracting(ValidationIssue::field)
                .containsExactly("faults.response.on", "faults.response.timing");
    }

    // ----------------------------------------------------------------- 공통

    /**
     * 한 fill 안에서도 조기 반환하지 않는다. hasSizeGreaterThanOrEqualTo로 두면 ratio/quantity
     * 문제가 같은 fill의 after 오류를 가려도 통과하므로 containsExactly로 고정한다.
     */
    @Test
    void collectsEveryIssueInsteadOfFailingOnTheFirst() {
        ScenarioSpec spec = new ScenarioSpec("bad", new AccountSpec(10L),
                new ConstraintsSpec(new RateLimitSpec(0), "0s"),
                new ExecutionSpec(List.of(new FillSpec("-1s", 2.0, 5L))), null, 42L);

        assertThat(validator.validate(spec))
                .extracting(ValidationIssue::field)
                .containsExactly(
                        "constraints.rate_limit.per_sec",
                        "constraints.token_ttl",
                        "execution.fills[0]",
                        "execution.fills[0].ratio",
                        "execution.fills[0].after",
                        "execution.fills");
    }

    /** YAML의 빈 항목은 Jackson이 null 원소로 만든다. NPE가 아니라 검증 오류여야 한다. */
    @Test
    void reportsAnEmptyFillEntryInsteadOfThrowing() {
        ExecutionSpec execution = new ExecutionSpec(java.util.Arrays.asList(new FillSpec[]{null}));

        assertThat(validator.validate(scenario(execution, null, null)))
                .extracting(ValidationIssue::field)
                .containsExactly("execution.fills[0]");
    }

    /** 검증을 통과했는데 provider가 모든 주문을 거부하는 상황을 막는다. */
    @Test
    void rejectsAMissingOrEmptyExecutionBlock() {
        assertThat(validator.validate(rawScenario(null)))
                .extracting(ValidationIssue::field)
                .containsExactly("execution.fills");
        assertThat(validator.validate(rawScenario(new ExecutionSpec(List.of()))))
                .extracting(ValidationIssue::field)
                .containsExactly("execution.fills");
    }

    @Test
    void returnsAnUnmodifiableIssueList() {
        List<ValidationIssue> issues =
                validator.validate(new ScenarioSpec(" ", null, null, null, null, null));

        assertThat(issues).isNotEmpty();
        assertThatThrownBy(() -> issues.add(new ValidationIssue("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private List<ValidationIssue> fillIssues(FillSpec fill) {
        return validator.validate(scenario(new ExecutionSpec(List.of(fill)), null, null));
    }

    /** execution을 지정하지 않으면 유효한 기본값을 넣어 검사 대상 규칙만 남긴다. */
    private ScenarioSpec scenario(ExecutionSpec execution, ConstraintsSpec constraints, FaultsSpec faults) {
        return new ScenarioSpec("valid_scenario", new AccountSpec(10_000_000L),
                constraints, execution == null ? validExecution() : execution, faults, 42L);
    }

    /** execution 자체를 검사하는 테스트용이다. 기본값으로 바꿔치지 않는다. */
    private ScenarioSpec rawScenario(ExecutionSpec execution) {
        return new ScenarioSpec("valid_scenario", new AccountSpec(10_000_000L),
                null, execution, null, 42L);
    }

    private ExecutionSpec validExecution() {
        return new ExecutionSpec(List.of(new FillSpec("1s", 0.3, null)));
    }
}
