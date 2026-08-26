package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.IllegalOrderTransitionException;
import io.github.stockmock.core.order.OrderState;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 분류와 봉투 내용을 검증한다. 이 예외들이 실제로 라우팅되는지는
 * {@link LsErrorRoutingTest}가 MockMvc로 확인한다.
 */
class LsErrorAdviceTest {
    private final LsErrorAdvice advice = new LsErrorAdvice(new LsErrorMapper());

    @Test
    void mapsACoreExceptionByItsCodeNotItsMessage() {
        ResponseEntity<Map<String, Object>> response = advice.engineFailure(
                new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40410");
    }

    @Test
    void unwrapsACoreExceptionWrappedByCompletableFutureJoin() {
        CompletionException wrapped = new CompletionException(
                new CoreException(CoreErrorCode.ILLEGAL_ORDER_STATE, "취소할 수 없습니다"));

        ResponseEntity<Map<String, Object>> response = advice.engineFailure(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40900");
    }

    /**
     * 체결 완료 주문의 취소는 봇의 잘못이므로 409다. 목 서버 내부 오류인 500으로 새면
     * 봇 개발자가 자기 코드 대신 목을 의심하게 된다.
     */
    @Test
    void reportsAnIllegalTransitionAsAConflictNotAnInternalError() {
        ResponseEntity<Map<String, Object>> response = advice.engineFailure(
                new CompletionException(new IllegalOrderTransitionException(OrderState.FILLED, "취소")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40900");
    }

    /** core가 아직 코드 없이 던지는 봇 잘못은 400을 유지한다. TODO(CORE-03). */
    @Test
    void keepsCoreClientErrorsAsBadRequest() {
        ResponseEntity<Map<String, Object>> response = advice.engineFailure(
                new CompletionException(new IllegalArgumentException("중복 clientOrderId입니다")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40000");
    }

    /**
     * 목 자체의 실패를 "요청이 잘못됐다"로 위장하지 않는다. AGENTS.md 4.2절의
     * "엔진의 장부는 절대 틀리지 않는다"가 깨진 상황은 500으로 정직하게 보고한다.
     */
    @Test
    void reportsTheMockOwnFailureAsInternalServerError() {
        ResponseEntity<Map<String, Object>> response = advice.engineFailure(
                new CompletionException(new IllegalStateException("계좌 원장 불변식이 깨졌습니다")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("rsp_cd", "50000");
    }

    @Test
    void neverLeaksAJavaClassNameToTheBot() {
        ResponseEntity<Map<String, Object>> response = advice.engineFailure(
                new CompletionException(new IllegalStateException("알 수 없는 실패")));

        assertThat(response.getBody()).containsOnlyKeys("rsp_cd", "rsp_msg");
        assertThat(response.getBody().values())
                .noneSatisfy(value -> assertThat((String) value).contains("java."));
    }

    @Test
    void keepsTheUnknownTrEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                advice.unknownTr(new UnknownTrException("지원하지 않는 TR입니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40400");
    }

    @Test
    void keepsTheBadRequestEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                advice.badRequest(new LsRequestException("IsuNo 값이 필요합니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40000");
    }

    @Test
    void mapsRequestPolicyRejectionsToObservedLsEnvelopes() {
        ResponseEntity<Map<String, Object>> expired = advice.policyRejected(
                new LsPolicyException(LsPolicyDecision.TOKEN_EXPIRED));
        ResponseEntity<Map<String, Object>> limited = advice.policyRejected(
                new LsPolicyException(LsPolicyDecision.RATE_LIMITED));

        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(expired.getBody()).containsEntry("rsp_cd", "40100");
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).containsEntry("rsp_cd", "42900");
    }

    @Test
    void everyErrorResponseCarriesExactlyTheLsEnvelopeKeys() {
        List<ResponseEntity<Map<String, Object>>> responses = List.of(
                advice.engineFailure(new CoreException(CoreErrorCode.INSUFFICIENT_FUNDS, "증거금이 부족합니다")),
                advice.engineFailure(new CompletionException(new IllegalStateException("내부 실패"))),
                advice.unknownTr(new UnknownTrException("지원하지 않는 TR입니다")),
                advice.badRequest(new LsRequestException("잘못된 요청입니다")));

        assertThat(responses).allSatisfy(response ->
                assertThat(response.getBody()).containsOnlyKeys("rsp_cd", "rsp_msg"));
    }
}
