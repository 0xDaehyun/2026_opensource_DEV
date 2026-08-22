package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.CancelOrder;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.OrderResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LS 미체결 주문 전량 취소 handler다.
 *
 * <p>MVP는 부분 취소를 지원하지 않으므로 {@link CancelOrder#qty()}는 항상 0(전량)으로
 * 보낸다. {@code trCode}와 OutBlock 필드 이름({@code OrgOrdNo}, {@code CancQty} 등)은
 * LS 공식 콘솔로 아직 확인되지 않은 임시 값이다. 공식 fixture를 확보하면 값을 교체한다.</p>
 */
@Component
final class CancelOrderHandler implements TrHandler {
    private final EnginePort engine;
    private final LsErrorMapper errorMapper = new LsErrorMapper();
    private final AtomicLong clientSequence = new AtomicLong();

    CancelOrderHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "CSPAT00801"; // TODO(ADAPTER-02): 공식 콘솔에서 취소 TR을 최종 확인한다.
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        if (inBlock == null || !inBlock.isObject()) {
            throw new LsRequestException("CSPAT00801InBlock1이 필요합니다");
        }
        String targetOrderId = requiredText(inBlock, "OrgOrdNo");
        String clientOrderId = text(inBlock, "clientOrderId", "ls-cancel-" + clientSequence.incrementAndGet());

        OrderResult result;
        try {
            result = engine.cancel(new CancelOrder(clientOrderId, targetOrderId, 0)).join();
        } catch (CompletionException exception) {
            return cancelFailureEnvelope(exception);
        }

        long cancelledQuantity = result.quantity() - result.filledQuantity();

        Map<String, Object> outBlock = new LinkedHashMap<>();
        outBlock.put("OrgOrdNo", targetOrderId);
        outBlock.put("OrdNo", result.orderId());
        outBlock.put("CancQty", cancelledQuantity);
        outBlock.put("OrdStat", result.state().name());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", "00000");
        response.put("rsp_msg", "취소가 완료되었습니다");
        response.put("CSPAT00801OutBlock1", java.util.List.of(outBlock));
        return response;
    }

    private Map<String, Object> cancelFailureEnvelope(CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof IllegalArgumentException) {
            return errorMapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, cause.getMessage());
        }
        if (cause instanceof IllegalStateException) {
            return errorMapper.toEnvelope(LsErrorType.ILLEGAL_ORDER_STATE, cause.getMessage());
        }
        throw exception;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) {
            throw new LsRequestException(field + " 값이 필요합니다");
        }
        return value;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }
}
