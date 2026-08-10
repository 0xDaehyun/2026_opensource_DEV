package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.engine.CancelOrder;
import io.github.stockmock.core.engine.EnginePort;
import io.github.stockmock.core.engine.OrderResult;

import java.util.Map;

/**
 * TODO(ADAPTER-02): LS 미체결 주문 전량 취소를 구현한다.
 *
 * <h2>입력</h2>
 * <ul>
 *   <li>{@code inBlock}: 공식 LS 취소 fixture와 같은 JSON object</li>
 *   <li>원주문번호: 취소 대상 core 주문번호</li>
 *   <li>clientOrderId: 취소 요청을 식별하는 비어 있지 않은 문자열</li>
 * </ul>
 *
 * <h2>처리</h2>
 * <ol>
 *   <li>InBlock을 검증한다.</li>
 *   <li>{@link CancelOrder}를 생성한다.</li>
 *   <li>{@link EnginePort#cancel(CancelOrder)}만 호출한다.</li>
 *   <li>{@link OrderResult}를 공식 LS OutBlock으로 변환한다.</li>
 * </ol>
 *
 * <h2>출력</h2>
 * <p>성공 시 주문번호, 취소 수량, 최종 상태와 LS 성공 봉투를 반환한다.</p>
 *
 * <h2>경계와 오류</h2>
 * <ul>
 *   <li>MVP는 부분 취소가 아니라 미체결 수량 전체를 취소한다.</li>
 *   <li>30/100주 부분체결 주문은 70주만 취소한다.</li>
 *   <li>FILLED, CANCELLED, REJECTED 주문 취소는 오류 봉투로 변환한다.</li>
 * </ul>
 *
 * <p>현금 반환과 상태 변경은 core의 책임이다. 구현 완료 후에만 {@code @Component}를 추가한다.</p>
 */
final class CancelOrderHandler implements TrHandler {
    private final EnginePort engine;

    CancelOrderHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "CSPAT00801"; // TODO(ADAPTER-02): 공식 콘솔에서 취소 TR을 최종 확인한다.
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        throw new UnsupportedOperationException("TODO(ADAPTER-02): 미체결 주문 전량 취소");
    }
}
