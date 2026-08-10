package io.github.stockmock.adapter.ls;

import java.util.Map;

/**
 * TODO(ADAPTER-04): 중립 오류를 LS 오류 봉투로 변환한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code type}: null이 아닌 {@link LsErrorType}</li>
 *   <li>{@code detail}: 로그와 테스트에 사용할 선택 상세 메시지</li>
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
 *   <li>공식 fixture를 확인하지 않은 코드는 임시 코드임을 테스트 이름에 표시한다.</li>
 * </ul>
 */
public final class LsErrorMapper {
    public Map<String, Object> toEnvelope(LsErrorType type, String detail) {
        throw new UnsupportedOperationException("TODO(ADAPTER-04): LS 오류 봉투 변환");
    }
}
