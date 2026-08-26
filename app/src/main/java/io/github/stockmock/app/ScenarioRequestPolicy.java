package io.github.stockmock.app;

import io.github.stockmock.adapter.ls.LsPolicyDecision;
import io.github.stockmock.adapter.ls.LsRequestOperation;
import io.github.stockmock.adapter.ls.LsRequestPolicy;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.scenario.ScenarioSettings;
import io.github.stockmock.scenario.constraint.Operation;
import io.github.stockmock.scenario.constraint.RateLimitDecision;
import io.github.stockmock.scenario.constraint.RateLimitPolicy;
import io.github.stockmock.scenario.constraint.TokenExpiryPolicy;
import io.github.stockmock.scenario.constraint.TokenStatus;
import io.github.stockmock.scenario.fault.FaultTiming;
import io.github.stockmock.scenario.fault.ResponseDelayPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 시나리오 정책을 실제 LS HTTP 요청의 앞뒤에 적용하는 app 조립 구현이다. */
public final class ScenarioRequestPolicy implements LsRequestPolicy {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ScenarioSettings settings;
    private final VirtualClock clock;
    private final RateLimitPolicy rateLimitPolicy = new RateLimitPolicy();
    private final TokenExpiryPolicy tokenExpiryPolicy = new TokenExpiryPolicy();
    private final ResponseDelayPolicy responseDelayPolicy = new ResponseDelayPolicy();
    private final Map<String, Instant> issuedTokens = new ConcurrentHashMap<>();
    private final Duration defaultTokenTtl;

    public ScenarioRequestPolicy(ScenarioSettings settings, VirtualClock clock, Duration defaultTokenTtl) {
        this.settings = settings;
        this.clock = clock;
        this.defaultTokenTtl = defaultTokenTtl;
    }

    @Override
    public Duration tokenTtl(Duration configuredDefault) {
        return settings.tokenTtl().orElse(configuredDefault);
    }

    @Override
    public void tokenIssued(String accessToken) {
        issuedTokens.put(accessToken, clock.now());
    }

    @Override
    public LsPolicyDecision beforeRequest(LsRequestOperation requestOperation, String authorization) {
        String token = bearerToken(authorization);
        Instant issuedAt = issuedTokens.get(token);
        Instant now = clock.now();
        if (issuedAt == null || tokenExpiryPolicy.evaluate(issuedAt, now, effectiveTokenTtl())
                == TokenStatus.EXPIRED) {
            return LsPolicyDecision.TOKEN_EXPIRED;
        }

        Optional<Operation> operation = operationOf(requestOperation);
        if (operation.isEmpty()) {
            return LsPolicyDecision.ALLOW;
        }

        if (settings.ratePerSecond().isPresent()
                && rateLimitPolicy.evaluate(token, operation.get(), now,
                settings.ratePerSecond().getAsInt()) == RateLimitDecision.RATE_LIMITED) {
            return LsPolicyDecision.RATE_LIMITED;
        }

        applyDelay(operation.get(), FaultTiming.BEFORE_COMMIT);
        return LsPolicyDecision.ALLOW;
    }

    @Override
    public void afterRequest(LsRequestOperation requestOperation) {
        operationOf(requestOperation)
                .ifPresent(operation -> applyDelay(operation, FaultTiming.AFTER_COMMIT));
    }

    private Duration effectiveTokenTtl() {
        return settings.tokenTtl().orElse(defaultTokenTtl);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return "";
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private Optional<Operation> operationOf(LsRequestOperation operation) {
        if (operation == null || operation == LsRequestOperation.UNKNOWN) {
            return Optional.empty();
        }
        return Optional.of(switch (operation) {
            case QUERY_ACCOUNT -> Operation.QUERY_ACCOUNT;
            case PLACE_ORDER -> Operation.PLACE_ORDER;
            case QUERY_ORDER -> Operation.QUERY_ORDER;
            case CANCEL -> Operation.CANCEL;
            case UNKNOWN -> throw new IllegalStateException("UNKNOWN은 위에서 제외됩니다");
        });
    }

    private void applyDelay(Operation operation, FaultTiming timing) {
        settings.responseDelay()
                .flatMap(rule -> responseDelayPolicy.delayFor(operation, timing, rule))
                .ifPresent(this::sleep);
    }

    private void sleep(Duration delay) {
        try {
            long millis = delay.toMillis();
            int nanos = (int) (delay.minusMillis(millis).toNanos());
            Thread.sleep(millis, nanos);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("응답 지연 중 요청 스레드가 중단됐습니다", interrupted);
        }
    }
}
