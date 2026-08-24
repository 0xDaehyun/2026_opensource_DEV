package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.CancelOrder;
import io.github.stockmock.core.engine.EnginePort;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LS 미체결 주문 전량 취소 handler다.
 *
 * <p>MVP는 부분 취소를 지원하지 않으므로 {@link CancelOrder#qty()}는 항상 0(전량)으로
 * 보낸다. 외부에는 별도의 숫자 취소 접수번호를 발급하고, {@code PrntOrdNo}에는 원주문의
 * LS 주문번호를 반환한다.</p>
 */
@Component
final class CancelOrderHandler implements TrHandler {
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("HHmmssSSS")
            .withZone(ZoneId.of("Asia/Seoul"));
    private final EnginePort engine;
    private final LsOrderNumberRegistry orderNumbers;
    private final AtomicLong clientSequence = new AtomicLong();

    CancelOrderHandler(EnginePort engine, LsOrderNumberRegistry orderNumbers) {
        this.engine = engine;
        this.orderNumbers = orderNumbers;
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
        long originalLsOrderNumber = requiredLong(inBlock, "OrgOrdNo");
        String targetOrderId = orderNumbers.coreOrderId(originalLsOrderNumber);
        String isuNo = text(inBlock, "IsuNo", "");
        long ordQty = requiredLong(inBlock, "OrdQty");
        String clientOrderId = text(inBlock, "clientOrderId", "ls-cancel-" + clientSequence.incrementAndGet());

        engine.cancel(new CancelOrder(clientOrderId, targetOrderId, 0)).join();
        long cancellationOrderNumber = orderNumbers.issueReceiptNumber();

        Map<String, Object> outBlock1 = new LinkedHashMap<>();
        outBlock1.put("RecCnt", 1);
        outBlock1.put("AcntNo", text(inBlock, "AcntNo", "00000000000"));
        outBlock1.put("OrgOrdNo", originalLsOrderNumber);
        outBlock1.put("IsuNo", isuNo);
        outBlock1.put("OrdQty", ordQty);

        Map<String, Object> outBlock2 = new LinkedHashMap<>();
        outBlock2.put("RecCnt", 1);
        outBlock2.put("OrdNo", cancellationOrderNumber);
        outBlock2.put("PrntOrdNo", originalLsOrderNumber);
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

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) {
            throw new LsRequestException(field + " 값이 필요합니다");
        }
        return value;
    }

    private long requiredLong(JsonNode node, String field) {
        String value = requiredText(node, field);
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new LsRequestException(field + " 값은 0보다 큰 정수여야 합니다");
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }
}
