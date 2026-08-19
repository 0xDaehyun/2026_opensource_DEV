package io.github.stockmock.core.engine;

import io.github.stockmock.core.account.Account;
import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import io.github.stockmock.core.event.EventLog;
import io.github.stockmock.core.event.EventRecord;
import io.github.stockmock.core.event.SimEvent;
import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillStep;
import io.github.stockmock.core.order.Order;
import io.github.stockmock.core.order.OrderState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SimulationEngine implements EnginePort, AutoCloseable {
    private final VirtualClock clock;
    private final Account account;
    private final EventLog eventLog = new EventLog();
    private final PriorityQueue<SimEvent> queue = new PriorityQueue<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<String, String> orderIdsByClientId = new HashMap<>();
    private final Object queueMonitor = new Object();
    private final Thread engineThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final double fillRatio;
    private final Duration fillDelay;
    private long scheduleSequence;
    private long eventSequence;
    private long orderSequence;
    private Instant activeEventTime;

    public SimulationEngine(VirtualClock clock, long initialCash, double fillRatio, Duration fillDelay) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.account = new Account(initialCash);
        if (fillRatio <= 0 || fillRatio > 1 || fillDelay == null || fillDelay.isNegative()) {
            throw new IllegalArgumentException("체결 비율과 지연 설정이 올바르지 않습니다");
        }
        this.fillRatio = fillRatio;
        this.fillDelay = fillDelay;
        this.engineThread = Thread.ofPlatform().name("stock-mock-des").daemon(true).start(this::loop);
    }

    @Override
    public CompletableFuture<OrderResult> submit(PlaceOrder command) {
        Objects.requireNonNull(command, "command");
        CompletableFuture<OrderResult> result = new CompletableFuture<>();
        schedule(clock.now(), () -> accept(command, result));
        return result;
    }

    @Override
    public CompletableFuture<OrderResult> cancel(CancelOrder command) {
        Objects.requireNonNull(command, "command");
        CompletableFuture<OrderResult> result = new CompletableFuture<>();
        schedule(clock.now(), () -> cancelOnEngine(command, result));
        return result;
    }

    @Override
    public CompletableFuture<AccountView> query(AccountQuery query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<AccountView> result = new CompletableFuture<>();
        schedule(clock.now(), () -> result.complete(account.snapshot()));
        return result;
    }

    @Override
    public CompletableFuture<OrderView> query(OrderQuery query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<OrderView> result = new CompletableFuture<>();
        schedule(clock.now(), () -> {
            Order order = orders.get(query.orderId());
            if (order == null) {
                result.completeExceptionally(
                        new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다"));
                return;
            }
            result.complete(viewOf(order));
        });
        return result;
    }

    public CompletableFuture<java.util.List<EventRecord>> events() {
        CompletableFuture<java.util.List<EventRecord>> result = new CompletableFuture<>();
        schedule(clock.now(), () -> result.complete(eventLog.snapshot()));
        return result;
    }

    public CompletableFuture<Void> awaitIdle() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (queueMonitor) {
            if (!running.get()) {
                throw new IllegalStateException("엔진이 종료되었습니다");
            }
            Instant barrierTime = clock.now();
            if (activeEventTime != null && activeEventTime.isAfter(barrierTime)) {
                barrierTime = activeEventTime;
            }
            for (SimEvent queued : queue) {
                if (queued.time().isAfter(barrierTime)) {
                    barrierTime = queued.time();
                }
            }
            queue.add(new ScheduledEvent(barrierTime, ++scheduleSequence, () -> result.complete(null)));
            queueMonitor.notifyAll();
        }
        return result;
    }

    private void accept(PlaceOrder command, CompletableFuture<OrderResult> future) {
        if (command.clientOrderId() == null || command.clientOrderId().isBlank()) {
            future.completeExceptionally(new IllegalArgumentException("clientOrderId가 필요합니다"));
            return;
        }
        if (orderIdsByClientId.containsKey(command.clientOrderId())) {
            future.completeExceptionally(new IllegalArgumentException("중복 clientOrderId입니다"));
            return;
        }

        String orderId = "ORD-%06d".formatted(++orderSequence);
        Order order;
        try {
            order = new Order(orderId, command.clientOrderId(), command.symbol(), command.side(),
                    command.qty(), command.price());
            account.accept(order);
        } catch (RuntimeException exception) {
            append("ORDER_REJECTED", orderId, Map.of("reason", exception.getMessage()));
            future.complete(new OrderResult(orderId, command.clientOrderId(), OrderState.REJECTED,
                    command.qty(), 0, exception.getMessage()));
            return;
        }

        orders.put(orderId, order);
        orderIdsByClientId.put(command.clientOrderId(), orderId);
        append("ORDER_ACCEPTED", orderId, orderPayload(order));

        FillPlan fillPlan = FillPlan.partial(order.quantity(), fillRatio, fillDelay);
        Instant acceptedAt = clock.now();
        for (FillStep step : fillPlan.steps()) {
            schedule(acceptedAt.plus(step.delay()), () -> fill(orderId, step.quantity()));
        }
        future.complete(resultOf(order, null));
    }

    private void fill(String orderId, long quantity) {
        Order order = orders.get(orderId);
        if (order == null || order.state() == OrderState.CANCELLED || order.state() == OrderState.REJECTED) {
            return;
        }
        account.fill(order, quantity);
        order.fill(quantity);
        account.assertConsistent();
        append(order.state() == OrderState.FILLED ? "FILL" : "PARTIAL_FILL", orderId, orderPayload(order));
    }

    private void cancelOnEngine(CancelOrder command, CompletableFuture<OrderResult> future) {
        String resolvedId = orders.containsKey(command.targetOrderId())
                ? command.targetOrderId() : orderIdsByClientId.get(command.targetOrderId());
        Order order = resolvedId == null ? null : orders.get(resolvedId);
        if (order == null) {
            future.completeExceptionally(new IllegalArgumentException("대상 주문을 찾을 수 없습니다"));
            return;
        }
        if (command.qty() != 0 && command.qty() != order.remainingQuantity()) {
            future.completeExceptionally(new IllegalArgumentException("MVP에서는 미체결 전량 취소만 지원합니다"));
            return;
        }
        try {
            order.cancel();
            account.cancel(order);
            account.assertConsistent();
            append("ORDER_CANCELLED", order.id(), orderPayload(order));
            future.complete(resultOf(order, null));
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
    }

    private OrderResult resultOf(Order order, String reason) {
        return new OrderResult(order.id(), order.clientOrderId(), order.state(), order.quantity(),
                order.filledQuantity(), reason);
    }

    private OrderView viewOf(Order order) {
        return new OrderView(order.id(), order.clientOrderId(), order.symbol(), order.side(), order.state(),
                order.quantity(), order.filledQuantity(), order.remainingQuantity(), order.price());
    }

    private Map<String, Object> orderPayload(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientOrderId", order.clientOrderId());
        payload.put("symbol", order.symbol().value());
        payload.put("side", order.side().name());
        payload.put("quantity", order.quantity());
        payload.put("filledQuantity", order.filledQuantity());
        payload.put("remainingQuantity", order.remainingQuantity());
        payload.put("price", order.price());
        payload.put("state", order.state().name());
        return payload;
    }

    private void append(String type, String orderId, Map<String, Object> payload) {
        eventLog.append(new EventRecord(++eventSequence, clock.now(), type, orderId, payload));
    }

    private void schedule(Instant time, Runnable action) {
        synchronized (queueMonitor) {
            if (!running.get()) {
                throw new IllegalStateException("엔진이 종료되었습니다");
            }
            queue.add(new ScheduledEvent(time, ++scheduleSequence, action));
            queueMonitor.notifyAll();
        }
    }

    private void loop() {
        while (running.get()) {
            SimEvent event;
            synchronized (queueMonitor) {
                event = awaitNextEvent();
                if (event == null) {
                    continue;
                }
            }
            try {
                clock.advanceTo(event.time());
                event.apply();
                account.assertConsistent();
            } catch (Throwable failure) {
                running.set(false);
                throw new IllegalStateException("DES 엔진이 정합성 오류로 중단되었습니다", failure);
            } finally {
                synchronized (queueMonitor) {
                    activeEventTime = null;
                    queueMonitor.notifyAll();
                }
            }
        }
    }

    private SimEvent awaitNextEvent() {
        try {
            while (running.get()) {
                SimEvent next = queue.peek();
                if (next == null) {
                    queueMonitor.wait();
                    continue;
                }
                long waitMillis = clock.waitMillisUntil(next.time());
                if (waitMillis > 0) {
                    queueMonitor.wait(waitMillis);
                    continue;
                }
                SimEvent event = queue.poll();
                activeEventTime = event.time();
                return event;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    @Override
    public void close() {
        running.set(false);
        synchronized (queueMonitor) {
            queueMonitor.notifyAll();
        }
        try {
            engineThread.join(2_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record ScheduledEvent(Instant time, long seq, Runnable action) implements SimEvent {
        @Override
        public void apply() {
            action.run();
        }
    }
}
