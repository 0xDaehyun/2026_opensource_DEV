package io.github.stockmock.core.error;

/** 증권사 형식과 무관하게 core가 외부 모듈에 제공하는 오류 분류다. */
public enum CoreErrorCode {
    INVALID_REQUEST,
    ORDER_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    ILLEGAL_ORDER_STATE
}
