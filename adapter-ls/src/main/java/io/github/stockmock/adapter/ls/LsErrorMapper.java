package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 중립 오류를 LS 오류 봉투로 변환한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code type}: null이 아닌 {@link LsErrorType}</li>
 *   <li>{@code detail}: 로그와 테스트에 사용할 선택 상세 메시지</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <p>{@code rsp_cd}, {@code rsp_msg}를 포함한 수정 불가능한 Map. {@code detail}이 있으면
 * {@code rsp_detail}이 추가된다.</p>
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>같은 {@code type}은 항상 같은 {@code rsp_cd}와 {@code rsp_msg}로 변환한다.</li>
 *   <li>{@code detail}은 {@code rsp_msg}를 덮어쓰지 않는다. 봇이 파싱하는 값이기 때문이다.</li>
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
    /** LS 오류 분류별로 고정된 봉투 값이다. 같은 type은 언제나 같은 코드와 메시지를 만든다. */
    private static final Map<LsErrorType, Envelope> ENVELOPES = Map.of(
            LsErrorType.INVALID_REQUEST, new Envelope("40000", "요청 값이 올바르지 않습니다"),
            LsErrorType.INSUFFICIENT_FUNDS, new Envelope("40010", "주문 가능 금액이 부족합니다"),
            LsErrorType.TOKEN_EXPIRED, new Envelope("40100", "토큰이 만료되었습니다"),
            LsErrorType.UNSUPPORTED_TR, new Envelope("40400", "지원하지 않는 TR입니다"),
            LsErrorType.ORDER_NOT_FOUND, new Envelope("40410", "주문을 찾을 수 없습니다"),
            LsErrorType.ILLEGAL_ORDER_STATE, new Envelope("40900", "현재 주문 상태에서는 처리할 수 없습니다"),
            LsErrorType.RATE_LIMITED, new Envelope("42900", "호출 한도를 초과했습니다"));

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

    public Map<String, Object> toEnvelope(LsErrorType type, String detail) {
        if (type == null) {
            throw new IllegalArgumentException("LsErrorType이 필요합니다");
        }
        Envelope fixed = ENVELOPES.get(type);
        if (fixed == null) {
            throw new IllegalStateException("LS 봉투가 정의되지 않은 오류 분류입니다: " + type);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("rsp_cd", fixed.code());
        envelope.put("rsp_msg", fixed.message());
        if (detail != null && !detail.isBlank()) {
            envelope.put("rsp_detail", detail);
        }
        return Collections.unmodifiableMap(envelope);
    }

    private record Envelope(String code, String message) {
    }
}
