package io.github.stockmock.core.fill;

import java.time.Duration;
import java.util.List;

public record FillPlan(List<FillStep> steps) {
    public FillPlan {
        steps = List.copyOf(steps);
    }

    public static FillPlan partial(long orderQuantity, double ratio, Duration delay) {
        if (orderQuantity <= 0 || ratio <= 0 || ratio > 1) {
            throw new IllegalArgumentException("주문 수량은 양수이고 체결 비율은 0 초과 1 이하여야 합니다");
        }
        long fillQuantity = Math.max(1, Math.min(orderQuantity, (long) Math.floor(orderQuantity * ratio)));
        return new FillPlan(List.of(new FillStep(delay, fillQuantity)));
    }
}
