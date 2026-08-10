package io.github.stockmock.core.account;

import java.util.Map;

public record AccountView(long cash, long lockedCash, Map<String, PositionView> positions) {
    public AccountView {
        positions = Map.copyOf(positions);
    }
}
