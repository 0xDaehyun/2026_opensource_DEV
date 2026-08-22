package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.PlaceOrder;
import io.github.stockmock.core.engine.OrderResult;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

@Component
final class CashBuyOrderHandler implements TrHandler {
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("HHmmssSSS")
            .withZone(ZoneId.of("Asia/Seoul"));
    private final EnginePort engine;
    private final AtomicLong clientSequence = new AtomicLong();

    CashBuyOrderHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "CSPAT00601";
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        if (inBlock == null || !inBlock.isObject()) {
            throw new LsRequestException("CSPAT00601InBlock1이 필요합니다");
        }
        String sideCode = text(inBlock, "BnsTpCode", "2");
        if (!"2".equals(sideCode)) {
            throw new LsRequestException("M1에서는 현물 매수만 지원합니다");
        }
        String rawSymbol = requiredText(inBlock, "IsuNo");
        String symbol = stripMarketPrefix(rawSymbol);
        long quantity = requiredLong(inBlock, "OrdQty");
        long price = requiredLong(inBlock, "OrdPrc");
        String clientOrderId = text(inBlock, "clientOrderId", "ls-" + clientSequence.incrementAndGet());

        OrderResult result;
        try {
            result = engine.submit(new PlaceOrder(clientOrderId, new Symbol(symbol), Side.BUY, quantity, price)).join();
        } catch (CompletionException exception) {
            throw new LsRequestException(rootMessage(exception));
        }
        if (!result.accepted()) {
            return envelope("10001", result.reason(), Map.of());
        }

        Map<String, Object> outBlock1 = new LinkedHashMap<>();
        outBlock1.put("RecCnt", 1);
        outBlock1.put("AcntNo", text(inBlock, "AcntNo", "00000000000"));
        outBlock1.put("IsuNo", rawSymbol);
        outBlock1.put("OrdQty", quantity);
        outBlock1.put("OrdPrc", "%.2f".formatted((double) price));
        outBlock1.put("BnsTpCode", sideCode);

        Map<String, Object> outBlock2 = new LinkedHashMap<>();
        outBlock2.put("RecCnt", 1);
        // TODO(ADAPTER-05): 공식 응답은 OrdNo가 숫자(예: 32004)지만, core 주문번호는 "ORD-000001" 형태의
        // 문자열이다. 지금은 core 형식을 그대로 노출한다 — LS 숫자 주문번호로 변환하는 매핑 계층이
        // 필요한지는 OrderStatusHandler/CancelOrderHandler의 입력 형식과 함께 팀 논의가 필요하다.
        outBlock2.put("OrdNo", result.orderId());
        outBlock2.put("OrdTime", ORDER_TIME.format(java.time.Instant.EPOCH));
        outBlock2.put("OrdMktCode", "10");
        outBlock2.put("OrdPtnCode", "00");

        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("CSPAT00601OutBlock1", outBlock1);
        blocks.put("CSPAT00601OutBlock2", outBlock2);
        return envelope("00040", "매수 주문이 완료되었습니다", blocks);
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

    /**
     * 공식 fixture의 {@code IsuNo}는 {@code "A005930"}처럼 시장 접두문자가 붙는다.
     * core의 {@link Symbol}은 접두문자 없는 6자리 종목코드를 기대하므로 여기서 벗겨낸다.
     * OutBlock1의 echo 필드는 접두문자가 붙은 원본 입력을 그대로 돌려준다.
     */
    private String stripMarketPrefix(String isuNo) {
        return isuNo.length() == 7 && Character.isLetter(isuNo.charAt(0)) ? isuNo.substring(1) : isuNo;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
