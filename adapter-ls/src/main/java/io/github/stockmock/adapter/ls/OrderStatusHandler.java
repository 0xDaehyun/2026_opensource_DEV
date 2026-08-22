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
 * <p>{@code trCode}와 OutBlock 필드 이름({@code OrdNo}, {@code OrdStat} 등)은
 * LS 공식 콘솔로 아직 확인되지 않은 임시 값이다. 공식 fixture를 확보하면 값을 교체한다.</p>
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
        return "t0425"; // TODO(ADAPTER-01): 공식 콘솔에서 MVP 조회 TR을 최종 확인한다.
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
        row.put("OrdNo", order.orderId());
        row.put("IsuNo", order.symbol().value());
        row.put("BnsTpCode", order.side() == Side.BUY ? "2" : "1");
        row.put("OrdStat", order.state().name());
        row.put("OrdQty", order.quantity());
        row.put("ExecQty", order.filledQuantity());
        row.put("UnastQty", order.remainingQuantity());
        row.put("OrdPrc", order.price());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", "00000");
        response.put("rsp_msg", "조회완료");
        response.put("t0425OutBlock", List.of(row));
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
