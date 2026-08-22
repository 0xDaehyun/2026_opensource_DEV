package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stockmock.core.clock.VirtualClock;
import io.github.stockmock.core.engine.PlaceOrder;
import io.github.stockmock.core.engine.SimulationEngine;
import io.github.stockmock.core.order.Side;
import io.github.stockmock.core.order.Symbol;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LS 공식 콘솔 fixture와 우리 Handler의 실제 출력 구조·타입을 비교하는 ADAPTER-05 계약 테스트다.
 * 값이 아니라 필드 이름과 JSON 타입만 비교한다.
 *
 * <h2>진행 상황</h2>
 * <ul>
 *   <li>토큰 발급(oauth2/token): {@code fixtures/token-issue.json} 확보, 검증 완료.</li>
 *   <li>잔고 조회(t0424): {@code fixtures/balance-query.json} 확보, 검증 완료
 *       (MVP는 공식 필드 중 일부만 사용하므로 부분집합 비교).</li>
 *   <li>현물 매수(CSPAT00601), 주문 조회(t0425), 취소(CSPAT00801):
 *       공식 콘솔 fixture 미확보. fixture가 도착하면 이 클래스에 검증 메서드를 추가한다.</li>
 * </ul>
 */
class LsFixtureContractTest {
    private static final Instant START = Instant.parse("2026-01-02T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tokenIssueResponseMatchesTheOfficialFixtureFieldsAndTypes() throws Exception {
        JsonNode fixture = readFixture("token-issue.json");
        JsonNode expectedFields = fixture.path("response").path("fields");

        TokenController controller = new TokenController();
        TokenResponse response = controller.issue(
                new TokenRequest("client_credentials", "app-key", "app-secret", "oob"),
                Duration.ofSeconds(86_400));
        JsonNode actual = objectMapper.valueToTree(response);

        assertSameFieldNames(expectedFields, actual);
        assertFieldType(expectedFields, actual, "access_token", JsonNode::isTextual);
        assertFieldType(expectedFields, actual, "scope", JsonNode::isTextual);
        assertFieldType(expectedFields, actual, "token_type", JsonNode::isTextual);
        assertFieldType(expectedFields, actual, "expires_in", JsonNode::isIntegralNumber);
    }

    @Test
    void balanceQueryResponseUsesTheOfficialOutBlockShapeAndFieldTypes() throws Exception {
        JsonNode fixture = readFixture("balance-query.json");
        JsonNode officialOutBlockFields = fixture.path("response").path("outBlockFields");
        JsonNode officialOutBlock1Fields = fixture.path("response").path("outBlock1ItemFields");

        try (SimulationEngine engine = headlessEngine()) {
            engine.submit(new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            engine.awaitIdle().join();

            BalanceQueryHandler handler = new BalanceQueryHandler(engine);
            JsonNode actual = objectMapper.valueToTree(handler.handle(null));

            assertThat(actual.path("t0424OutBlock").isObject())
                    .as("t0424OutBlock은 배열이 아니라 공식 응답처럼 object여야 한다")
                    .isTrue();
            assertThat(actual.path("t0424OutBlock1").isArray())
                    .as("t0424OutBlock1은 공식 응답처럼 array여야 한다")
                    .isTrue();

            assertFieldsAreOfficialSubset(officialOutBlockFields, actual.path("t0424OutBlock"),
                    Set.of("sunamt", "mamt", "tappamt"));
            assertFieldsAreOfficialSubset(officialOutBlock1Fields, actual.path("t0424OutBlock1").get(0),
                    Set.of("expcode", "janqty", "mdposqt"));
        }
    }

    @Test
    void fixtureSampleValuesUseOurSanitizedPlaceholdersNotRealSecrets() throws Exception {
        JsonNode fixture = readFixture("token-issue.json");
        Map<String, String> sensitiveSampleFields = Map.of(
                "appkey", "SAMPLE",
                "appsecretkey", "SAMPLE",
                "access_token", "SAMPLE");

        sensitiveSampleFields.forEach((field, expectedPrefix) -> {
            JsonNode requestSample = fixture.path("request").path("sample").path(field);
            JsonNode responseSample = fixture.path("response").path("sample").path(field);
            JsonNode sample = requestSample.isMissingNode() ? responseSample : requestSample;

            assertThat(sample.isMissingNode())
                    .as("fixture에 %s 샘플 필드가 있어야 한다", field)
                    .isFalse();
            assertThat(sample.asText())
                    .as("%s 샘플 값은 실제 값이 아니라 %s로 시작하는 더미여야 한다", field, expectedPrefix)
                    .startsWith(expectedPrefix);
        });
    }

    private JsonNode readFixture(String fileName) throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/" + fileName)) {
            assertThat(in).as("fixture 파일이 있어야 한다: %s", fileName).isNotNull();
            return objectMapper.readTree(in);
        }
    }

    private void assertSameFieldNames(JsonNode expectedFields, JsonNode actual) {
        Iterator<String> expectedNames = expectedFields.fieldNames();
        var expected = new java.util.TreeSet<String>();
        expectedNames.forEachRemaining(expected::add);

        var actualNames = new java.util.TreeSet<String>();
        actual.fieldNames().forEachRemaining(actualNames::add);

        assertThat(actualNames)
                .as("실제 응답 필드 이름이 fixture와 정확히 일치해야 한다")
                .isEqualTo(expected);
    }

    private void assertFieldType(JsonNode expectedFields, JsonNode actual, String field,
            java.util.function.Predicate<JsonNode> typeCheck) {
        assertThat(expectedFields.has(field)).as("fixture에 %s 필드가 있어야 한다", field).isTrue();
        assertThat(typeCheck.test(actual.path(field)))
                .as("%s 필드의 실제 JSON 타입이 fixture(%s)와 일치해야 한다",
                        field, expectedFields.path(field).asText())
                .isTrue();
    }

    /**
     * MVP는 공식 필드 전체가 아니라 일부만 사용한다. 실제 필드 이름 집합이 {@code expectedSubset}과
     * 정확히 같은지, 그리고 각 필드가 공식 fixture에도 존재하며 타입(string/number)이 같은지 검증한다.
     */
    private void assertFieldsAreOfficialSubset(JsonNode officialFields, JsonNode actual, Set<String> expectedSubset) {
        var actualNames = new TreeSet<String>();
        actual.fieldNames().forEachRemaining(actualNames::add);
        assertThat(actualNames).as("실제 응답 필드 집합이 MVP 부분집합과 일치해야 한다").isEqualTo(new TreeSet<>(expectedSubset));

        for (String field : expectedSubset) {
            assertThat(officialFields.has(field))
                    .as("공식 fixture에 %s 필드가 있어야 한다", field)
                    .isTrue();
            String officialType = officialFields.path(field).asText();
            boolean typeMatches = switch (officialType) {
                case "number" -> actual.path(field).isNumber();
                case "string" -> actual.path(field).isTextual();
                default -> false;
            };
            assertThat(typeMatches)
                    .as("%s 필드의 실제 JSON 타입이 공식 타입(%s)과 일치해야 한다", field, officialType)
                    .isTrue();
        }
    }

    private SimulationEngine headlessEngine() {
        return new SimulationEngine(VirtualClock.headless(START), 10_000_000, 0.3, Duration.ofSeconds(5));
    }
}
