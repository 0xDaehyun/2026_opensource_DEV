package io.github.stockmock.adapter.ls;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO(ADAPTER-04) 테스트 목록:
 * <ul>
 *   <li>각 LsErrorType이 고정된 rsp_cd와 rsp_msg를 반환한다.</li>
 *   <li>null type을 거부한다.</li>
 *   <li>반환 Map을 외부에서 수정할 수 없다.</li>
 * </ul>
 *
 * <p>rsp_cd 값은 LS 공식 콘솔로 확인되지 않은 임시 코드다.
 * 확정되면 이 테스트의 기대값을 함께 갱신한다.</p>
 */
class LsErrorMapperTest {
    private final LsErrorMapper mapper = new LsErrorMapper();

    @Test
    void toEnvelopeMapsEachErrorTypeToFixedTemporaryRspCode() {
        for (LsErrorType type : LsErrorType.values()) {
            Map<String, Object> first = mapper.toEnvelope(type, "detail-" + type);
            Map<String, Object> second = mapper.toEnvelope(type, "다른 detail");

            assertThat(first.get("rsp_cd")).isNotNull().isEqualTo(second.get("rsp_cd"));
            assertThat(first.get("rsp_msg")).isNotNull().isEqualTo(second.get("rsp_msg"));
        }
    }

    @Test
    void toEnvelopeRejectsNullType() {
        assertThatThrownBy(() -> mapper.toEnvelope(null, "detail"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toEnvelopeReturnsImmutableMap() {
        Map<String, Object> envelope = mapper.toEnvelope(LsErrorType.INVALID_REQUEST, null);

        assertThatThrownBy(() -> envelope.put("rsp_cd", "99999"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
