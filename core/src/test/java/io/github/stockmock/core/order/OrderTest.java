package io.github.stockmock.core.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    @Test
    void acceptedOrderBecomesPartiallyFilledThenFilled() {
        Order order = order(100);

        order.fill(30);
        assertThat(order.state()).isEqualTo(OrderState.PARTIALLY_FILLED);
        assertThat(order.filledQuantity()).isEqualTo(30);
        assertThat(order.remainingQuantity()).isEqualTo(70);

        order.fill(70);
        assertThat(order.state()).isEqualTo(OrderState.FILLED);
    }

    @Test
    void acceptedOrderCanBeCancelled() {
        Order order = order(100);

        order.cancel();

        assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
    }

    @Test
    void partiallyFilledOrderCanBeCancelled() {
        Order order = order(100);
        order.fill(30);

        order.cancel();

        assertThat(order.state()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.remainingQuantity()).isEqualTo(70);
    }

    @Test
    void acceptedOrderCanBeModifiedOrRejected() {
        Order modified = order(100);
        modified.modify(80, 71_000);
        assertThat(modified.quantity()).isEqualTo(80);
        assertThat(modified.price()).isEqualTo(71_000);

        Order rejected = order(100);
        rejected.reject();
        assertThat(rejected.state()).isEqualTo(OrderState.REJECTED);
    }

    @Test
    void partiallyFilledOrderCannotBeRejected() {
        Order order = order(100);
        order.fill(30);

        assertThatThrownBy(order::reject)
                .isInstanceOf(IllegalOrderTransitionException.class);
    }

    @Test
    void terminalStatesRejectEveryTransition() {
        for (OrderState terminalState : new OrderState[]{OrderState.FILLED, OrderState.CANCELLED, OrderState.REJECTED}) {
            Order order = terminalOrder(terminalState);
            assertThatThrownBy(() -> order.fill(1)).isInstanceOf(IllegalOrderTransitionException.class);
            assertThatThrownBy(order::cancel).isInstanceOf(IllegalOrderTransitionException.class);
            assertThatThrownBy(() -> order.modify(100, 70_000)).isInstanceOf(IllegalOrderTransitionException.class);
            assertThatThrownBy(order::reject).isInstanceOf(IllegalOrderTransitionException.class);
        }
    }

    @Test
    void overfillIsRejected() {
        assertThatThrownBy(() -> order(100).fill(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Order terminalOrder(OrderState state) {
        Order order = order(100);
        switch (state) {
            case FILLED -> order.fill(100);
            case CANCELLED -> order.cancel();
            case REJECTED -> order.reject();
            default -> throw new IllegalArgumentException();
        }
        return order;
    }

    private Order order(long quantity) {
        return new Order("ORD-1", "CLIENT-1", new Symbol("005930"), Side.BUY, quantity, 70_000);
    }
}
