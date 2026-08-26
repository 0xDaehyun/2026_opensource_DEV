package io.github.stockmock.adapter.ls;

import java.time.Duration;

/**
 * 인증·호출 제한·응답 지연을 app 조립 계층에서 적용하기 위한 포트다.
 * adapter는 시나리오 타입을 모르고 판정 결과만 LS 오류 봉투로 바꾼다.
 */
public interface LsRequestPolicy {
    default Duration tokenTtl(Duration configuredDefault) {
        return configuredDefault;
    }

    default void tokenIssued(String accessToken) {
    }

    default LsPolicyDecision beforeRequest(LsRequestOperation operation, String authorization) {
        return LsPolicyDecision.ALLOW;
    }

    default void afterRequest(LsRequestOperation operation) {
    }

    static LsRequestPolicy permitAll() {
        return new LsRequestPolicy() {
        };
    }
}
