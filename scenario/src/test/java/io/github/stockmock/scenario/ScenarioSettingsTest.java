package io.github.stockmock.scenario;

import io.github.stockmock.scenario.constraint.Operation;
import io.github.stockmock.scenario.fault.FaultTiming;
import io.github.stockmock.scenario.fault.ResponseDelayRule;
import io.github.stockmock.scenario.loader.ScenarioLoader;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.AccountSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ConstraintsSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ExecutionSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FaultsSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FillSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.RateLimitSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.ResponseFaultSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * YAML 문자열을 정책 입력 타입으로 바꾸는 변환기 테스트다. 이 변환이 없으면 조립 계층이
 * {@code Operation.valueOf}와 {@code DurationParser}를 직접 불러야 하고, 그것은
 * docs/contracts.md의 "app은 업무 규칙을 갖지 않는다"에 어긋난다.
 */
class ScenarioSettingsTest {

    @Test
    void convertsEveryStringFieldIntoAPolicyInput() {
        ScenarioSettings settings = ScenarioSettings.from(spec(
                new ConstraintsSpec(new RateLimitSpec(2), "10s"),
                new FaultsSpec(new ResponseFaultSpec("PLACE_ORDER", "AFTER_COMMIT", "3s"))));

        assertThat(settings.initialCash()).isEqualTo(10_000_000L);
        assertThat(settings.tokenTtl()).contains(Duration.ofSeconds(10));
        assertThat(settings.ratePerSecond()).hasValue(2);
        assertThat(settings.responseDelay()).contains(new ResponseDelayRule(
                Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, Duration.ofSeconds(3)));
    }

    @Test
    void buildsAUsableFillPlanProvider() {
        ScenarioSettings settings = ScenarioSettings.from(spec(null, null));

        assertThat(settings.fillPlanProvider().create(100).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(30));
    }

    @Test
    void leavesOptionalSettingsEmptyWhenTheScenarioOmitsThem() {
        ScenarioSettings settings = ScenarioSettings.from(spec(null, null));

        assertThat(settings.tokenTtl()).isEmpty();
        assertThat(settings.ratePerSecond()).isEmpty();
        assertThat(settings.responseDelay()).isEmpty();
    }

    @Test
    void handlesAConstraintsBlockWithOnlyOneField() {
        ScenarioSettings onlyTtl = ScenarioSettings.from(spec(new ConstraintsSpec(null, "24h"), null));
        assertThat(onlyTtl.tokenTtl()).contains(Duration.ofHours(24));
        assertThat(onlyTtl.ratePerSecond()).isEmpty();

        ScenarioSettings onlyRate =
                ScenarioSettings.from(spec(new ConstraintsSpec(new RateLimitSpec(5), null), null));
        assertThat(onlyRate.ratePerSecond()).hasValue(5);
        assertThat(onlyRate.tokenTtl()).isEmpty();
    }

    @Test
    void rejectsAnUnknownOperationOrTiming() {
        assertThatThrownBy(() -> ScenarioSettings.from(spec(null,
                new FaultsSpec(new ResponseFaultSpec("PLACE_ORDR", "AFTER_COMMIT", "3s")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faults.response.on");

        assertThatThrownBy(() -> ScenarioSettings.from(spec(null,
                new FaultsSpec(new ResponseFaultSpec("PLACE_ORDER", "AFTER_COMMITT", "3s")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faults.response.timing");
    }

    @Test
    void rejectsAnUnparsableDuration() {
        assertThatThrownBy(() -> ScenarioSettings.from(spec(new ConstraintsSpec(null, "하루"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingSpecOrAccount() {
        assertThatThrownBy(() -> ScenarioSettings.from(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScenarioSettings.from(
                new ScenarioSpec("x", null, null, new ExecutionSpec(List.of(new FillSpec("1s", 0.3, null))),
                        null, 42L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 카탈로그 시나리오가 실제로 조립 가능한지 확인한다. APP-01이 그대로 쓰는 경로다. */
    @Test
    void assemblesTheResponseDelayCatalogScenario() {
        ScenarioSpec spec = new ScenarioLoader()
                .load(Path.of("..", "scenarios", "hazards", "response-delay-after-commit.yml"));

        ScenarioSettings settings = ScenarioSettings.from(spec);

        assertThat(settings.responseDelay()).contains(new ResponseDelayRule(
                Operation.PLACE_ORDER, FaultTiming.AFTER_COMMIT, Duration.ofSeconds(3)));
        assertThat(settings.fillPlanProvider().create(100).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(30));
    }

    private ScenarioSpec spec(ConstraintsSpec constraints, FaultsSpec faults) {
        return new ScenarioSpec("valid_scenario", new AccountSpec(10_000_000L), constraints,
                new ExecutionSpec(List.of(new FillSpec("1s", 0.3, null))), faults, 42L);
    }
}
