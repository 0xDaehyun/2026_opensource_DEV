package io.github.stockmock.core.engine;

import io.github.stockmock.core.account.Account;
import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.event.EventLog;
import io.github.stockmock.core.event.EventRecord;
import io.github.stockmock.core.event.SimEvent;
import io.github.stockmock.core.fill.FillPlan;
import io.github.stockmock.core.fill.FillPlanProvider;
import io.github.stockmock.core.fill.FillStep;
import io.github.stockmock.core.order.Order;
import io.github.stockmock.core.order.OrderState;

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
    private final FillPlanProvider fillPlanProvider;
    private long scheduleSequence;
    private long eventSequence;
    private long orderSequence;
    private Instant activeEventTime;

    public SimulationEngine(VirtualClock clock, long initialCash, FillPlanProvider fillPlanProvider) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.account = new Account(initialCash);
        this.fillPlanProvider = Objects.requireNonNull(fillPlanProvider, "fillPlanProvider");
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
                result.completeExceptionally(new IllegalArgumentException("주문을 찾을 수 없습니다"));
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
        } catch (RuntimeException exception) {
            rejectOrder(null, orderId, command, exception, future);
            return;
        }

        try {
            account.accept(order);
        } catch (RuntimeException exception) {
            rejectOrder(order, orderId, command, exception, future);
            return;
        }

        FillPlan fillPlan;
        try {
            fillPlan = Objects.requireNonNull(fillPlanProvider.create(order.quantity()),
                    "FillPlanProvider는 null을 반환할 수 없습니다");
            validateFillPlan(order.quantity(), fillPlan);
        } catch (RuntimeException exception) {
            account.cancel(order);
            account.assertConsistent();
            rejectOrder(order, orderId, command, exception, future);
            return;
        }

        orders.put(orderId, order);
        orderIdsByClientId.put(command.clientOrderId(), orderId);
        append("ORDER_ACCEPTED", orderId, orderPayload(order));

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

    private void rejectOrder(Order order, String orderId, PlaceOrder command, RuntimeException failure,
                             CompletableFuture<OrderResult> future) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (order != null) {
            order.reject();
            orders.put(orderId, order);
        }
        append("ORDER_REJECTED", orderId, Map.of("reason", reason));
        future.complete(new OrderResult(orderId, command.clientOrderId(), OrderState.REJECTED,
                command.qty(), 0, reason));
    }

    private OrderView viewOf(Order order) {
        return new OrderView(order.id(), order.clientOrderId(), order.symbol(), order.side(), order.state(),
                order.quantity(), order.filledQuantity(), order.remainingQuantity(), order.price());
    }

    private void validateFillPlan(long orderQuantity, FillPlan fillPlan) {
        long plannedQuantity = 0;
        for (FillStep step : fillPlan.steps()) {
            try {
                plannedQuantity = Math.addExact(plannedQuantity, step.quantity());
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("체결 계획 수량 합계가 너무 큽니다", overflow);
            }
            if (plannedQuantity > orderQuantity) {
                throw new IllegalArgumentException("체결 계획 수량은 주문 수량을 넘을 수 없습니다");
            }
        }
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
