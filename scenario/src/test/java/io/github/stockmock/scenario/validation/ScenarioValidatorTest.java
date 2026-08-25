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
        ScenarioSpec spec = new ScenarioSpec(" ", new AccountSpec(-1L), null, null, null, 42L);

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

    @Test
    void collectsEveryIssueInsteadOfFailingOnTheFirst() {
        ScenarioSpec spec = new ScenarioSpec("bad", new AccountSpec(10L),
                new ConstraintsSpec(new RateLimitSpec(0), "0s"),
                new ExecutionSpec(List.of(new FillSpec("-1s", 2.0, 5L))), null, 42L);

        assertThat(validator.validate(spec)).hasSizeGreaterThanOrEqualTo(4);
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

    private ScenarioSpec scenario(ExecutionSpec execution, ConstraintsSpec constraints, FaultsSpec faults) {
        return new ScenarioSpec("valid_scenario", new AccountSpec(10_000_000L),
                constraints, execution, faults, 42L);
    }
}
