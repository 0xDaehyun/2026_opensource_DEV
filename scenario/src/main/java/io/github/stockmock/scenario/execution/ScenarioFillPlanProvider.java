package io.github.stockmock.scenario.execution;

import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillPlanProvider;
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
 * <h2>core 계약</h2>
 * <p>core의 {@link FillPlanProvider}는 {@code FillPlan create(long orderQuantity)} 하나만
 * 요구한다. core는 시나리오 타입을 몰라야 하므로(의존 방향 {@code core ← scenario})
 * {@code ExecutionSpec}을 인자로 받을 수 없다. 그래서 설정은 생성자로 받고 {@link #create(long)}
 * 만 남긴다.</p>
 *
 * <h2>변환 규칙</h2>
 * <ul>
 *   <li>ratio는 최초 주문 수량 기준이다. 100주 × 0.3은 30주다.</li>
 *   <li>quantity가 있으면 해당 수량을 그대로 사용한다.</li>
 *   <li>ratio와 quantity 중 정확히 하나만 존재해야 한다.</li>
 *   <li>after는 모두 주문 접수 시각 기준이며 누적하지 않는다.</li>
 *   <li>계산된 전체 수량은 orderQuantity를 넘을 수 없다.</li>
 * </ul>
 *
 * <p>{@code after} 문자열은 생성 시 한 번만 파싱한다. {@link #create(long)}은 DES 엔진
 * 스레드에서 주문마다 호출되므로 거기서 정규식을 돌리지 않는다.</p>
 *
 * <p>난수를 쓰지 않으므로 같은 설정과 주문 수량에서 항상 같은 계획이 나온다.
 * 시나리오의 {@code seed}는 사용하지 않는다.</p>
 */
public final class ScenarioFillPlanProvider implements FillPlanProvider {
    private final List<ParsedFill> fills;

    public ScenarioFillPlanProvider(ScenarioSpec.ExecutionSpec execution) {
        this(execution, new DurationParser());
    }

    public ScenarioFillPlanProvider(ScenarioSpec.ExecutionSpec execution, DurationParser durationParser) {
        Objects.requireNonNull(durationParser, "durationParser");
        if (execution == null || execution.fills() == null || execution.fills().isEmpty()) {
            throw new IllegalArgumentException("체결 계획에는 fills가 하나 이상 필요합니다");
        }
        this.fills = parse(execution.fills(), durationParser);
    }

    @Override
    public FillPlan create(long orderQuantity) {
        if (orderQuantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 0보다 커야 합니다: " + orderQuantity);
        }
        if (orderQuantity < fills.size()) {
            throw new IllegalArgumentException(
                    "분할체결 시나리오는 step 수 이상의 주문 수량이 필요합니다: step " + fills.size()
                            + "개, 주문 " + orderQuantity + "주");
        }

        List<FillStep> steps = new ArrayList<>(fills.size());
        long remaining = orderQuantity;
        for (ParsedFill fill : fills) {
            long quantity = fill.quantityFor(orderQuantity);
            if (quantity > remaining) {
                throw new IllegalArgumentException(
                        "체결 계획의 총수량이 주문 수량을 넘습니다: 주문 " + orderQuantity + "주");
            }
            steps.add(new FillStep(fill.delay(), quantity));
            remaining -= quantity;
        }
        return new FillPlan(steps);
    }

    private static List<ParsedFill> parse(List<ScenarioSpec.FillSpec> specs, DurationParser parser) {
        List<ParsedFill> parsed = new ArrayList<>(specs.size());
        for (int index = 0; index < specs.size(); index++) {
            ScenarioSpec.FillSpec spec = specs.get(index);
            if (spec == null) {
                throw new IllegalArgumentException("체결 step이 비어 있습니다: fills[" + index + "]");
            }
            boolean hasRatio = spec.ratio() != null;
            boolean hasQuantity = spec.quantity() != null;
            if (hasRatio == hasQuantity) {
                throw new IllegalArgumentException(
                        "ratio와 quantity 중 정확히 하나가 필요합니다: fills[" + index + "]");
            }
            if (hasRatio && (!Double.isFinite(spec.ratio())
                    || spec.ratio() <= 0
                    || spec.ratio() > 1)) {
                throw new IllegalArgumentException(
                        "체결 비율은 유한한 숫자이며 0 초과 1 이하여야 합니다: " + spec.ratio());
            }
            if (hasQuantity && spec.quantity() <= 0) {
                throw new IllegalArgumentException("체결 수량은 0보다 커야 합니다: " + spec.quantity());
            }
            parsed.add(new ParsedFill(parser.parse(spec.after()), spec.ratio(), spec.quantity()));
        }
        return List.copyOf(parsed);
    }

    private record ParsedFill(Duration delay, Double ratio, Long quantity) {
        /**
         * core의 {@code FillPlan.partial}과 같은 규칙이다. 내림하되 최소 1주를 보장한다.
         * {@link FillStep}이 0 수량을 거부하므로 0으로 내려가면 step 자체를 만들 수 없다.
         */
        long quantityFor(long orderQuantity) {
            if (quantity != null) {
                return quantity;
            }
            return Math.max(1, Math.min(orderQuantity, (long) Math.floor(orderQuantity * ratio)));
        }
    }
}
