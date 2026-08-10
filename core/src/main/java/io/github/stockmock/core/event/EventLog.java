package io.github.stockmock.core.event;

import java.util.ArrayList;
import java.util.List;

public final class EventLog {
    private final List<EventRecord> records = new ArrayList<>();

    public void append(EventRecord record) {
        records.add(record);
    }

    public List<EventRecord> snapshot() {
        return List.copyOf(records);
    }
}