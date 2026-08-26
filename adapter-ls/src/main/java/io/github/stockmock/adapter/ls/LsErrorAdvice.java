package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * adapter-ls의 모든 Controller가 같은 LS 오류 봉투를 쓰게 한다.
 *
 * <p>{@code @ExceptionHandler}를 Controller 안에 두면 그 Controller에만 적용된다.
 * ADAPTER-03의 토큰 endpoint처럼 Controller가 늘어나도 봉투가 하나로 유지되도록 분리했다.</p>
 *
 * <p>깨진 JSON은 {@link HttpMessageNotReadableException}으로 분류해 LS 잘못된 요청 봉투로
 * 바꾼다. 405, 415, 존재하지 않는 URL처럼 Controller 선택 전에 끝나는 나머지 실패는
 * 별도 HTTP 계약 작업에서 다룬다.</p>
 */
@RestControllerAdvice
public final class LsErrorAdvice {
    private static final Log log = LogFactory.getLog(LsErrorAdvice.class);

    private final LsErrorMapper errorMapper;

    public LsErrorAdvice(LsErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
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

    @ExceptionHandler(LsPolicyException.class)
    public ResponseEntity<Map<String, Object>> policyRejected(LsPolicyException exception) {
        LsErrorType type = switch (exception.decision()) {
            case TOKEN_EXPIRED -> LsErrorType.TOKEN_EXPIRED;
            case RATE_LIMITED -> LsErrorType.RATE_LIMITED;
            case ALLOW -> throw new IllegalArgumentException("ALLOW는 거부 오류가 아닙니다");
        };
        return respond(type, exception);
    }

    @ExceptionHandler({LsRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return respond(LsErrorType.INVALID_REQUEST, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableBody(HttpMessageNotReadableException exception) {
        return respond(LsErrorType.INVALID_REQUEST, exception);
    }

    /**
     * Handler 또는 Controller에서 직접 발생한 예상하지 못한 런타임 실패를 마지막으로 처리한다.
     * 구체적인 예외는 위 handler들이 먼저 선택되고, 나머지만 목 서버 내부 오류로 닫힌다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> unexpectedFailure(RuntimeException exception) {
        return respond(LsErrorType.INTERNAL_ERROR, exception);
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
