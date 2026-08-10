package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.EnginePort;

import java.util.Map;

/**
 * TODO(ADAPTER-01): LS 주문 상태·미체결 조회를 구현한다.
 *
 * <h2>선행 조건</h2>
 * <p>core에 {@code OrderQuery}, {@code OrderView},
 * {@code EnginePort.query(OrderQuery)}가 병합되어야 한다.</p>
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code inBlock}: 공식 LS fixture와 같은 JSON object</li>
 *   <li>대상 주문번호: 비어 있지 않은 문자열</li>
 * </ul>
 *
 * <h2>출력</h2>
 * <p>{@code rsp_cd}, {@code rsp_msg}와 다음 값을 가진 주문 조회 OutBlock:</p>
 * <ul>
 *   <li>주문번호</li>
 *   <li>주문 상태</li>
 *   <li>전체 주문 수량</li>
 *   <li>체결 수량</li>
 *   <li>미체결 수량</li>
 *   <li>주문 가격</li>
 * </ul>
 *
 * <h2>오류</h2>
 * <ul>
 *   <li>InBlock 또는 주문번호 누락: {@link LsRequestException}</li>
 *   <li>주문 없음: {@link LsErrorMapper}를 통해 ORDER_NOT_FOUND 봉투 반환</li>
 * </ul>
 *
 * <p>이 클래스는 주문 상태를 계산하거나 core 내부 객체를 직접 조회하지 않는다.
 * 구현 완료 후에만 {@code @Component}를 추가한다.</p>
 */
final class OrderStatusHandler implements TrHandler {
    private final EnginePort engine;

    OrderStatusHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "t0425"; // TODO(ADAPTER-01): 공식 콘솔에서 MVP 조회 TR을 최종 확인한다.
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        throw new UnsupportedOperationException("TODO(ADAPTER-01): 주문 상태 조회");
    }
}
