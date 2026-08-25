package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.IllegalOrderTransitionException;
import io.github.stockmock.core.order.OrderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionException;
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

    // ---------------------------------------------------------------- 봉투

    @ParameterizedTest
    @EnumSource(LsErrorType.class)
    void everyTypeProducesAnEnvelopeWithACodeAndMessage(LsErrorType type) {
        Map<String, Object> envelope = mapper.toEnvelope(type);

        assertThat(envelope).containsOnlyKeys("rsp_cd", "rsp_msg");
        assertThat((String) envelope.get("rsp_cd")).isNotBlank();
        assertThat((String) envelope.get("rsp_msg")).isNotBlank();
    }

    @Test
    void neverLeaksInternalDetailIntoTheEnvelope() {
        assertThat(mapper.toEnvelope(LsErrorType.INTERNAL_ERROR).values())
                .noneSatisfy(value -> assertThat((String) value).contains("java."));
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND))
                .doesNotContainKey("rsp_detail");
    }

    @Test
    void distinctTypesUseDistinctCodes_임시코드() {
        Map<String, Object> codes = Arrays.stream(LsErrorType.values())
                .collect(Collectors.toMap(Enum::name, type -> mapper.toEnvelope(type).get("rsp_cd")));

        assertThat(codes.values()).doesNotHaveDuplicates();
    }

    @Test
    void mapsTheFourCoreErrorCodes_임시코드() {
        assertThat(mapper.toEnvelope(LsErrorType.INVALID_REQUEST)).containsEntry("rsp_cd", "40000");
        assertThat(mapper.toEnvelope(LsErrorType.INSUFFICIENT_FUNDS)).containsEntry("rsp_cd", "40010");
        assertThat(mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND)).containsEntry("rsp_cd", "40410");
        assertThat(mapper.toEnvelope(LsErrorType.ILLEGAL_ORDER_STATE)).containsEntry("rsp_cd", "40900");
    }

    @Test
    void keepsTheExistingUnsupportedTrAndBadRequestCodes_임시코드() {
        assertThat(mapper.toEnvelope(LsErrorType.UNSUPPORTED_TR)).containsEntry("rsp_cd", "40400");
        assertThat(mapper.toEnvelope(LsErrorType.INVALID_REQUEST)).containsEntry("rsp_cd", "40000");
    }

    @Test
    void separatesTheMockOwnFailureFromClientErrors_임시코드() {
        assertThat(mapper.toEnvelope(LsErrorType.INTERNAL_ERROR)).containsEntry("rsp_cd", "50000");
    }

    @Test
    void rejectsANullType() {
        assertThatThrownBy(() -> mapper.toEnvelope(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsAnUnmodifiableEnvelope() {
        Map<String, Object> envelope = mapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND);

        assertThatThrownBy(() -> envelope.put("rsp_cd", "99999"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> envelope.remove("rsp_msg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------- CoreErrorCode

    @ParameterizedTest
    @EnumSource(CoreErrorCode.class)
    void mapsEveryCoreErrorCodeToALsErrorType(CoreErrorCode code) {
        assertThat(LsErrorMapper.typeOf(code)).isNotNull();
    }

    @Test
    void mapsCoreErrorCodesByCodeNotByMessage() {
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.INVALID_REQUEST)).isEqualTo(LsErrorType.INVALID_REQUEST);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.ORDER_NOT_FOUND)).isEqualTo(LsErrorType.ORDER_NOT_FOUND);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.INSUFFICIENT_FUNDS)).isEqualTo(LsErrorType.INSUFFICIENT_FUNDS);
        assertThat(LsErrorMapper.typeOf(CoreErrorCode.ILLEGAL_ORDER_STATE)).isEqualTo(LsErrorType.ILLEGAL_ORDER_STATE);
    }

    @Test
    void rejectsANullCoreErrorCode() {
        assertThatThrownBy(() -> LsErrorMapper.typeOf(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------ classify

    /**
     * 순서 함정 방지 테스트다. {@link IllegalOrderTransitionException}은
     * {@link IllegalStateException}의 하위 타입이므로 내부 오류보다 먼저 판정해야 한다.
     * 체결 완료 주문의 취소는 봇의 잘못이지 목 서버의 잘못이 아니다.
     */
    @Test
    void classifiesAnIllegalTransitionAsAClientErrorNotAnInternalOne() {
        Throwable failure = new IllegalOrderTransitionException(OrderState.FILLED, "취소");

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(LsErrorMapper.classify(failure)).isEqualTo(LsErrorType.ILLEGAL_ORDER_STATE);
        assertThat(LsErrorMapper.classify(new CompletionException(failure)))
                .isEqualTo(LsErrorType.ILLEGAL_ORDER_STATE);
    }

    @Test
    void classifiesACoreExceptionByItsCode() {
        assertThat(LsErrorMapper.classify(
                new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다")))
                .isEqualTo(LsErrorType.ORDER_NOT_FOUND);
    }

    @Test
    void classifiesCoreClientErrorsThrownAsIllegalArgument() {
        // TODO(CORE-03): core가 이 경로를 CoreException으로 바꾸면 타입 판정을 제거한다.
        assertThat(LsErrorMapper.classify(new IllegalArgumentException("중복 clientOrderId입니다")))
                .isEqualTo(LsErrorType.INVALID_REQUEST);
        assertThat(LsErrorMapper.classify(new IllegalArgumentException("대상 주문을 찾을 수 없습니다")))
                .isEqualTo(LsErrorType.INVALID_REQUEST);
    }

    @Test
    void classifiesAnInvariantBreachAsAnInternalError() {
        assertThat(LsErrorMapper.classify(new IllegalStateException("계좌 원장 불변식이 깨졌습니다")))
                .isEqualTo(LsErrorType.INTERNAL_ERROR);
        assertThat(LsErrorMapper.classify(new CompletionException(
                new IllegalStateException("현금 불변식이 깨졌습니다"))))
                .isEqualTo(LsErrorType.INTERNAL_ERROR);
    }

    @Test
    void unwrapsNestedCompletionExceptions() {
        Throwable nested = new CompletionException(new CompletionException(
                new CoreException(CoreErrorCode.ILLEGAL_ORDER_STATE, "취소할 수 없습니다")));

        assertThat(LsErrorMapper.classify(nested)).isEqualTo(LsErrorType.ILLEGAL_ORDER_STATE);
    }

    @Test
    void treatsAnEmptyFailureAsInternal() {
        assertThat(LsErrorMapper.classify(null)).isEqualTo(LsErrorType.INTERNAL_ERROR);
        assertThat(LsErrorMapper.classify(new CompletionException((Throwable) null)))
                .isEqualTo(LsErrorType.INTERNAL_ERROR);
    }

    /**
     * 깊이 제한이 없으면 병리적인 cause 사슬에서 루프가 끝나지 않는다. 한계를 넘으면
     * 매달리는 대신 내부 오류로 닫는다. 실제 {@code join()}은 한두 겹만 감싼다.
     */
    @Test
    void stopsWalkingAPathologicallyDeepCauseChain() {
        Throwable failure = new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다");
        for (int depth = 0; depth < 50; depth++) {
            failure = new CompletionException(failure);
        }

        assertThat(LsErrorMapper.classify(failure)).isEqualTo(LsErrorType.INTERNAL_ERROR);
    }
}
