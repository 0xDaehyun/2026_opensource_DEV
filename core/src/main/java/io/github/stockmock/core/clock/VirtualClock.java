package io.github.stockmock.core.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class VirtualClock {
    public enum Mode {
        ATTACHED,
        HEADLESS
    }

    private final Mode mode;
    private final Instant origin;
    private final LongSupplier nanoTime;
    private final long originNanos;
    private Instant advancedTime;

    public VirtualClock(Mode mode, Instant origin) {
        this(mode, origin, System::nanoTime);
    }

    VirtualClock(Mode mode, Instant origin, LongSupplier nanoTime) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.originNanos = nanoTime.getAsLong();
        this.advancedTime = origin;
    }

    public static VirtualClock attached(Instant origin) {
        return new VirtualClock(Mode.ATTACHED, origin);
    }

    public static VirtualClock headless(Instant origin) {
        return new VirtualClock(Mode.HEADLESS, origin);
    }

    public Mode mode() {
        return mode;
    }

    public synchronized Instant now() {
        if (mode == Mode.HEADLESS) {
            return advancedTime;
        }
        long elapsedNanos = Math.max(0, nanoTime.getAsLong() - originNanos);
        Instant wallProgress = origin.plusNanos(elapsedNanos);
        return wallProgress.isAfter(advancedTime) ? wallProgress : advancedTime;
    }

    public synchronized void advanceTo(Instant target) {
        Objects.requireNonNull(target, "target");
        if (target.isBefore(advancedTime)) {
            throw new IllegalArgumentException("가상시계는 뒤로 이동할 수 없습니다");
        }
        advancedTime = target;
    }

    public synchronized long waitMillisUntil(Instant target) {
        if (mode == Mode.HEADLESS) {
            return 0;
        }
        Duration remaining = Duration.between(now(), target);
        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }
        return Math.max(1, (remaining.toNanos() + 999_999) / 1_000_000);
    }
}
