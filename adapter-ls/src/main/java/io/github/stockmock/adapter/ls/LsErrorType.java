package io.github.stockmock.adapter.ls;

/**
 * LS 봉투로 변환할 중립 오류 분류다. core 오류 계약이 확정되면 그 타입과 매핑한다.
 *
 * <p>{@link #INTERNAL_ERROR}만 예외적으로 core 오류가 아니라 목 서버 자신의 실패를 뜻한다.
 * 목의 장부가 틀렸을 때 봇 개발자가 자기 요청을 의심하지 않도록 클라이언트 오류와 분리한다.</p>
 */
public enum LsErrorType {
    INVALID_REQUEST,
    ORDER_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    ILLEGAL_ORDER_STATE,
    TOKEN_EXPIRED,
    RATE_LIMITED,
    UNSUPPORTED_TR,
    INTERNAL_ERROR
}
