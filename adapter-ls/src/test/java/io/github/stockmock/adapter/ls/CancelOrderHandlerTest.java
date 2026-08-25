package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.OrderQuery;
import io.github.stockmock.core.engine.OrderResult;
import io.github.stockmock.core.engine.OrderView;
import io.github.stockmock.core.engine.PlaceOrder;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.order.IllegalOrderTransitionException;
import io.github.stockmock.core.order.OrderState;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelOrderHandlerTest {
    private static final Instant START = Instant.parse("2026-01-02T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cancelsAllRemainingQuantityOfAnAcceptedOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            LsOrderNumberRegistry orderNumbers = new LsOrderNumberRegistry();
            CancelOrderHandler handler = new CancelOrderHandler(engine, orderNumbers);
            OrderResult accepted = place(engine);
            long lsOrderNumber = orderNumbers.register(accepted.orderId());

            Map<String, Object> response = handler.handle(request(lsOrderNumber, "A005930", 100));

            assertThat(response).containsEntry("rsp_cd", "00156");
            assertEchoAndConfirmation(response, lsOrderNumber, "A005930", 100);

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();
            assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.remainingQuantity()).isEqualTo(100);
        }
    }

    @Test
    void cancelsOnlyTheUnfilledSeventyOutOfAHundredShares() throws Exception {
        try (SimulationEngine engine = headlessEngine(0.3)) {
            LsOrderNumberRegistry orderNumbers = new LsOrderNumberRegistry();
            CancelOrderHandler handler = new CancelOrderHandler(engine, orderNumbers);
            OrderResult accepted = place(engine);
            long lsOrderNumber = orderNumbers.register(accepted.orderId());
            engine.awaitIdle().join();

            Map<String, Object> response = handler.handle(request(lsOrderNumber, "A005930", 70));

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
            LsOrderNumberRegistry orderNumbers = new LsOrderNumberRegistry();
            CancelOrderHandler handler = new CancelOrderHandler(engine, orderNumbers);
            OrderResult accepted = place(engine);
            long lsOrderNumber = orderNumbers.register(accepted.orderId());
            engine.awaitIdle().join();

            assertThatThrownBy(() -> handler.handle(request(lsOrderNumber, "A005930", 100)))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalOrderTransitionException.class);
        }
    }

    @Test
    void rejectsCancellingAnAlreadyCancelledOrder() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            LsOrderNumberRegistry orderNumbers = new LsOrderNumberRegistry();
            CancelOrderHandler handler = new CancelOrderHandler(engine, orderNumbers);
            OrderResult accepted = place(engine);
            long lsOrderNumber = orderNumbers.register(accepted.orderId());
            handler.handle(request(lsOrderNumber, "A005930", 100));

            assertThatThrownBy(() -> handler.handle(request(lsOrderNumber, "A005930", 100)))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalOrderTransitionException.class);
        }
    }

    @Test
    void rejectsAMissingOriginalOrderNumber() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine, new LsOrderNumberRegistry());
            JsonNode inBlock = objectMapper.readTree("{}");

            assertThatThrownBy(() -> handler.handle(inBlock))
                    .isInstanceOf(LsRequestException.class);
        }
    }

    @Test
    void propagatesOrderNotFoundForAnUnknownLsOrderNumber() throws Exception {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            CancelOrderHandler handler = new CancelOrderHandler(engine, new LsOrderNumberRegistry());

            assertThatThrownBy(() -> handler.handle(request(999_999, "A005930", 100)))
                    .isInstanceOfSatisfying(CoreException.class,
                            exception -> assertThat(exception.code()).isEqualTo(CoreErrorCode.ORDER_NOT_FOUND));
        }
    }

    private OrderResult place(SimulationEngine engine) {
        return engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
    }

    private JsonNode request(long orderId, String isuNo, long ordQty) throws Exception {
        return objectMapper.readTree(
                "{\"OrgOrdNo\": " + orderId + ", \"IsuNo\": \"" + isuNo + "\", \"OrdQty\": " + ordQty + "}");
    }

    @SuppressWarnings("unchecked")
    private void assertEchoAndConfirmation(Map<String, Object> response, long orderId, String isuNo, long ordQty) {
        Map<String, Object> outBlock1 = (Map<String, Object>) response.get("CSPAT00801OutBlock1");
        assertThat(outBlock1.get("OrgOrdNo")).isEqualTo(orderId);
        assertThat(outBlock1.get("IsuNo")).isEqualTo(isuNo);
        assertThat(outBlock1.get("OrdQty")).isEqualTo(ordQty);

        Map<String, Object> outBlock2 = (Map<String, Object>) response.get("CSPAT00801OutBlock2");
        assertThat(outBlock2.get("OrdNo")).isInstanceOf(Long.class).isNotEqualTo(orderId);
        assertThat(outBlock2.get("PrntOrdNo")).isEqualTo(orderId);
    }

    private SimulationEngine headlessEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, fillRatio, Duration.ofSeconds(5));
    }

    private SimulationEngine attachedEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.attached(START), 10_000_000, fillRatio, Duration.ofHours(1));
    }
}
