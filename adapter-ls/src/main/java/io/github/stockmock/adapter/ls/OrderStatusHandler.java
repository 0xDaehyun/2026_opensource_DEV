package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.OrderQuery;
import io.github.stockmock.core.engine.OrderView;
import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.Side;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * LS 주문 상태·미체결 조회 handler다.
 *
 * <p><b>알려진 범위 축소(MVP):</b> 공식 t0425는 {@code expcode}(종목코드) 기준으로 여러 주문을
 * 목록 조회하고 {@code cts_ordno}로 페이지네이션한다({@code fixtures/order-status-query.json}
 * 참고). core의 {@code EnginePort.query(OrderQuery)}는 주문번호 1건 단건 조회만 지원하므로,
 * 이 handler는 의도적으로 입력을 {@code OrdNo}(단일 주문번호) 하나로 단순화했다. 목록 조회가
 * 필요해지면 core에 새 조회 기능을 요청해야 한다.</p>
 *
 * <p>출력 모양({@code t0425OutBlock1} 배열 + {@code t0425OutBlock} 합계)과 필드 이름은
 * fixture로 확인했지만, 다음은 여전히 알려진 gap이다:</p>
 * <ul>
 *   <li>{@code ordno}는 공식 응답에서 숫자지만 core 주문번호가 문자열이라 그대로 노출한다.</li>
 *   <li>{@code status}는 공식 응답에서 "접수"만 확인했다. 체결·일부체결·취소의 실제 텍스트는
 *       미확인이라 core {@code OrderState} enum 이름을 그대로 쓴다.</li>
 * </ul>
 */
@Component
final class OrderStatusHandler implements TrHandler {
    private final EnginePort engine;
    private final LsErrorMapper errorMapper = new LsErrorMapper();

    OrderStatusHandler(EnginePort engine) {
        this.engine = engine;
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
        String orderId = requiredText(inBlock, "OrdNo");

        OrderView order;
        try {
            order = engine.query(new OrderQuery(orderId)).join();
        } catch (CompletionException exception) {
            return orderNotFoundEnvelope(exception);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ordno", order.orderId());
        row.put("expcode", order.symbol().value());
        row.put("medosu", order.side() == Side.BUY ? "매수" : "매도");
        row.put("qty", order.quantity());
        row.put("cheqty", order.filledQuantity());
        row.put("ordrem", order.remainingQuantity());
        row.put("price", order.price());
        row.put("status", order.state().name());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tqty", order.quantity());
        summary.put("tcheqty", order.filledQuantity());
        summary.put("tordrem", order.remainingQuantity());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", "00000");
        response.put("rsp_msg", "조회가 완료되었습니다.");
        response.put("t0425OutBlock1", List.of(row));
        response.put("t0425OutBlock", summary);
        return response;
    }

    private Map<String, Object> orderNotFoundEnvelope(CompletionException exception) {
        if (exception.getCause() instanceof CoreException coreFailure
                && coreFailure.code() == CoreErrorCode.ORDER_NOT_FOUND) {
            return errorMapper.toEnvelope(LsErrorType.ORDER_NOT_FOUND, coreFailure.getMessage());
        }
        throw exception;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new LsRequestException(field + " 값이 필요합니다");
        }
        return value.asText();
    }
}
