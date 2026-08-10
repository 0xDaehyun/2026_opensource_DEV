package io.github.stockmock.core.event;

import java.time.Instant;
import java.util.Map;

public record EventRecord(long seq, Instant virtualTime, String type,
                          String orderId, Map<String, Object> payload) {
    public EventRecord {
        payload = Map.copyOf(payload);
    }
}
