package io.github.stockmock.core.account;

import io.github.stockmock.core.order.Order;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;

import java.util.HashMap;
import java.util.Map;

public final class Account {
    private final long initialCash;
    private long cash;
    private long lockedCash;
    private long buyFillAmount;
    private long sellFillAmount;
    private final Map<Symbol, Long> positions = new HashMap<>();
    private final Map<Symbol, Long> lockedPositions = new HashMap<>();

    public Account(long initialCash) {
        if (initialCash < 0) {
            throw new IllegalArgumentException("초기 현금은 음수일 수 없습니다");
        }
        this.initialCash = initialCash;
        this.cash = initialCash;
        assertConsistent();
    }

    public void accept(Order order) {
        long amount = Math.multiplyExact(order.quantity(), order.price());
        if (order.side() == Side.BUY) {
            if (cash < amount) {
                throw new IllegalArgumentException("증거금이 부족합니다");
            }
            cash -= amount;
            lockedCash += amount;
        } else {
            long available = positions.getOrDefault(order.symbol(), 0L)
                    - lockedPositions.getOrDefault(order.symbol(), 0L);
            if (available < order.quantity()) {
                throw new IllegalArgumentException("매도 가능 수량이 부족합니다");
            }
            lockedPositions.merge(order.symbol(), order.quantity(), Long::sum);
        }
        assertConsistent();
    }

    public void fill(Order order, long fillQuantity) {
        long amount = Math.multiplyExact(fillQuantity, order.price());
        if (order.side() == Side.BUY) {
            lockedCash -= amount;
            buyFillAmount += amount;
            positions.merge(order.symbol(), fillQuantity, Long::sum);
        } else {
            lockedPositions.compute(order.symbol(), (symbol, locked) -> requiredNonNegative(locked, fillQuantity));
            positions.compute(order.symbol(), (symbol, quantity) -> requiredNonNegative(quantity, fillQuantity));
            cash += amount;
            sellFillAmount += amount;
        }
        assertConsistent();
    }

    public void cancel(Order order) {
        if (order.side() == Side.BUY) {
            long release = Math.multiplyExact(order.remainingQuantity(), order.price());
            lockedCash -= release;
            cash += release;
        } else {
            lockedPositions.compute(order.symbol(),
                    (symbol, locked) -> requiredNonNegative(locked, order.remainingQuantity()));
        }
        assertConsistent();
    }

    private Long requiredNonNegative(Long current, long decrease) {
        if (current == null || current < decrease) {
            throw new IllegalStateException("계좌 수량 불변식이 깨졌습니다");
        }
        long result = current - decrease;
        return result == 0 ? null : result;
    }

    public void assertConsistent() {
        if (cash < 0 || lockedCash < 0) {
            throw new IllegalStateException("현금 불변식이 깨졌습니다");
        }
        long reconstructed = Math.addExact(Math.addExact(cash, lockedCash), buyFillAmount) - sellFillAmount;
        if (reconstructed != initialCash) {
            throw new IllegalStateException("계좌 원장 불변식이 깨졌습니다");
        }
        if (positions.values().stream().anyMatch(quantity -> quantity < 0)) {
            throw new IllegalStateException("보유 수량 불변식이 깨졌습니다");
        }
    }

    public AccountView snapshot() {
        Map<String, PositionView> view = new HashMap<>();
        positions.forEach((symbol, quantity) -> view.put(symbol.value(), new PositionView(quantity)));
        return new AccountView(cash, lockedCash, view);
    }
}
