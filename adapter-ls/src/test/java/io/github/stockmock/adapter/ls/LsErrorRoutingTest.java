package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.IllegalOrderTransitionException;
import io.github.stockmock.core.order.OrderState;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link LsErrorAdvice}가 실제 HTTP 경로에서 라우팅되는지 확인한다. 단위 테스트는 handler를
 * 직접 호출하므로 Spring이 그 handler를 고르는지까지는 증명하지 못한다.
 */
class LsErrorRoutingTest {
    private MockMvc mockMvcThrowing(RuntimeException failure) {
        LsTrDispatcher dispatcher = new LsTrDispatcher(List.of(new ThrowingHandler(() -> failure)));
        return MockMvcBuilders.standaloneSetup(new LsController(dispatcher))
                .setControllerAdvice(new LsErrorAdvice(new LsErrorMapper()))
                .build();
    }

    private static final String BODY = "{\"t9999InBlock\": {}}";

    @Test
    void routesACoreExceptionToTheLsEnvelope() throws Exception {
        mockMvcThrowing(new CompletionException(
                new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다")))
                .perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.rsp_cd").value("40410"))
                .andExpect(jsonPath("$.rsp_msg").value("주문을 찾을 수 없습니다"))
                .andExpect(jsonPath("$.rsp_detail").doesNotExist());
    }

    @Test
    void routesAnIllegalTransitionToConflict() throws Exception {
        mockMvcThrowing(new CompletionException(
                new IllegalOrderTransitionException(OrderState.FILLED, "취소")))
                .perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.rsp_cd").value("40900"));
    }

    @Test
    void routesAnInvariantBreachToInternalServerErrorWithoutLeakingTheClassName() throws Exception {
        mockMvcThrowing(new CompletionException(
                new IllegalStateException("계좌 원장 불변식이 깨졌습니다")))
                .perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.rsp_cd").value("50000"))
                .andExpect(jsonPath("$.rsp_msg").value("목 서버 내부 오류입니다"))
                .andExpect(jsonPath("$.rsp_detail").doesNotExist());
    }

    @Test
    void routesAnUnknownTrToTheLsEnvelope() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LsController(new LsTrDispatcher(List.of())))
                .setControllerAdvice(new LsErrorAdvice(new LsErrorMapper()))
                .build();

        mockMvc.perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"t0000InBlock\": {}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.rsp_cd").value("40400"));
    }

    @Test
    void routesARequestValidationFailureToTheLsEnvelope() throws Exception {
        mockMvcThrowing(new LsRequestException("IsuNo 값이 필요합니다"))
                .perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rsp_cd").value("40000"));
    }

    /**
     * TODO(ADAPTER-04): Spring이 Controller보다 먼저 처리하는 예외는 아직 LS 봉투를 타지 않는다.
     * 이 테스트는 그 공백을 눈에 보이게 고정한다. {@code ResponseEntityExceptionHandler}를
     * 상속해 덮으면 이 단언을 rsp_cd 검증으로 바꾼다.
     */
    @Test
    void frameworkLevelFailuresDoNotCarryTheLsEnvelopeYet() throws Exception {
        mockMvcThrowing(new LsRequestException("사용되지 않음"))
                .perform(post("/stock/order").contentType(MediaType.APPLICATION_JSON).content("{ 깨진 JSON"))
                .andExpect(jsonPath("$.rsp_cd").doesNotExist());
    }

    private record ThrowingHandler(Supplier<RuntimeException> failure) implements TrHandler {
        @Override
        public String trCode() {
            return "t9999";
        }

        @Override
        public Map<String, Object> handle(JsonNode inBlock) {
            throw failure.get();
        }
    }
}
