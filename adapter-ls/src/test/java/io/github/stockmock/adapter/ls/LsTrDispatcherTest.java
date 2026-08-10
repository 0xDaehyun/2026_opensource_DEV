package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LsTrDispatcherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesToAHandlerByInBlockName() throws Exception {
        LsTrDispatcher dispatcher = new LsTrDispatcher(List.of(new StubHandler("t9999")));
        JsonNode body = objectMapper.readTree("{\"t9999InBlock\": {\"value\": \"ok\"}}");

        assertThat(dispatcher.dispatch(body))
                .containsEntry("rsp_cd", "00000")
                .containsEntry("received", "ok");
    }

    @Test
    void rejectsUnknownTrCodes() throws Exception {
        LsTrDispatcher dispatcher = new LsTrDispatcher(List.of(new StubHandler("t9999")));
        JsonNode body = objectMapper.readTree("{\"t0000InBlock\": {}}");

        assertThatThrownBy(() -> dispatcher.dispatch(body))
                .isInstanceOf(UnknownTrException.class);
    }

    @Test
    void rejectsDuplicateHandlers() {
        assertThatThrownBy(() -> new LsTrDispatcher(List.of(
                new StubHandler("t9999"), new StubHandler("t9999"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("t9999");
    }

    /** TODO(ADAPTER-01): 새 Handler 테스트는 이 패턴을 복사해 시작한다. */
    private record StubHandler(String trCode) implements TrHandler {
        @Override
        public Map<String, Object> handle(JsonNode inBlock) {
            return Map.of("rsp_cd", "00000", "received", inBlock.path("value").asText());
        }
    }
}
