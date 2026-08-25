package io.github.stockmock.adapter.ls;

import io.github.stockmock.core.error.CoreErrorCode;
import io.github.stockmock.core.error.CoreException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부에 노출하는 LS 숫자 주문번호와 core의 문자열 주문번호를 연결한다.
 *
 * <p>core는 증권사 중립적인 {@code ORD-000001} 형식을 유지하고, LS 전용 타입 변환은
 * adapter 안에서만 수행한다. 서버 재시작 시 Bean이 다시 만들어지므로 주문번호도 초기화된다.</p>
 */
@Component
final class LsOrderNumberRegistry {
    private static final long FIRST_ORDER_NUMBER = 100_001L;

    private final Map<String, Long> lsNumberByCoreId = new LinkedHashMap<>();
    private final Map<Long, String> coreIdByLsNumber = new LinkedHashMap<>();
    private long nextNumber = FIRST_ORDER_NUMBER;

    synchronized long register(String coreOrderId) {
        if (coreOrderId == null || coreOrderId.isBlank()) {
            throw new IllegalArgumentException("core 주문번호가 필요합니다");
        }
        Long existing = lsNumberByCoreId.get(coreOrderId);
        if (existing != null) {
            return existing;
        }
        long issued = nextNumber++;
        lsNumberByCoreId.put(coreOrderId, issued);
        coreIdByLsNumber.put(issued, coreOrderId);
        return issued;
    }

    synchronized String coreOrderId(long lsOrderNumber) {
        String coreOrderId = coreIdByLsNumber.get(lsOrderNumber);
        if (coreOrderId == null) {
            throw new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다");
        }
        return coreOrderId;
    }

    synchronized long lsOrderNumber(String coreOrderId) {
        Long lsOrderNumber = lsNumberByCoreId.get(coreOrderId);
        if (lsOrderNumber == null) {
            throw new CoreException(CoreErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다");
        }
        return lsOrderNumber;
    }

    synchronized long issueReceiptNumber() {
        return nextNumber++;
    }

    synchronized List<OrderNumber> orders() {
        List<OrderNumber> snapshot = new ArrayList<>(lsNumberByCoreId.size());
        lsNumberByCoreId.forEach((coreOrderId, lsOrderNumber) ->
                snapshot.add(new OrderNumber(lsOrderNumber, coreOrderId)));
        return List.copyOf(snapshot);
    }

    record OrderNumber(long lsOrderNumber, String coreOrderId) {
    }
}
