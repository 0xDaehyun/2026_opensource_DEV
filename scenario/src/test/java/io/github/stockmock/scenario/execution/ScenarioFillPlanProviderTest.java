package io.github.stockmock.scenario.execution;

import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillStep;
import io.github.stockmock.scenario.spec.ScenarioSpec.ExecutionSpec;
import io.github.stockmock.scenario.spec.ScenarioSpec.FillSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
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
 */
class ScenarioFillPlanProviderTest {
    private final ScenarioFillPlanProvider provider = new ScenarioFillPlanProvider();

    @Test
    void turnsARatioIntoAnActualQuantity() {
        FillPlan plan = provider.create(100, execution(new FillSpec("1s", 0.3, null)));

        assertThat(plan.steps()).singleElement().satisfies(step -> {
            assertThat(step.quantity()).isEqualTo(30);
            assertThat(step.delay()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void usesAnExplicitQuantityAsIs() {
        FillPlan plan = provider.create(100, execution(new FillSpec("2s", null, 45L)));

        assertThat(plan.steps()).singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(45));
    }

    /**
     * after는 누적이 아니라 모두 주문 접수 시각 기준이다. 1s와 5s는 1초 뒤와 5초 뒤이지
     * 1초 뒤와 6초 뒤가 아니다.
     */
    @Test
    void readsEveryDelayFromTheAcceptanceTime() {
        FillPlan plan = provider.create(100, execution(
                new FillSpec("1s", 0.3, null),
                new FillSpec("5s", 0.7, null)));

        assertThat(plan.steps()).extracting(FillStep::delay)
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(5));
        assertThat(plan.steps()).extracting(FillStep::quantity)
                .containsExactly(30L, 70L);
    }

    @Test
    void producesTheSamePlanForTheSameInput() {
        ExecutionSpec execution = execution(
                new FillSpec("1s", 0.3, null),
                new FillSpec("5s", 0.7, null));

        assertThat(provider.create(100, execution)).isEqualTo(provider.create(100, execution));
    }

    /** core의 {@code FillPlan.partial}과 같은 내림 규칙을 쓴다. */
    @Test
    void roundsDownLikeTheCoreHelper() {
        assertThat(provider.create(100, execution(new FillSpec("1s", 0.333, null))).steps())
                .singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(33));

        assertThat(FillPlan.partial(100, 0.333, Duration.ofSeconds(1)).steps().getFirst().quantity())
                .isEqualTo(33);
    }

    /** 내림 결과가 0이 되어도 최소 1주를 보장한다. FillStep이 0 수량을 거부하기 때문이다. */
    @Test
    void neverProducesAZeroQuantityStep() {
        FillPlan plan = provider.create(100, execution(new FillSpec("1s", 0.001, null)));

        assertThat(plan.steps()).singleElement()
                .satisfies(step -> assertThat(step.quantity()).isEqualTo(1));
    }

    @Test
    void rejectsAPlanExceedingTheOrderQuantity() {
        ExecutionSpec execution = execution(
                new FillSpec("1s", null, 60L),
                new FillSpec("5s", null, 60L));

        assertThatThrownBy(() -> provider.create(100, execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void allowsAPlanFillingExactlyTheOrderQuantity() {
        FillPlan plan = provider.create(100, execution(
                new FillSpec("1s", null, 40L),
                new FillSpec("5s", null, 60L)));

        assertThat(plan.steps()).extracting(FillStep::quantity).containsExactly(40L, 60L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsANonPositiveOrderQuantity(long orderQuantity) {
        assertThatThrownBy(() -> provider.create(orderQuantity, execution(new FillSpec("1s", 0.3, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingExecutionBlock() {
        assertThatThrownBy(() -> provider.create(100, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.create(100, new ExecutionSpec(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAFillUsingBothRatioAndQuantity() {
        assertThatThrownBy(() -> provider.create(100, execution(new FillSpec("1s", 0.3, 30L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnparsableDelay() {
        assertThatThrownBy(() -> provider.create(100, execution(new FillSpec("3초", 0.3, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3초");
    }

    private ExecutionSpec execution(FillSpec... fills) {
        return new ExecutionSpec(List.of(fills));
    }
}
