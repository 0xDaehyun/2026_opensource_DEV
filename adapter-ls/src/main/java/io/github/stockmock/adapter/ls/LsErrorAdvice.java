package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * <p>TODO(ADAPTER-04): 깨진 JSON, 405, 415, 존재하지 않는 URL처럼 Spring이 Controller보다
 * 먼저 처리하는 예외는 아직 이 봉투를 타지 않는다. 덮으려면
 * {@code ResponseEntityExceptionHandler}를 상속해야 하며 별도 작업으로 분리한다.</p>
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
