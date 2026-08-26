package io.github.stockmock.adapter.ls;

/** 요청이 엔진에 도달하기 전에 인증 또는 호출 제한 정책에서 거부됐음을 나타낸다. */
public final class LsPolicyException extends RuntimeException {
    private final LsPolicyDecision decision;

    public LsPolicyException(LsPolicyDecision decision) {
        super("요청 정책 거부: " + decision);
        if (decision == null || decision == LsPolicyDecision.ALLOW) {
            throw new IllegalArgumentException("거부 판정이 필요합니다");
        }
        this.decision = decision;
    }

    public LsPolicyDecision decision() {
        return decision;
    }
}
