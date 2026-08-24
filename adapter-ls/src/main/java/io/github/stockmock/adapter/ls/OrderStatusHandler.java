package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.OrderQuery;
import io.github.stockmock.core.engine.OrderView;
import io.github.stockmock.core.order.OrderState;
import io.github.stockmock.core.order.Side;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LS 주문 상태·미체결 조회 handler다.
 *
 * <p>공식 입력인 {@code expcode}, {@code chegb}, {@code medosu}, {@code sortgb},
 * {@code cts_ordno}를 받는다. core는 단건 조회만 제공하므로 adapter가 자신이 발급한 LS
 * 주문번호 목록을 순회하며 각 주문을 {@link EnginePort#query(OrderQuery)}로 조회한다.</p>
 */
@Component
final class OrderStatusHandler implements TrHandler {
    private final EnginePort engine;
    private final LsOrderNumberRegistry orderNumbers;

    OrderStatusHandler(EnginePort engine, LsOrderNumberRegistry orderNumbers) {
        this.engine = engine;
        this.orderNumbers = orderNumbers;
    }

    @Override
    public String trCode() {
        return "t0425";
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        if (inBlock == null || !inBlock.isObject()) {
            throw new LsRequestException("t0425InBlock이 필요합니다");
        }
        String symbol = text(inBlock, "expcode", "").trim();
        String executionCode = text(inBlock, "chegb", "0").trim();
        String sideCode = text(inBlock, "medosu", "0").trim();
        String sortCode = text(inBlock, "sortgb", "2").trim();
        long cursor = cursor(inBlock);

        validateCode(executionCode, "chegb", "0", "1", "2");
        validateCode(sideCode, "medosu", "0", "1", "2");
        validateCode(sortCode, "sortgb", "1", "2");

        List<ListedOrder> orders = new ArrayList<>();
        for (LsOrderNumberRegistry.OrderNumber number : orderNumbers.orders()) {
            OrderView order = engine.query(new OrderQuery(number.coreOrderId())).join();
            if (matches(order, number.lsOrderNumber(), symbol, executionCode, sideCode, sortCode, cursor)) {
                orders.add(new ListedOrder(number.lsOrderNumber(), order));
            }
        }
        Comparator<ListedOrder> byOrderNumber = Comparator.comparingLong(ListedOrder::lsOrderNumber);
        orders.sort("2".equals(sortCode) ? byOrderNumber.reversed() : byOrderNumber);

        List<Map<String, Object>> rows = orders.stream().map(this::rowOf).toList();
        long totalQuantity = orders.stream().mapToLong(item -> item.order().quantity()).sum();
        long totalFilled = orders.stream().mapToLong(item -> item.order().filledQuantity()).sum();
        long totalRemaining = orders.stream().mapToLong(item -> item.order().remainingQuantity()).sum();
        long totalAmount = orders.stream()
                .mapToLong(item -> item.order().filledQuantity() * item.order().price()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tcheqty", totalFilled);
        summary.put("tamt", totalAmount);
        summary.put("tqty", totalQuantity);
        summary.put("cmss", 0);
        summary.put("tmsamt", 0);
        summary.put("tax", 0);
        summary.put("tmdamt", totalAmount);
        summary.put("cts_ordno", "");
        summary.put("tordrem", totalRemaining);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", "00000");
        response.put("rsp_msg", "조회가 완료되었습니다.");
        response.put("t0425OutBlock1", rows);
        response.put("t0425OutBlock", summary);
        return response;
    }

    private Map<String, Object> rowOf(ListedOrder listed) {
        OrderView order = listed.order();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orgordno", 0);
        row.put("ordrem", order.remainingQuantity());
        row.put("cfmqty", 0);
        row.put("ordgb", "보통");
        row.put("cheqty", order.filledQuantity());
        row.put("orggb", "02");
        row.put("ordno", listed.lsOrderNumber());
        row.put("loandt", "");
        row.put("price", order.price());
        row.put("sysprocseq", 0);
        row.put("singb", "00");
        row.put("qty", order.quantity());
        row.put("hogagb", "00");
        row.put("expcode", order.symbol().value());
        row.put("medosu", order.side() == Side.BUY ? "매수" : "매도");
        row.put("cheprice", order.filledQuantity() == 0 ? 0 : order.price());
        row.put("ordtime", "090000000");
        row.put("ordermtd", "Mock");
        row.put("price1", order.price());
        row.put("status", statusText(order.state()));
        return row;
    }

    private boolean matches(OrderView order, long lsOrderNumber, String symbol, String executionCode,
            String sideCode, String sortCode, long cursor) {
        boolean symbolMatches = symbol.isEmpty() || order.symbol().value().equals(stripMarketPrefix(symbol));
        boolean executionMatches = switch (executionCode) {
            case "0" -> true;
            case "1" -> order.remainingQuantity() == 0;
            case "2" -> order.remainingQuantity() > 0;
            default -> false;
        };
        boolean sideMatches = "0".equals(sideCode)
                || ("1".equals(sideCode) && order.side() == Side.SELL)
                || ("2".equals(sideCode) && order.side() == Side.BUY);
        boolean cursorMatches = cursor == 0
                || ("2".equals(sortCode) ? lsOrderNumber < cursor : lsOrderNumber > cursor);
        return symbolMatches && executionMatches && sideMatches && cursorMatches;
    }

    private long cursor(JsonNode node) {
        String raw = text(node, "cts_ordno", "").trim();
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(raw);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new LsRequestException("cts_ordno 값은 비어 있거나 양의 주문번호여야 합니다");
        }
    }

    private void validateCode(String value, String field, String... allowed) {
        if (List.of(allowed).contains(value)) {
            return;
        }
        throw new LsRequestException(field + " 값이 올바르지 않습니다");
    }

    private String statusText(OrderState state) {
        return switch (state) {
            case ACCEPTED -> "접수";
            case PARTIALLY_FILLED -> "일부체결";
            case FILLED -> "체결";
            case CANCELLED -> "취소";
            case REJECTED -> "거부";
        };
    }

    private String stripMarketPrefix(String symbol) {
        return symbol.length() == 7 && Character.isLetter(symbol.charAt(0)) ? symbol.substring(1) : symbol;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private record ListedOrder(long lsOrderNumber, OrderView order) {
    }
}
