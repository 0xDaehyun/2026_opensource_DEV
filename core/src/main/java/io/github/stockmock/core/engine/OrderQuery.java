package io.github.stockmock.core.engine;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;

/** 특정 주문을 주문번호로 조회하는 명령이다. */
public record OrderQuery(String orderId) {
    public OrderQuery {
        if (orderId == null || orderId.isBlank()) {
            throw new CoreException(CoreErrorCode.INVALID_REQUEST, "orderId가 필요합니다");
        }
    }
}
