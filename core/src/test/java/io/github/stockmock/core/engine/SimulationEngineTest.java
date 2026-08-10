package io.github.stockmock.core.engine;

import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.event.EventRecord;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<EventRecord> runOnce() {
        try (SimulationEngine engine = engine()) {
            engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();
            return engine.events().join();
        }
    }

    private SimulationEngine engine() {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, 0.3, Duration.ofSeconds(5));
    }
}
