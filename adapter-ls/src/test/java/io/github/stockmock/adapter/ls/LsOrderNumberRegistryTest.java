package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LsOrderNumberRegistryTest {
    @Test
    void mapsAStableNumericLsOrderNumberToTheCoreOrderId() {
        LsOrderNumberRegistry registry = new LsOrderNumberRegistry();

        long first = registry.register("ORD-000001");
        long repeated = registry.register("ORD-000001");

        assertThat(first).isEqualTo(100_001L);
        assertThat(repeated).isEqualTo(first);
        assertThat(registry.coreOrderId(first)).isEqualTo("ORD-000001");
        assertThat(registry.lsOrderNumber("ORD-000001")).isEqualTo(first);
    }

    @Test
    void issuesADifferentNumberForACancellationReceipt() {
        LsOrderNumberRegistry registry = new LsOrderNumberRegistry();
        long original = registry.register("ORD-000001");

        long cancellation = registry.issueReceiptNumber();

        assertThat(cancellation).isGreaterThan(original);
    }

    @Test
    void reportsAnUnknownLsOrderNumberWithTheNeutralCoreCode() {
        LsOrderNumberRegistry registry = new LsOrderNumberRegistry();

        assertThatThrownBy(() -> registry.coreOrderId(999_999L))
                .isInstanceOfSatisfying(CoreException.class,
                        exception -> assertThat(exception.code()).isEqualTo(CoreErrorCode.ORDER_NOT_FOUND));
    }
}
