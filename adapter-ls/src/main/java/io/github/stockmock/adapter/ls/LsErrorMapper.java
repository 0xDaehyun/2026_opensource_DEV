package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.IllegalOrderTransitionException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 중립 오류를 LS 오류 봉투로 변환한다.
 *
 * <h2>출력</h2>
 * <p>{@code rsp_cd}, {@code rsp_msg}만 담은 수정 불가능한 Map이다. 실제 LS 봉투에 없는 키는
 * 추가하지 않는다. 원인 상세는 응답이 아니라 서버 로그에 남긴다.</p>
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>같은 {@code type}은 항상 같은 {@code rsp_cd}와 {@code rsp_msg}로 변환한다.</li>
 *   <li>null type은 {@link IllegalArgumentException}으로 거부한다.</li>
 *   <li>core에 LS 코드나 메시지를 추가하지 않는다.</li>
 * </ul>
 *
 * <h2>임시 rsp_cd</h2>
 * <p>아래 코드는 LS 공식 콘솔에서 확인하기 전까지 임시값이다. {@code INVALID_REQUEST(40000)}과
 * {@code UNSUPPORTED_TR(40400)}은 기존 {@link LsController} 동작을 그대로 유지한 값이다.
 * 확정 전까지 관련 테스트 이름에 {@code 임시코드}를 남긴다.</p>
 */
@Component
public final class LsErrorMapper {
    /** cause 사슬을 따라갈 최대 깊이다. 병리적인 사슬에서 매달리지 않기 위한 방어값이다. */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * core의 중립 오류 코드를 LS 오류 분류로 바꾼다. 변환은 adapter의 책임이므로 core는
     * LS 분류를 알지 못한다.
     */
    public static LsErrorType typeOf(CoreErrorCode code) {
        if (code == null) {
            throw new IllegalArgumentException("CoreErrorCode가 필요합니다");
        }
        return switch (code) {
            case INVALID_REQUEST -> LsErrorType.INVALID_REQUEST;
            case ORDER_NOT_FOUND -> LsErrorType.ORDER_NOT_FOUND;
            case INSUFFICIENT_FUNDS -> LsErrorType.INSUFFICIENT_FUNDS;
            case ILLEGAL_ORDER_STATE -> LsErrorType.ILLEGAL_ORDER_STATE;
        };
    }

    /**
     * 엔진에서 올라온 실패를 봇 잘못과 목 서버 잘못으로 가른다. 세 갈래다.
     *
     * <ol>
     *   <li>{@link CoreException} — core가 코드로 분류한 오류. {@link #typeOf}를 그대로 쓴다.</li>
     *   <li>봇 잘못이지만 core가 아직 코드 없이 던지는 오류 — 타입으로 판정한다.</li>
     *   <li>나머지 — 목 서버 자신의 실패로 보고 {@link LsErrorType#INTERNAL_ERROR}로 닫는다.</li>
     * </ol>
     *
     * <p>판정 순서가 중요하다. {@link IllegalOrderTransitionException}은
     * {@link IllegalStateException}의 하위 타입이므로 내부 오류보다 먼저 걸러야 한다.
     * 체결 완료 주문의 취소는 봇의 잘못이지 목 서버의 장부 오류가 아니다.</p>
     *
     * <p>TODO(CORE-03): 2번 갈래는 core가 아래 경로를 {@code CoreException}으로 던지면 사라진다.
     * 현재 {@code SimulationEngine}은 clientOrderId 누락·중복, 취소 대상 없음, 부분 취소 시도를
     * 평범한 {@link IllegalArgumentException}으로, 불법 상태 전이를
     * {@link IllegalOrderTransitionException}으로 완료시킨다. 이 전제가 깨지면 내부 오류가
     * 400으로 위장되므로, core 변경 시 이 메서드를 함께 정리해야 한다.</p>
     */
    public static LsErrorType classify(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CoreException coreFailure) {
            return typeOf(coreFailure.code());
        }
        if (cause instanceof IllegalOrderTransitionException) {
            return LsErrorType.ILLEGAL_ORDER_STATE;
        }
        if (cause instanceof IllegalArgumentException) {
            return LsErrorType.INVALID_REQUEST;
        }
        return LsErrorType.INTERNAL_ERROR;
    }

    public Map<String, Object> toEnvelope(LsErrorType type) {
        if (type == null) {
            throw new IllegalArgumentException("LsErrorType이 필요합니다");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("rsp_cd", codeOf(type));
        envelope.put("rsp_msg", messageOf(type));
        return Collections.unmodifiableMap(envelope);
    }

    /** switch 표현식이라 새 {@link LsErrorType} 상수는 런타임이 아니라 컴파일 시점에 걸린다. */
    private String codeOf(LsErrorType type) {
        return switch (type) {
            case INVALID_REQUEST -> "40000";
            case INSUFFICIENT_FUNDS -> "40010";
            case TOKEN_EXPIRED -> "40100";
            case UNSUPPORTED_TR -> "40400";
            case ORDER_NOT_FOUND -> "40410";
            case ILLEGAL_ORDER_STATE -> "40900";
            case RATE_LIMITED -> "42900";
            case INTERNAL_ERROR -> "50000";
        };
    }

    private String messageOf(LsErrorType type) {
        return switch (type) {
            case INVALID_REQUEST -> "요청 값이 올바르지 않습니다";
            case INSUFFICIENT_FUNDS -> "주문 가능 금액이 부족합니다";
            case TOKEN_EXPIRED -> "토큰이 만료되었습니다";
            case UNSUPPORTED_TR -> "지원하지 않는 TR입니다";
            case ORDER_NOT_FOUND -> "주문을 찾을 수 없습니다";
            case ILLEGAL_ORDER_STATE -> "현재 주문 상태에서는 처리할 수 없습니다";
            case RATE_LIMITED -> "호출 한도를 초과했습니다";
            case INTERNAL_ERROR -> "목 서버 내부 오류입니다";
        };
    }

    /**
     * {@code CompletableFuture.join()}이 씌운 포장을 벗긴다. 포장 종류만 벗기므로 원래 실패의
     * 타입 정보가 보존된다. 깊이를 넘으면 매달리는 대신 마지막으로 본 예외를 돌려준다.
     */
    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            boolean wrapper = current instanceof CompletionException || current instanceof ExecutionException;
            if (!wrapper || current.getCause() == null) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }
}
