package io.github.stockmock.app;

import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.account.PositionView;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.AccountQuery;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.event.EventRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 대시보드가 사용할 현재 엔진 상태를 읽기 전용 스냅샷으로 제공한다. */
@RestController
@RequestMapping("/mock/dashboard")
public final class DashboardController {
    private static final int RECENT_EVENT_LIMIT = 30;

    private final SimulationEngine engine;
    private final VirtualClock clock;
    private final double fillRatio;
    private final Duration fillDelay;

    public DashboardController(
            SimulationEngine engine,
            VirtualClock clock,
            @Value("${mock.fill.ratio:0.3}") double fillRatio,
            @Value("${mock.fill.delay:PT5S}") Duration fillDelay
    ) {
        this.engine = engine;
        this.clock = clock;
        this.fillRatio = fillRatio;
        this.fillDelay = fillDelay;
    }

    @GetMapping
    public DashboardSnapshot snapshot() {
        AccountView account = engine.query(new AccountQuery()).join();
        List<EventRecord> eventRecords = engine.events().join();
        List<OrderSummary> orders = latestOrders(eventRecords);

        return new DashboardSnapshot(
                "RUNNING",
                clock.now(),
                new ScenarioSummary("기본 부분체결", fillRatio, fillDelay),
                accountSummary(account),
                countOrders(orders),
                orders,
                recentEvents(eventRecords));
    }

    private AccountSummary accountSummary(AccountView account) {
        List<PositionSummary> positions = account.positions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> positionOf(entry.getKey(), entry.getValue()))
                .toList();
        return new AccountSummary(account.cash(), account.lockedCash(), positions);
    }

    private PositionSummary positionOf(String symbol, PositionView position) {
        return new PositionSummary(symbol, position.quantity());
    }

    private List<OrderSummary> latestOrders(List<EventRecord> events) {
        Map<String, OrderSummary> latestByOrder = new LinkedHashMap<>();
        for (EventRecord event : events) {
            if (event.orderId() == null || event.orderId().isBlank()) {
                continue;
            }
            OrderSummary previous = latestByOrder.get(event.orderId());
            Map<String, Object> payload = event.payload();
            latestByOrder.put(event.orderId(), new OrderSummary(
                    event.orderId(),
                    text(payload, "clientOrderId", previous == null ? "-" : previous.clientOrderId()),
                    text(payload, "symbol", previous == null ? "-" : previous.symbol()),
                    text(payload, "side", previous == null ? "-" : previous.side()),
                    number(payload, "quantity", previous == null ? 0 : previous.quantity()),
                    number(payload, "filledQuantity", previous == null ? 0 : previous.filledQuantity()),
                    number(payload, "remainingQuantity", previous == null ? 0 : previous.remainingQuantity()),
                    number(payload, "price", previous == null ? 0 : previous.price()),
                    text(payload, "state", stateFrom(event.type(), previous)),
                    text(payload, "reason", previous == null ? "" : previous.reason()),
                    event.virtualTime()));
        }
        return latestByOrder.values().stream()
                .sorted(Comparator.comparing(OrderSummary::updatedAt).reversed())
                .toList();
    }

    private String stateFrom(String eventType, OrderSummary previous) {
        return switch (eventType) {
            case "ORDER_ACCEPTED" -> "ACCEPTED";
            case "ORDER_REJECTED" -> "REJECTED";
            case "PARTIAL_FILL" -> "PARTIALLY_FILLED";
            case "FILL" -> "FILLED";
            case "ORDER_CANCELLED" -> "CANCELLED";
            default -> previous == null ? "UNKNOWN" : previous.state();
        };
    }

    private OrderCounts countOrders(List<OrderSummary> orders) {
        return new OrderCounts(
                orders.size(),
                count(orders, "ACCEPTED"),
                count(orders, "PARTIALLY_FILLED"),
                count(orders, "FILLED"),
                count(orders, "CANCELLED"),
                count(orders, "REJECTED"));
    }

    private long count(List<OrderSummary> orders, String state) {
        return orders.stream().filter(order -> state.equals(order.state())).count();
    }

    private List<EventSummary> recentEvents(List<EventRecord> events) {
        List<EventSummary> recent = new ArrayList<>();
        for (int index = events.size() - 1;
             index >= 0 && recent.size() < RECENT_EVENT_LIMIT;
             index--) {
            EventRecord event = events.get(index);
            recent.add(new EventSummary(event.seq(), event.virtualTime(), event.type(),
                    event.orderId(), event.payload()));
        }
        return List.copyOf(recent);
    }

    private String text(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null ? fallback : value.toString();
    }

    private long number(Map<String, Object> payload, String key, long fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    public record DashboardSnapshot(
            String serverStatus,
            Instant virtualTime,
            ScenarioSummary scenario,
            AccountSummary account,
            OrderCounts orderCounts,
            List<OrderSummary> orders,
            List<EventSummary> events
    ) {
    }

    public record ScenarioSummary(String name, double fillRatio, Duration fillDelay) {
    }

    public record AccountSummary(long cash, long lockedCash, List<PositionSummary> positions) {
    }

    public record PositionSummary(String symbol, long quantity) {
    }

    public record OrderCounts(
            long total,
            long accepted,
            long partiallyFilled,
            long filled,
            long cancelled,
            long rejected
    ) {
    }

    public record OrderSummary(
            String orderId,
            String clientOrderId,
            String symbol,
            String side,
            long quantity,
            long filledQuantity,
            long remainingQuantity,
            long price,
            String state,
            String reason,
            Instant updatedAt
    ) {
    }

    public record EventSummary(
            long seq,
            Instant virtualTime,
            String type,
            String orderId,
            Map<String, Object> payload
    ) {
    }
}
