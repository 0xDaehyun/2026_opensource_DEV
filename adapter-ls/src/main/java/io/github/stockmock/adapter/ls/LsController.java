package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * LS는 여러 TR이 같은 URL을 공유하고 요청 본문의 InBlock 키로 동작이 갈린다. 그래서 TR마다
 * Controller를 만들지 않고 URL 소수 + {@link LsTrDispatcher} 구조를 쓴다.
 *
 * <p>오류 응답은 {@link LsErrorAdvice}가 만든다.</p>
 */
@RestController
public final class LsController {
    private final LsTrDispatcher dispatcher;
    private final TokenController tokenController;
    private final Duration tokenTtl;
    private final LsRequestPolicy requestPolicy;

    public LsController(LsTrDispatcher dispatcher, TokenController tokenController,
            @Value("${mock.token.ttl:PT24H}") Duration tokenTtl) {
        this(dispatcher, tokenController, tokenTtl, LsRequestPolicy.permitAll());
    }

    @Autowired
    public LsController(LsTrDispatcher dispatcher, TokenController tokenController,
            @Value("${mock.token.ttl:PT24H}") Duration tokenTtl,
            LsRequestPolicy requestPolicy) {
        this.dispatcher = dispatcher;
        this.tokenController = tokenController;
        this.tokenTtl = tokenTtl;
        this.requestPolicy = requestPolicy;
    }

    @PostMapping({"/stock/order", "/stock/accno"})
    public Map<String, Object> handle(
            @RequestBody JsonNode body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LsRequestOperation operation = operationOf(body);
        reject(requestPolicy.beforeRequest(operation, authorization));
        Map<String, Object> response = dispatcher.dispatch(body);
        requestPolicy.afterRequest(operation);
        return response;
    }

    @PostMapping("/oauth2/token")
    public TokenResponse issueToken(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "appkey", required = false) String appKey,
            @RequestParam(value = "appsecretkey", required = false) String appSecretKey,
            @RequestParam(value = "scope", required = false) String scope) {
        TokenRequest request = new TokenRequest(grantType, appKey, appSecretKey, scope);
        TokenResponse response = tokenController.issue(request, requestPolicy.tokenTtl(tokenTtl));
        requestPolicy.tokenIssued(response.accessToken());
        return response;
    }

    private LsRequestOperation operationOf(JsonNode body) {
        if (body != null) {
            if (body.has("CSPAT00601InBlock1")) {
                return LsRequestOperation.PLACE_ORDER;
            }
            if (body.has("CSPAT00801InBlock1")) {
                return LsRequestOperation.CANCEL;
            }
            if (body.has("t0424InBlock")) {
                return LsRequestOperation.QUERY_ACCOUNT;
            }
            if (body.has("t0425InBlock")) {
                return LsRequestOperation.QUERY_ORDER;
            }
        }
        return LsRequestOperation.UNKNOWN;
    }

    private void reject(LsPolicyDecision decision) {
        if (decision != LsPolicyDecision.ALLOW) {
            throw new LsPolicyException(decision);
        }
    }
}
