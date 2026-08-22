package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.OrderQuery;
import io.github.stockmock.core.engine.OrderResult;
import io.github.stockmock.core.engine.OrderView;
import io.github.stockmock.core.engine.PlaceOrder;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.order.OrderState;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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

            Map<String, Object> response = handler.handle(request(accepted.orderId(), "005930", 100));

            assertThat(response).containsEntry("rsp_cd", "00156");
            assertEchoAndConfirmation(response, accepted.orderId(), "005930", 100);

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();
            assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.remainingQuantity()).isEqualTo(100);
        }
    }

    @Test
    void cancelsOnlyTheUnfilledSeventyOutOfAHundredShares() throws Exception {
        try (SimulationEngine engine = headlessEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            engine.awaitIdle().join();

            Map<String, Object> response = handler.handle(request(accepted.orderId(), "005930", 70));

            assertThat(response).containsEntry("rsp_cd", "00156");

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();
            assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.filledQuantity()).isEqualTo(30);
            assertThat(order.remainingQuantity()).isEqualTo(70);
        }
    }

    @Test
    void rejectsCancellingAFilledOrder() throws Exception {
        try (SimulationEngine engine = headlessEngine(1.0)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            engine.awaitIdle().join();

            Map<String, Object> response = handler.handle(request(accepted.orderId(), "005930", 100));

            assertThat(response).containsEntry("rsp_cd", "40002");
        }
    }

    @Test
    void rejectsCancellingAnAlreadyCancelledOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            OrderResult accepted = place(engine);
            handler.handle(request(accepted.orderId(), "005930", 100));

            Map<String, Object> response = handler.handle(request(accepted.orderId(), "005930", 100));

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

            Map<String, Object> response = handler.handle(request("ORD-999999", "005930", 100));

            assertThat(response).containsEntry("rsp_cd", "40401");
        }
    }

    private OrderResult place(SimulationEngine engine) {
        return engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
    }

    private JsonNode request(String orderId, String isuNo, long ordQty) throws Exception {
        return objectMapper.readTree(
                "{\"OrgOrdNo\": \"" + orderId + "\", \"IsuNo\": \"" + isuNo + "\", \"OrdQty\": " + ordQty + "}");
    }

    @SuppressWarnings("unchecked")
    private void assertEchoAndConfirmation(Map<String, Object> response, String orderId, String isuNo, long ordQty) {
        Map<String, Object> outBlock1 = (Map<String, Object>) response.get("CSPAT00801OutBlock1");
        assertThat(outBlock1.get("OrgOrdNo")).isEqualTo(orderId);
        assertThat(outBlock1.get("IsuNo")).isEqualTo(isuNo);
        assertThat(outBlock1.get("OrdQty")).isEqualTo(ordQty);

        Map<String, Object> outBlock2 = (Map<String, Object>) response.get("CSPAT00801OutBlock2");
        assertThat(outBlock2.get("OrdNo")).isEqualTo(orderId);
        assertThat(outBlock2.get("PrntOrdNo")).isEqualTo(orderId);
    }

    private SimulationEngine headlessEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, fillRatio, Duration.ofSeconds(5));
    }

    private SimulationEngine attachedEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.attached(START), 10_000_000, fillRatio, Duration.ofHours(1));
    }
}
