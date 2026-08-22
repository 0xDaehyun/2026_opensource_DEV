package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.error.CoreException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletionException;

@RestController
public final class LsController {
    private final LsTrDispatcher dispatcher;
    private final LsErrorMapper errorMapper;

    public LsController(LsTrDispatcher dispatcher, LsErrorMapper errorMapper) {
        this.dispatcher = dispatcher;
        this.errorMapper = errorMapper;
    }

    @PostMapping({"/stock/order", "/stock/accno"})
    public Map<String, Object> handle(@RequestBody JsonNode body) {
        return dispatcher.dispatch(body);
    }

    /**
     * core 오류를 LS 봉투로 바꾼다. Handler가 {@code CompletableFuture.join()}을 호출하면
     * core 예외가 {@link CompletionException}으로 감싸여 올라오므로 원인을 벗겨 코드로 판단한다.
     * 메시지 문자열은 분류에 사용하지 않고 {@code rsp_detail}로만 전달한다.
     */
    @ExceptionHandler({CoreException.class, CompletionException.class})
    public ResponseEntity<Map<String, Object>> coreFailure(RuntimeException exception) {
        CoreException coreFailure = coreCauseOf(exception);
        LsErrorType type = coreFailure == null
                ? LsErrorType.INVALID_REQUEST
                : LsErrorMapper.typeOf(coreFailure.code());
        String detail = coreFailure == null ? exception.getMessage() : coreFailure.getMessage();
        return respond(type, detail);
    }

    @ExceptionHandler(UnknownTrException.class)
    public ResponseEntity<Map<String, Object>> unknownTr(UnknownTrException exception) {
        return respond(LsErrorType.UNSUPPORTED_TR, exception.getMessage());
    }

    @ExceptionHandler({LsRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return respond(LsErrorType.INVALID_REQUEST, exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respond(LsErrorType type, String detail) {
        return ResponseEntity.status(statusOf(type)).body(errorMapper.toEnvelope(type, detail));
    }

    /**
     * HTTP 상태는 봉투가 아니라 전송 계층의 값이므로 {@link LsErrorMapper}가 아니라 여기서 고른다.
     * 사용하는 상태는 LS 콘솔에서 관측한 400, 401, 404, 409, 429로 제한한다.
     */
    private HttpStatus statusOf(LsErrorType type) {
        return switch (type) {
            case INVALID_REQUEST, INSUFFICIENT_FUNDS -> HttpStatus.BAD_REQUEST;
            case TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case ORDER_NOT_FOUND, UNSUPPORTED_TR -> HttpStatus.NOT_FOUND;
            case ILLEGAL_ORDER_STATE -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }

    private CoreException coreCauseOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CoreException coreFailure) {
                return coreFailure;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
