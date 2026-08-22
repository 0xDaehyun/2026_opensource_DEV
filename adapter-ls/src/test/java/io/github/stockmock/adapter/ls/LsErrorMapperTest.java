package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
 * <p>rsp_cd 값은 LS 공식 콘솔에서 확인하기 전까지 임시값이다. 확정 전까지 테스트 이름에
 * {@code 임시코드}를 남겨 어떤 단언이 콘솔 확인 후 바뀌어야 하는지 표시한다.</p>
 */
class LsErrorMapperTest {
    private final LsErrorMapper mapper = new LsErrorMapper();

    @ParameterizedTest
    @EnumSource(LsErrorType.class)
    void everyTypeProducesAnEnvelopeWithACodeAndMessage(LsErrorType type) {
        Map<String, Object> envelope = mapper.toEnvelope(type, null);

        assertThat(envelope).containsOnlyKeys("rsp_cd", "rsp_msg");
        assertThat((String) envelope.get("rsp_cd")).isNotBlank();
        assertThat((String) envelope.get("rsp_msg")).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(LsErrorType.class)
    void theSameTypeAlwaysProducesTheSameEnvelope(LsErrorType type) {
        assertThat(mapper.toEnvelope(type, "첫 번째 상세"))
                .containsAllEntriesOf(Map.of(
                        "rsp_cd", mapper.toEnvelope(type, "두 번째 상세").get("rsp_cd"),
                        "rsp_msg", mapper.toEnvelope(type, "두 번째 상세").get("rsp_msg")));
    }

    @Test
    void distinctTypesUseDistinctCodes_임시코드() {
        Map<String, Object> codes = Arrays.stream(LsErrorType.values())
                .collect(Collectors.toMap(
                        type -> type.name(),
                        type -> mapper.toEnvelope(type, null).get("rsp_cd")));

        assertThat(codes.values()).doesNotHaveDuplicates();
    }

    @Test
    void mapsTheFourCoreErrorCodes_임시코드() {
        assertThat(mapper.toEnvelope(LsErrorType.INVALID_REQUEST, null))
                .containsEntry("rsp_cd", "40000");
        assertThat(mapper.toEnvelope(LsErrorType.INSUFFICIENT_FUNDS, null))
                .containsEntry("rsp_cd", "40010");
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, null))
                .containsEntry("rsp_cd", "40410");
        assertThat(mapper.toEnvelope(LsErrorType.ILLEGAL_ORDER_STATE, null))
                .containsEntry("rsp_cd", "40900");
    }

    @Test
    void keepsTheExistingUnsupportedTrAndBadRequestCodes_임시코드() {
        assertThat(mapper.toEnvelope(LsErrorType.UNSUPPORTED_TR, null))
                .containsEntry("rsp_cd", "40400");
        assertThat(mapper.toEnvelope(LsErrorType.INVALID_REQUEST, null))
                .containsEntry("rsp_cd", "40000");
    }

    @Test
    void addsTheDetailOnlyWhenItIsPresent() {
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, "ORD-000009"))
                .containsEntry("rsp_detail", "ORD-000009");
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, "  "))
                .doesNotContainKey("rsp_detail");
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, null))
                .doesNotContainKey("rsp_detail");
    }

    @Test
    void neverLetsTheDetailOverrideTheFixedMessage() {
        Map<String, Object> withDetail = mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, "내부 상세");
        Map<String, Object> withoutDetail = mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, null);

        assertThat(withDetail.get("rsp_msg")).isEqualTo(withoutDetail.get("rsp_msg"));
    }

    @Test
    void rejectsANullType() {
        assertThatThrownBy(() -> mapper.toEnvelope(null, "상세"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsAnUnmodifiableEnvelope() {
        Map<String, Object> envelope = mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, "상세");

        assertThatThrownBy(() -> envelope.put("rsp_cd", "99999"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> envelope.remove("rsp_msg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(CoreErrorCode.class)
    void mapsEveryCoreErrorCodeToALsErrorType(CoreErrorCode code) {
        assertThat(LsErrorMapper.typeOf(code)).isNotNull();
    }

    @Test
    void mapsCoreErrorCodesByCodeNotByMessage() {
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.INVALID_REQUEST))
                .isEqualTo(LsErrorType.INVALID_REQUEST);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.ORDER_NOT_FOUND))
                .isEqualTo(LsErrorType.ORDER_NOT_FOUND);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.INSUFFICIENT_FUNDS))
                .isEqualTo(LsErrorType.INSUFFICIENT_FUNDS);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.ILLEGAL_ORDER_STATE))
                .isEqualTo(LsErrorType.ILLEGAL_ORDER_STATE);
    }

    @Test
    void rejectsANullCoreErrorCode() {
        assertThatThrownBy(() -> LsErrorMapper.typeOf(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
