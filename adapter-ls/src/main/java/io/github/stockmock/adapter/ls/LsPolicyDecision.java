package io.github.stockmock.adapter.ls;

/** 요청 정책이 adapter에 돌려주는 최소 판정이다. LS 오류 봉투 변환은 adapter가 담당한다. */
public enum LsPolicyDecision {
    ALLOW,
    TOKEN_EXPIRED,
    RATE_LIMITED
}
