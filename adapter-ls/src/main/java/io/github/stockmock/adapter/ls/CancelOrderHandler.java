package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.CancelOrder;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.OrderResult;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LS 미체결 주문 전량 취소 handler다.
 *
 * <p>MVP는 부분 취소를 지원하지 않으므로 {@link CancelOrder#qty()}는 항상 0(전량)으로
 * 보낸다. 공식 응답({@code fixtures/cancel-order.json})은 취소 자체가 새 접수 이벤트인
 * 것처럼 {@code OrdNo}(취소 접수번호)와 {@code PrntOrdNo}(원주문번호)를 구분해서 돌려주지만,
 * core는 취소로 새 주문번호를 만들지 않고 원주문 상태만 바꾼다. 그래서 이 handler는
 * {@code OrdNo}와 {@code PrntOrdNo}에 같은 core 주문번호를 넣는다(알려진 gap).</p>
 */
@Component
final class CancelOrderHandler implements TrHandler {
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("HHmmssSSS")
            .withZone(ZoneId.of("Asia/Seoul"));
    private final EnginePort engine;
    private final LsErrorMapper errorMapper = new LsErrorMapper();
    private final AtomicLong clientSequence = new AtomicLong();

    CancelOrderHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "CSPAT00801";
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        if (inBlock == null || !inBlock.isObject()) {
            throw new LsRequestException("CSPAT00801InBlock1이 필요합니다");
        }
        String targetOrderId = requiredText(inBlock, "OrgOrdNo");
        String isuNo = text(inBlock, "IsuNo", "");
        long ordQty = optionalLong(inBlock, "OrdQty");
        String clientOrderId = text(inBlock, "clientOrderId", "ls-cancel-" + clientSequence.incrementAndGet());

        OrderResult result;
        try {
            result = engine.cancel(new CancelOrder(clientOrderId, targetOrderId, 0)).join();
        } catch (CompletionException exception) {
            return cancelFailureEnvelope(exception);
        }

        Map<String, Object> outBlock1 = new LinkedHashMap<>();
        outBlock1.put("RecCnt", 1);
        outBlock1.put("AcntNo", text(inBlock, "AcntNo", "00000000000"));
        outBlock1.put("OrgOrdNo", targetOrderId);
        outBlock1.put("IsuNo", isuNo);
        outBlock1.put("OrdQty", ordQty);

        Map<String, Object> outBlock2 = new LinkedHashMap<>();
        outBlock2.put("RecCnt", 1);
        outBlock2.put("OrdNo", result.orderId());
        outBlock2.put("PrntOrdNo", targetOrderId);
        outBlock2.put("OrdTime", ORDER_TIME.format(java.time.Instant.EPOCH));
        outBlock2.put("OrdMktCode", "10");
        outBlock2.put("OrdPtnCode", "02");

        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("CSPAT00801OutBlock1", outBlock1);
        blocks.put("CSPAT00801OutBlock2", outBlock2);
        return envelope("00156", "취소주문이 완료되었습니다.", blocks);
    }

    private Map<String, Object> envelope(String code, String message, Map<String, Object> blocks) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", code);
        response.put("rsp_msg", message);
        response.putAll(blocks);
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

    private long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asLong();
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }
}
