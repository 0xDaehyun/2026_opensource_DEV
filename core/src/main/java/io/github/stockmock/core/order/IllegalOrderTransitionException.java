package io.github.stockmock.core.order;

public final class IllegalOrderTransitionException extends IllegalStateException {
    public IllegalOrderTransitionException(OrderState state, String action) {
        super(state + " 상태에서는 " + action + "할 수 없습니다");
    }
}
