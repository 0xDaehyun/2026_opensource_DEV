package io.github.stockmock.core.order;

import java.util.Objects;

public final class Order {
    private final String id;
    private final String clientOrderId;
    private final Symbol symbol;
    private final Side side;
    private long quantity;
    private long price;
    private long filledQuantity;
    private OrderState state;

    public Order(String id, String clientOrderId, Symbol symbol, Side side, long quantity, long price) {
        if (id == null || id.isBlank() || clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("주문 식별자는 비어 있을 수 없습니다");
        }
        if (quantity <= 0 || price <= 0) {
            throw new IllegalArgumentException("수량과 가격은 0보다 커야 합니다");
        }
        this.id = id;
        this.clientOrderId = clientOrderId;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = quantity;
        this.price = price;
        this.state = OrderState.ACCEPTED;
    }

    public void fill(long fillQuantity) {
        requireActive("체결");
        if (fillQuantity <= 0 || fillQuantity > remainingQuantity()) {
            throw new IllegalArgumentException("체결 수량은 미체결 수량 이하여야 합니다");
        }
        filledQuantity += fillQuantity;
        state = filledQuantity == quantity ? OrderState.FILLED : OrderState.PARTIALLY_FILLED;
    }

    public void cancel() {
        requireActive("취소");
        state = OrderState.CANCELLED;
    }

    public void modify(long newQuantity, long newPrice) {
        requireActive("정정");
        if (newQuantity < filledQuantity || newQuantity <= 0 || newPrice <= 0) {
            throw new IllegalArgumentException("정정 수량은 체결 수량 이상이고 가격은 0보다 커야 합니다");
        }
        quantity = newQuantity;
        price = newPrice;
        if (filledQuantity == quantity) {
            state = OrderState.FILLED;
        }
    }

    public void reject() {
        if (state != OrderState.ACCEPTED) {
            throw new IllegalOrderTransitionException(state, "거부");
        }
        state = OrderState.REJECTED;
    }

    private void requireActive(String action) {
        if (state != OrderState.ACCEPTED && state != OrderState.PARTIALLY_FILLED) {
            throw new IllegalOrderTransitionException(state, action);
        }
    }

    public String id() { return id; }
    public String clientOrderId() { return clientOrderId; }
    public Symbol symbol() { return symbol; }
    public Side side() { return side; }
    public long quantity() { return quantity; }
    public long price() { return price; }
    public long filledQuantity() { return filledQuantity; }
    public long remainingQuantity() { return quantity - filledQuantity; }
    public OrderState state() { return state; }
}
