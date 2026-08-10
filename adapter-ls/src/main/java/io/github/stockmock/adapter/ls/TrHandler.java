package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

interface TrHandler {
    String trCode();

    Map<String, Object> handle(JsonNode inBlock);
}
