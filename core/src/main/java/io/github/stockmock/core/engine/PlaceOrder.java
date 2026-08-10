package io.github.stockmock.core.engine;

import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;

public record PlaceOrder(String clientOrderId, Symbol symbol, Side side, long qty, long price) {
}
