package io.github.stockmock.core.fill;

import java.time.Duration;

public record FillStep(Duration delay, long quantity) {
    public FillStep {
        if (delay == null || delay.isNegative() || quantity <= 0) {
            throw new IllegalArgumentException("체결 지연은 음수가 아니고 수량은 0보다 커야 합니다");
        }
    }
}
