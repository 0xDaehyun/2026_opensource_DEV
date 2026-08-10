package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class LsTrDispatcher {
    private final Map<String, TrHandler> handlers;

    public LsTrDispatcher(List<TrHandler> registered) {
        Map<String, TrHandler> byCode = new LinkedHashMap<>();
        registered.forEach(handler -> {
            TrHandler duplicate = byCode.putIfAbsent(handler.trCode(), handler);
            if (duplicate != null) {
                throw new IllegalStateException("중복 TR handler입니다: " + handler.trCode());
            }
        });
        this.handlers = Map.copyOf(byCode);
    }

    public Map<String, Object> dispatch(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new LsRequestException("요청 본문은 JSON 객체여야 합니다");
        }
        var fields = body.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            String trCode = extractTrCode(field);
            TrHandler handler = handlers.get(trCode);
            if (handler != null) {
                return handler.handle(body.get(field));
            }
        }
        throw new UnknownTrException("지원하지 않는 TR입니다");
    }

    private String extractTrCode(String inBlockName) {
        int suffix = inBlockName.indexOf("InBlock");
        return suffix < 0 ? inBlockName : inBlockName.substring(0, suffix);
    }
}
