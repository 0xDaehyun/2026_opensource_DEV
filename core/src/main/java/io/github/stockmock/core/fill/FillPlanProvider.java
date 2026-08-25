package io.github.stockmock.core.fill;

/** 주문 접수 시점에 해당 주문의 전체 체결 계획을 생성하는 확장 포트다. */
@FunctionalInterface
public interface FillPlanProvider {
    /**
     * 최초 주문 수량을 기준으로 불변 체결 계획을 생성한다.
     * 이 메서드는 한 주문당 정확히 한 번 호출된다.
     *
     * @param orderQuantity 최초 주문 수량, 1 이상
     * @return 전체 체결 수량이 주문 수량을 넘지 않는 계획
     */
    FillPlan create(long orderQuantity);
}
