package io.github.stockmock.scenario.execution;

import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillStep;
import io.github.stockmock.scenario.spec.ScenarioSpec;
import io.github.stockmock.scenario.time.DurationParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 검증된 execution 설정을 core {@link FillPlan}으로 변환한다.
 *
 * <h2>선행 조건</h2>
 * <p>TODO(CORE-02): core의 {@code FillPlanProvider}가 병합되면 이 클래스가 해당 인터페이스를
 * 구현하도록 변경한다. 변환 규칙과 시그니처는 그대로 두면 된다.</p>
 *
 * <h2>변환 규칙</h2>
 * <ul>
 *   <li>ratio는 최초 주문 수량 기준이다. 100주 × 0.3은 30주다.</li>
 *   <li>quantity가 있으면 해당 수량을 그대로 사용한다.</li>
 *   <li>ratio와 quantity 중 정확히 하나만 존재해야 한다.</li>
 *   <li>after는 모두 주문 접수 시각 기준이며 누적하지 않는다.</li>
 *   <li>계산된 전체 수량은 orderQuantity를 넘을 수 없다.</li>
 *   <li>계획 생성 이후에는 난수를 사용하거나 수량을 다시 계산하지 않는다.</li>
 * </ul>
 *
 * <p>이 클래스는 난수를 쓰지 않으므로 같은 입력에서 항상 같은 계획이 나온다. 시나리오의
 * {@code seed}는 여기서 사용하지 않는다.</p>
 */
public final class ScenarioFillPlanProvider {
    private final DurationParser durationParser;

    public ScenarioFillPlanProvider() {
        this(new DurationParser());
    }

    public ScenarioFillPlanProvider(DurationParser durationParser) {
        this.durationParser = Objects.requireNonNull(durationParser, "durationParser");
    }

    public FillPlan create(long orderQuantity, ScenarioSpec.ExecutionSpec execution) {
        if (orderQuantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 0보다 커야 합니다: " + orderQuantity);
        }
        if (execution == null || execution.fills() == null || execution.fills().isEmpty()) {
            throw new IllegalArgumentException("체결 계획에는 fills가 하나 이상 필요합니다");
        }

        List<FillStep> steps = new ArrayList<>();
        long remaining = orderQuantity;
        for (ScenarioSpec.FillSpec fill : execution.fills()) {
            Duration delay = durationParser.parse(fill.after());
            long quantity = quantityOf(fill, orderQuantity);
            if (quantity > remaining) {
                throw new IllegalArgumentException(
                        "체결 계획의 총수량이 주문 수량을 넘습니다: 주문 " + orderQuantity + "주");
            }
            steps.add(new FillStep(delay, quantity));
            remaining -= quantity;
        }
        return new FillPlan(steps);
    }

    private long quantityOf(ScenarioSpec.FillSpec fill, long orderQuantity) {
        boolean hasRatio = fill.ratio() != null;
        boolean hasQuantity = fill.quantity() != null;
        if (hasRatio == hasQuantity) {
            throw new IllegalArgumentException("ratio와 quantity 중 정확히 하나가 필요합니다");
        }
        return hasRatio ? fromRatio(fill.ratio(), orderQuantity) : fill.quantity();
    }

    /**
     * core의 {@code FillPlan.partial}과 같은 규칙이다. 내림하되 최소 1주를 보장한다.
     * {@link FillStep}이 0 수량을 거부하므로 0으로 내려가면 계획 자체를 만들 수 없다.
     */
    private long fromRatio(double ratio, long orderQuantity) {
        if (ratio <= 0 || ratio > 1) {
            throw new IllegalArgumentException("체결 비율은 0 초과 1 이하여야 합니다: " + ratio);
        }
        return Math.max(1, Math.min(orderQuantity, (long) Math.floor(orderQuantity * ratio)));
    }
}
