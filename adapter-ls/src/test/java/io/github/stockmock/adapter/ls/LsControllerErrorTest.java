package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO(ADAPTER-04): 모든 오류가 하나의 LS 봉투로 나가는지 확인한다.
 *
 * <p>adapter-ls에는 Spring test와 MockMvc 의존성이 없으므로 {@code @ExceptionHandler}
 * 메서드를 직접 호출해 검증한다. 전체 HTTP 왕복은 app 모듈의 통합 테스트가 담당한다.</p>
 */
class LsControllerErrorTest {
    private final LsController controller = new LsController(
            new LsTrDispatcher(List.of()), new LsErrorMapper());

    @Test
    void mapsACoreExceptionByItsCodeNotItsMessage() {
        ResponseEntity<Map<String, Object>> response =
                controller.coreFailure(new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("rsp_cd", "40410")
                .containsEntry("rsp_detail", "주문을 찾을 수 없습니다");
    }

    @Test
    void unwrapsACoreExceptionWrappedByCompletableFutureJoin() {
        CompletionException wrapped = new CompletionException(
                new CoreException(CoreErrorCode.ILLEGAL_ORDER_STATE, "FILLED 상태에서는 취소할 수 없습니다"));

        ResponseEntity<Map<String, Object>> response = controller.coreFailure(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40900");
    }

    @Test
    void fallsBackToInvalidRequestWhenNoCoreExceptionIsFound() {
        ResponseEntity<Map<String, Object>> response =
                controller.coreFailure(new CompletionException(new IllegalStateException("알 수 없는 실패")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("rsp_cd", "40000");
    }

    @Test
    void keepsTheUnknownTrEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                controller.unknownTr(new UnknownTrException("지원하지 않는 TR입니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("rsp_cd", "40400")
                .containsEntry("rsp_detail", "지원하지 않는 TR입니다");
    }

    @Test
    void keepsTheBadRequestEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                controller.badRequest(new LsRequestException("IsuNo 값이 필요합니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("rsp_cd", "40000")
                .containsEntry("rsp_detail", "IsuNo 값이 필요합니다");
    }

    @Test
    void everyErrorResponseCarriesTheLsEnvelopeKeys() {
        List<ResponseEntity<Map<String, Object>>> responses = List.of(
                controller.coreFailure(new CoreException(CoreErrorCode.INSUFFICIENT_FUNDS, "증거금이 부족합니다")),
                controller.unknownTr(new UnknownTrException("지원하지 않는 TR입니다")),
                controller.badRequest(new LsRequestException("잘못된 요청입니다")));

        assertThat(responses).allSatisfy(response ->
                assertThat(response.getBody()).containsKeys("rsp_cd", "rsp_msg"));
    }
}
