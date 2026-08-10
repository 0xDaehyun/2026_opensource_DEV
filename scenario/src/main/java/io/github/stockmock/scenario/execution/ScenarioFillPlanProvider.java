package io.github.stockmock.scenario.execution;

import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.scenario.spec.ScenarioSpec;

/**
 * TODO(SCENARIO-04): 검증된 execution 설정을 core {@link FillPlan}으로 변환한다.
 *
 * <h2>선행 조건</h2>
 * <p>CORE-02에서 core의 {@code FillPlanProvider}가 병합되면 이 클래스가 해당 인터페이스를 구현하도록 변경한다.</p>
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code orderQuantity}: 최초 주문 수량, 1 이상</li>
 *   <li>{@code execution}: 검증이 끝난 체결 step 목록</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <p>각 step의 after와 실제 체결 수량을 가진 불변 {@link FillPlan}.</p>
 *
 * <h2>변환 규칙</h2>
 * <ul>
 *   <li>ratio는 최초 주문 수량 기준이다. 100주 × 0.3은 30주다.</li>
 *   <li>quantity가 있으면 해당 수량을 그대로 사용한다.</li>
 *   <li>ratio와 quantity 중 정확히 하나만 존재해야 한다.</li>
 *   <li>after는 모두 주문 접수 시각 기준이다.</li>
 *   <li>계산된 전체 수량은 orderQuantity를 넘을 수 없다.</li>
 *   <li>계획 생성 이후에는 난수를 사용하거나 수량을 다시 계산하지 않는다.</li>
 * </ul>
 */
public final class ScenarioFillPlanProvider {
    public FillPlan create(long orderQuantity, ScenarioSpec.ExecutionSpec execution) {
        throw new UnsupportedOperationException("TODO(SCENARIO-04): FillPlan 변환");
    }
}
