package io.github.stockmock.adapter.ls;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 중립 오류를 LS 오류 봉투로 변환한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code type}: null이 아닌 {@link LsErrorType}</li>
 *   <li>{@code detail}: 로그와 테스트에 사용할 선택 상세 메시지 (봉투에는 포함하지 않는다)</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <p>최소 {@code rsp_cd}, {@code rsp_msg}를 포함한 수정 불가능한 Map.</p>
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>같은 {@code type}은 항상 같은 {@code rsp_cd}로 변환한다.</li>
 *   <li>null type은 {@link IllegalArgumentException}으로 거부한다.</li>
 *   <li>core에 LS 코드나 메시지를 추가하지 않는다.</li>
 * </ul>
 *
 * <p>아래 {@code rsp_cd} 값은 LS 공식 콘솔로 확인되지 않은 임시 코드다.
 * 공식 fixture를 확보하면 값을 교체한다.</p>
 */
public final class LsErrorMapper {
    private static final Map<LsErrorType, Entry> ENVELOPES = new EnumMap<>(LsErrorType.class);

    static {
        ENVELOPES.put(LsErrorType.INVALID_REQUEST, new Entry("40000", "잘못된 요청입니다"));
        ENVELOPES.put(LsErrorType.ORDER_NOT_FOUND, new Entry("40401", "주문을 찾을 수 없습니다"));
        ENVELOPES.put(LsErrorType.INSUFFICIENT_FUNDS, new Entry("40001", "증거금이 부족합니다"));
        ENVELOPES.put(LsErrorType.ILLEGAL_ORDER_STATE, new Entry("40002", "처리할 수 없는 주문 상태입니다"));
        ENVELOPES.put(LsErrorType.TOKEN_EXPIRED, new Entry("40100", "토큰이 만료되었습니다"));
        ENVELOPES.put(LsErrorType.RATE_LIMITED, new Entry("42900", "요청 한도를 초과했습니다"));
        ENVELOPES.put(LsErrorType.UNSUPPORTED_TR, new Entry("40400", "지원하지 않는 TR입니다"));
    }

    public Map<String, Object> toEnvelope(LsErrorType type, String detail) {
        if (type == null) {
            throw new IllegalArgumentException("LsErrorType은 null일 수 없습니다.");
        }
        Entry entry = ENVELOPES.get(type);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("rsp_cd", entry.rspCode());
        envelope.put("rsp_msg", entry.rspMessage());
        return Map.copyOf(envelope);
    }

    private record Entry(String rspCode, String rspMessage) {
    }
}
