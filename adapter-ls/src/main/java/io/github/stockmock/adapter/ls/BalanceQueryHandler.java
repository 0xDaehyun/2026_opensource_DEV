package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.stockmock.core.account.AccountView;
import io.github.stockmock.core.engine.AccountQuery;
import io.github.stockmock.core.engine.EnginePort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
final class BalanceQueryHandler implements TrHandler {
    private final EnginePort engine;

    BalanceQueryHandler(EnginePort engine) {
        this.engine = engine;
    }

    @Override
    public String trCode() {
        return "t0424";
    }

    @Override
    public Map<String, Object> handle(JsonNode inBlock) {
        AccountView account = engine.query(new AccountQuery()).join();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sunamt", account.cash());
        summary.put("mamt", account.lockedCash());
        summary.put("tappamt", account.cash() + account.lockedCash());

        List<Map<String, Object>> positions = new ArrayList<>();
        account.positions().forEach((symbol, position) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("expcode", symbol);
            row.put("janqty", position.quantity());
            row.put("mdposqt", position.quantity());
            positions.add(row);
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rsp_cd", "00000");
        response.put("rsp_msg", "조회완료");
        response.put("t0424OutBlock", summary);
        response.put("t0424OutBlock1", positions);
        return response;
    }
}
