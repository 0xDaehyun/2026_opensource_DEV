package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.error.CoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
    private static final Log log = LogFactory.getLog(LsController.class);

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
     * 엔진에서 올라온 실패를 LS 봉투로 바꾼다. Handler가 {@code CompletableFuture.join()}을
     * 호출하면 원래 실패가 {@link CompletionException}에 싸여 올라오므로
     * {@link LsErrorMapper#classify}가 포장을 벗기고 코드 또는 타입으로 판정한다.
     * 메시지 문자열은 분류에 쓰지 않고 로그로만 남긴다.
     */
    @ExceptionHandler({CoreException.class, CompletionException.class})
    public ResponseEntity<Map<String, Object>> engineFailure(RuntimeException exception) {
        return respond(LsErrorMapper.classify(exception), exception);
    }

    @ExceptionHandler(UnknownTrException.class)
    public ResponseEntity<Map<String, Object>> unknownTr(UnknownTrException exception) {
        return respond(LsErrorType.UNSUPPORTED_TR, exception);
    }

    @ExceptionHandler({LsRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return respond(LsErrorType.INVALID_REQUEST, exception);
    }

    /**
     * 원인 상세는 봉투가 아니라 로그로 나간다. 실제 LS 응답에 없는 키를 늘리지 않고,
     * Java 클래스명 같은 내부 정보가 봇에게 새지 않게 한다.
     */
    private ResponseEntity<Map<String, Object>> respond(LsErrorType type, Throwable failure) {
        if (type == LsErrorType.INTERNAL_ERROR) {
            log.error("목 서버 내부 오류로 " + type + " 봉투를 반환합니다", failure);
        } else if (log.isDebugEnabled()) {
            log.debug(type + " 봉투를 반환합니다: " + failure.getMessage());
        }
        return ResponseEntity.status(statusOf(type)).body(errorMapper.toEnvelope(type));
    }

    /**
     * HTTP 상태는 봉투가 아니라 전송 계층의 값이므로 {@link LsErrorMapper}가 아니라 여기서 고른다.
     *
     * <p>AGENTS.md 6절이 LS 콘솔에서 실제로 관측했다고 기록한 상태는 400, 401, 404, 405, 500,
     * 503이다. 409와 429는 관측 목록에 없는 <b>추정값</b>이며 ADAPTER-05 fixture 확인 대상이다.</p>
     */
    private HttpStatus statusOf(LsErrorType type) {
        return switch (type) {
            case INVALID_REQUEST, INSUFFICIENT_FUNDS -> HttpStatus.BAD_REQUEST;
            case TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case ORDER_NOT_FOUND, UNSUPPORTED_TR -> HttpStatus.NOT_FOUND;
            case ILLEGAL_ORDER_STATE -> HttpStatus.CONFLICT;   // 추정값
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS; // 추정값
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
