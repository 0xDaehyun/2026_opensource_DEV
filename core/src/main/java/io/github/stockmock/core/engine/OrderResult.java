package io.github.stockmock.core.engine;

import io.github.stockmock.core.order.OrderState;

public record OrderResult(String orderId, String clientOrderId, OrderState state,
                          long quantity, long filledQuantity, String reason) {
    public boolean accepted() {
        return state != OrderState.REJECTED;
    }
}
