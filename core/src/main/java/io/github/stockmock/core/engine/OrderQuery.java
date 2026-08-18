package io.github.stockmock.core.engine;

/** 특정 주문을 주문번호로 조회하는 명령이다. */
public record OrderQuery(String orderId) {
    public OrderQuery {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId가 필요합니다");
        }
    }
}
