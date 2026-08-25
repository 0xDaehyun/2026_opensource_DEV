package io.github.stockmock.scenario.execution;

import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillStep;
import io.github.stockmock.scenario.spec.ScenarioSpec.ExecutionSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FillSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(SCENARIO-04) 테스트 목록:
 * <ul>
 *   <li>100주와 0.3에서 30주가 생성된다.</li>
 *   <li>같은 시나리오와 seed에서 같은 계획이 생성된다.</li>
 *   <li>모든 after는 주문 접수 시각 기준이다.</li>
 * </ul>
 *
 * <p>core의 {@code FillPlanProvider}는 {@code create(long)} 하나만 요구하므로 설정은
 * 생성자로 받는다. 이 테스트는 그 형태를 고정한다.</p>
 */
class ScenarioFillPlanProviderTest {

    @Test
    void turnsARatioIntoAnActualQuantity() {
        FillPlan plan = provider(new FillSpec("1s", 0.3, null)).create(100);

        assertThat(plan.steps()).singleElement().satisfies(step -> {
            assertThat(step.quantity()).isEqualTo(30);
            assertThat(step.delay()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void usesAnExplicitQuantityAsIs() {
        assertThat(provider(new FillSpec("2s", null, 45L)).create(100).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(45));
    }

    /**
     * after는 누적이 아니라 모두 주문 접수 시각 기준이다. 1s와 5s는 1초 뒤와 5초 뒤이지
     * 1초 뒤와 6초 뒤가 아니다.
     */
    @Test
    void readsEveryDelayFromTheAcceptanceTime() {
        FillPlan plan = provider(
                new FillSpec("1s", 0.3, null),
                new FillSpec("5s", 0.7, null)).create(100);

        assertThat(plan.steps()).extracting(FillStep::delay)
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(5));
        assertThat(plan.steps()).extracting(FillStep::quantity).containsExactly(30L, 70L);
    }

    /** 한 provider로 여러 주문을 처리해도 계획이 서로 영향을 주지 않는다. */
    @Test
    void producesAnIndependentPlanPerOrder() {
        ScenarioFillPlanProvider provider = provider(new FillSpec("1s", 0.3, null));

        assertThat(provider.create(100).steps().getFirst().quantity()).isEqualTo(30);
        assertThat(provider.create(200).steps().getFirst().quantity()).isEqualTo(60);
        assertThat(provider.create(100)).isEqualTo(provider.create(100));
    }

    /** core의 {@code FillPlan.partial}과 같은 내림 규칙을 쓴다. */
    @Test
    void roundsDownLikeTheCoreHelper() {
        assertThat(provider(new FillSpec("1s", 0.333, null)).create(100).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(33));

        assertThat(FillPlan.partial(100, 0.333, Duration.ofSeconds(1)).steps().getFirst().quantity())
                .isEqualTo(33);
    }

    /** 내림 결과가 0이 되어도 최소 1주를 보장한다. FillStep이 0 수량을 거부하기 때문이다. */
    @Test
    void neverProducesAZeroQuantityStep() {
        assertThat(provider(new FillSpec("1s", 0.001, null)).create(100).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(1));
    }

    /**
     * 최소 1주 보장 때문에 step 수보다 적은 주문은 계획을 세울 수 없다. 검증기는 주문 수량을
     * 모르므로 이 조건은 여기서만 잡힌다. 메시지에 필요한 수량을 밝혀 봇이 원인을 알 수 있게 한다.
     */
    @Test
    void rejectsAnOrderSmallerThanTheStepCount() {
        ScenarioFillPlanProvider provider = provider(
                new FillSpec("1s", 0.5, null),
                new FillSpec("2s", 0.5, null));

        assertThatThrownBy(() -> provider.create(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step 2개")
                .hasMessageContaining("1주");
    }

    @Test
    void splitsTheSmallestOrderThatTheScenarioAllows() {
        FillPlan plan = provider(
                new FillSpec("1s", 0.5, null),
                new FillSpec("2s", 0.5, null)).create(2);

        assertThat(plan.steps()).extracting(FillStep::quantity).containsExactly(1L, 1L);
    }

    @Test
    void rejectsAPlanExceedingTheOrderQuantity() {
        ScenarioFillPlanProvider provider = provider(
                new FillSpec("1s", null, 60L),
                new FillSpec("5s", null, 60L));

        assertThatThrownBy(() -> provider.create(100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void allowsAPlanFillingExactlyTheOrderQuantity() {
        FillPlan plan = provider(
                new FillSpec("1s", null, 40L),
                new FillSpec("5s", null, 60L)).create(100);

        assertThat(plan.steps()).extracting(FillStep::quantity).containsExactly(40L, 60L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsANonPositiveOrderQuantity(long orderQuantity) {
        assertThatThrownBy(() -> provider(new FillSpec("1s", 0.3, null)).create(orderQuantity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------- 생성 시점 검증

    @Test
    void rejectsAMissingExecutionBlockAtConstruction() {
        assertThatThrownBy(() -> new ScenarioFillPlanProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScenarioFillPlanProvider(new ExecutionSpec(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullFillEntryAtConstruction() {
        ExecutionSpec execution = new ExecutionSpec(Arrays.asList(new FillSpec[]{null}));

        assertThatThrownBy(() -> new ScenarioFillPlanProvider(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fills[0]");
    }

    @Test
    void rejectsAFillUsingBothRatioAndQuantityAtConstruction() {
        assertThatThrownBy(() -> provider(new FillSpec("1s", 0.3, 30L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** after 파싱은 주문마다가 아니라 생성 시 한 번만 한다. */
    @Test
    void rejectsAnUnparsableDelayAtConstruction() {
        assertThatThrownBy(() -> provider(new FillSpec("3초", 0.3, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3초");
    }

    private ScenarioFillPlanProvider provider(FillSpec... fills) {
        return new ScenarioFillPlanProvider(new ExecutionSpec(List.of(fills)));
    }
}
