package io.github.stockmock.scenario.constraint;

/** 증권사와 무관한 요청 종류다. */
public enum Operation {
    ISSUE_TOKEN,
    QUERY_ACCOUNT,
    PLACE_ORDER,
    QUERY_ORDER,
    CANCEL
}
