package io.github.stockmock.core.engine;

public record CancelOrder(String clientOrderId, String targetOrderId, long qty) {
}
