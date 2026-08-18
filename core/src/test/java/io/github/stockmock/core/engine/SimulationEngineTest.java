package io.github.stockmock.core.engine;

import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.event.EventRecord;
import io.github.stockmock.core.order.OrderState;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationEngineTest {
    private static final Instant START = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void headlessEngineAcceptsAndPartiallyFillsDeterministically() {
        List<EventRecord> first = runOnce();
        List<EventRecord> second = runOnce();

        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(EventRecord::type)
                .containsExactly("ORDER_ACCEPTED", "PARTIAL_FILL");
    }

    @Test
    void everyQueryRunsThroughTheQueueAndReturnsConsistentSnapshot() {
        try (SimulationEngine engine = engine()) {
            engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();

            AccountView account = engine.query(new AccountQuery()).join();

            assertThat(account.cash()).isEqualTo(3_000_000);
            assertThat(account.lockedCash()).isEqualTo(4_900_000);
            assertThat(account.positions().get("005930").quantity()).isEqualTo(30);
        }
    }

    @Test
    void queriesAnAcceptedOrder() {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            OrderResult accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();

            assertThat(order.state()).isEqualTo(OrderState.ACCEPTED);
            assertThat(order.quantity()).isEqualTo(100);
            assertThat(order.filledQuantity()).isZero();
            assertThat(order.remainingQuantity()).isEqualTo(100);
        }
    }

    @Test
    void queriesAPartiallyFilledOrder() {
        try (SimulationEngine engine = headlessEngine(0.3)) {
            OrderResult accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();

            assertThat(order.orderId()).isEqualTo("ORD-000001");
            assertThat(order.clientOrderId()).isEqualTo("CLIENT-1");
            assertThat(order.symbol()).isEqualTo(new Symbol("005930"));
            assertThat(order.side()).isEqualTo(Side.BUY);
            assertThat(order.state()).isEqualTo(OrderState.PARTIALLY_FILLED);
            assertThat(order.quantity()).isEqualTo(100);
            assertThat(order.filledQuantity()).isEqualTo(30);
            assertThat(order.remainingQuantity()).isEqualTo(70);
            assertThat(order.price()).isEqualTo(70_000);
        }
    }

    @Test
    void queriesAFilledOrder() {
        try (SimulationEngine engine = headlessEngine(1.0)) {
            OrderResult accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();

            assertThat(order.state()).isEqualTo(OrderState.FILLED);
            assertThat(order.filledQuantity()).isEqualTo(100);
            assertThat(order.remainingQuantity()).isZero();
        }
    }

    @Test
    void queriesACancelledOrder() {
        try (SimulationEngine engine = attachedEngine(0.3)) {
            OrderResult accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.cancel(new CancelOrder("CANCEL-1", accepted.orderId(), 0)).join();

            OrderView order = engine.query(new OrderQuery(accepted.orderId())).join();

            assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
            assertThat(order.filledQuantity()).isZero();
            assertThat(order.remainingQuantity()).isEqualTo(100);
        }
    }

    @Test
    void failsAnUnknownOrderQueryWithoutStoppingTheEngine() {
        try (SimulationEngine engine = engine()) {
            assertThatThrownBy(() -> engine.query(new OrderQuery("ORD-999999")).join())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("주문을 찾을 수 없습니다");

            assertThat(engine.query(new AccountQuery()).join().cash()).isEqualTo(10_000_000);
        }
    }

    @Test
    void rejectsABlankOrderId() {
        assertThatThrownBy(() -> new OrderQuery(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("orderId가 필요합니다");
    }

    private List<EventRecord> runOnce() {
        try (SimulationEngine engine = engine()) {
            engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();
            return engine.events().join();
        }
    }

    private SimulationEngine engine() {
        return headlessEngine(0.3);
    }

    private SimulationEngine headlessEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, fillRatio,
                Duration.ofSeconds(5));
    }

    private SimulationEngine attachedEngine(double fillRatio) {
        return new SimulationEngine(VirtualClock.attached(START), 10_000_000, fillRatio,
                Duration.ofHours(1));
    }
}
