package io.github.stockmock.adapter.ls;

/** LS InBlock을 조립 계층의 증권사 중립 정책으로 전달할 때 사용하는 요청 종류다. */
public enum LsRequestOperation {
    QUERY_ACCOUNT,
    PLACE_ORDER,
    QUERY_ORDER,
    CANCEL,
    UNKNOWN
}
