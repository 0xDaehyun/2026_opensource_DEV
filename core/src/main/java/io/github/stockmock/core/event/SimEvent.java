package io.github.stockmock.core.event;

import java.time.Instant;

public interface SimEvent extends Comparable<SimEvent> {
    Instant time();

    long seq();

    void apply();

    @Override
    default int compareTo(SimEvent other) {
        int timeComparison = time().compareTo(other.time());
        return timeComparison != 0 ? timeComparison : Long.compare(seq(), other.seq());
    }
}
