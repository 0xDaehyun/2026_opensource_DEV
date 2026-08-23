package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LS는 여러 TR이 같은 URL을 공유하고 요청 본문의 InBlock 키로 동작이 갈린다. 그래서 TR마다
 * Controller를 만들지 않고 URL 소수 + {@link LsTrDispatcher} 구조를 쓴다.
 *
 * <p>오류 응답은 {@link LsErrorAdvice}가 만든다.</p>
 */
@RestController
public final class LsController {
    private final LsTrDispatcher dispatcher;

    public LsController(LsTrDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping({"/stock/order", "/stock/accno"})
    public Map<String, Object> handle(@RequestBody JsonNode body) {
        return dispatcher.dispatch(body);
    }
}
