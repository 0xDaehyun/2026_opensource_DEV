package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.OrderResult;
import io.github.stockmock.core.engine.PlaceOrder;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelOrderHandlerTest {
    private static final Instant START = Instant.parse("2026-01-02T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cancelsAllRemainingQuantityOfAnAcceptedOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);

            Map<String, Object> response = handler.handle(request(accepted.orderId()));

            assertThat(response).containsEntry("rsp_cd", "00000");
            assertRow(response, accepted.orderId(), "CANCELLED", 100);
        }
    }

    @Test
    void cancelsOnlyTheUnfilledSeventyOutOfAHundredShares() throws Exception {
        try (SimulationEngine engine = headlessEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            engine.awaitIdle().join();

            Map<String, Object> response = handler.handle(request(accepted.orderId()));

            assertRow(response, accepted.orderId(), "CANCELLED", 70);
        }
    }

    @Test
    void rejectsCancellingAFilledOrder() throws Exception {
        try (SimulationEngine engine = headlessEngine(1.0)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            engine.awaitIdle().join();

            Map<String, Object> response = handler.handle(request(accepted.orderId()));

            assertThat(response).containsEntry("rsp_cd", "40002");
        }
    }

    @Test
    void rejectsCancellingAnAlreadyCancelledOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            handler.handle(request(accepted.orderId()));

            Map<String, Object> response = handler.handle(request(accepted.orderId()));

            assertThat(response).containsEntry("rsp_cd", "40002");
        }
    }

    @Test
    void rejectsAMissingOriginalOrderNumber() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            JsonNode inBlock = objectMapper.readTree("{}");

            assertThatThrownBy(() -> handler.handle(inBlock))
                    .isInstanceOf(LsRequestException.class);
        }
    }

    @Test
    void returnsAnOrderNotFoundEnvelopeForAnUnknownOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);

            Map<String, Object> response = handler.handle(request("ORD-999999"));

            assertThat(response).containsEntry("rsp_cd", "40401");
        }
    }

    private OrderResult place(SimulationEngine engine) {
        return engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
    }

    private JsonNode request(String orderId) throws Exception {
        return objectMapper.readTree("{\"OrgOrdNo\": \"" + orderId + "\"}");
    }

    @SuppressWarnings("unchecked")
    private void assertRow(Map<String, Object> response, String orderId, String state, long cancelledQty) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.get("CSPAT00801OutBlock1");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("OrgOrdNo")).isEqualTo(orderId);
        assertThat(row.get("OrdNo")).isEqualTo(orderId);
        assertThat(row.get("OrdStat")).isEqualTo(state);
        assertThat(row.get("CancQty")).isEqualTo(cancelledQty);
    }

    private SimulationEngine headlessEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, fillRatio, Duration.ofSeconds(5));
    }

    private SimulationEngine attachedEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.attached(START), 10_000_000, fillRatio, Duration.ofHours(1));
    }
}
