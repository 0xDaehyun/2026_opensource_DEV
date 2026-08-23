package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public final class LsController {
    private final LsTrDispatcher dispatcher;
    private final TokenController tokenController;
    private final Duration tokenTtl;

    public LsController(LsTrDispatcher dispatcher, TokenController tokenController,
            @Value("${mock.token.ttl:PT24H}") Duration tokenTtl) {
        this.dispatcher = dispatcher;
        this.tokenController = tokenController;
        this.tokenTtl = tokenTtl;
    }

    @PostMapping({"/stock/order", "/stock/accno"})
    public Map<String, Object> handle(@RequestBody JsonNode body) {
        return dispatcher.dispatch(body);
    }

    @PostMapping("/oauth2/token")
    public TokenResponse issueToken(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "appkey", required = false) String appKey,
            @RequestParam(value = "appsecretkey", required = false) String appSecretKey,
            @RequestParam(value = "scope", required = false) String scope) {
        TokenRequest request = new TokenRequest(grantType, appKey, appSecretKey, scope);
        return tokenController.issue(request, tokenTtl);
    }

    @ExceptionHandler(UnknownTrException.class)
    public ResponseEntity<Map<String, Object>> unknownTr(UnknownTrException exception) {
        return error(HttpStatus.NOT_FOUND, "40400", exception.getMessage());
    }

    @ExceptionHandler({LsRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, "40000", exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("rsp_cd", code);
        envelope.put("rsp_msg", message);
        return ResponseEntity.status(status).body(envelope);
    }
}
