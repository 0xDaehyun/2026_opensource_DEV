package io.github.stockmock.core.account;

import io.github.stockmock.core.order.Order;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {
    @Test
    void buyAcceptanceLocksCash() {
        Account account = new Account(10_000_000);
        Order order = buyOrder(100, 70_000);

        account.accept(order);

        assertThat(account.snapshot().cash()).isEqualTo(3_000_000);
        assertThat(account.snapshot().lockedCash()).isEqualTo(7_000_000);
        account.assertConsistent();
    }

    @Test
    void partialFillThenCancelReleasesOnlyUnfilledCash() {
        Account account = new Account(10_000_000);
        Order order = buyOrder(100, 70_000);
        account.accept(order);

        order.fill(30);
        account.fill(order, 30);
        order.cancel();
        account.cancel(order);

        AccountView view = account.snapshot();
        assertThat(view.cash()).isEqualTo(7_900_000);
        assertThat(view.lockedCash()).isZero();
        assertThat(view.positions().get("005930").quantity()).isEqualTo(30);
        account.assertConsistent();
    }

    @Test
    void insufficientCashCannotCreateNegativeBalance() {
        Account account = new Account(1_000);

        assertThatThrownBy(() -> account.accept(buyOrder(1, 1_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("증거금");
        assertThat(account.snapshot().cash()).isEqualTo(1_000);
        assertThat(account.snapshot().lockedCash()).isZero();
    }

    @Test
    void sellCannotMakePositionNegative() {
        Account account = new Account(1_000_000);
        Order sell = new Order("ORD-2", "CLIENT-2", new Symbol("005930"), Side.SELL, 1, 70_000);

        assertThatThrownBy(() -> account.accept(sell))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매도 가능");
    }

    private Order buyOrder(long quantity, long price) {
        return new Order("ORD-1", "CLIENT-1", new Symbol("005930"), Side.BUY, quantity, price);
    }
}
