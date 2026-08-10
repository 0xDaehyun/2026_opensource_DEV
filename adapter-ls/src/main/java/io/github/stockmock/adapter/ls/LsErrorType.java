package io.github.stockmock.adapter.ls;

/** LS 봉투로 변환할 중립 오류 분류다. core 오류 계약이 확정되면 그 타입과 매핑한다. */
public enum LsErrorType {
    INVALID_REQUEST,
    ORDER_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    ILLEGAL_ORDER_STATE,
    TOKEN_EXPIRED,
    RATE_LIMITED,
    UNSUPPORTED_TR
}
