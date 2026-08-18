package io.github.stockmock.core.engine;

import io.github.stockmock.core.order.OrderState;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;

/** 외부 모듈에 노출하는 읽기 전용 주문 스냅샷이다. */
public record OrderView(
        String orderId,
        String clientOrderId,
        Symbol symbol,
        Side side,
        OrderState state,
        long quantity,
        long filledQuantity,
        long remainingQuantity,
        long price
) {
}
