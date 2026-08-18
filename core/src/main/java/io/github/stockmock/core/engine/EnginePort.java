package io.github.stockmock.core.engine;

import io.github.stockmock.core.account.AccountView;

import java.util.concurrent.CompletableFuture;

public interface EnginePort {
    CompletableFuture<OrderResult> submit(PlaceOrder command);

    CompletableFuture<OrderResult> cancel(CancelOrder command);

    CompletableFuture<AccountView> query(AccountQuery query);

    CompletableFuture<OrderView> query(OrderQuery query);
}
