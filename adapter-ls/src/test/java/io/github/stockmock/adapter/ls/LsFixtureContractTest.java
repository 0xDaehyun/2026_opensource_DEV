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
 *   <li>현물 매수(CSPAT00601): {@code fixtures/cash-buy-order.json} 확보, 검증 완료
 *       (OrdNo는 공식 타입이 number인데 core 주문번호가 문자열이라 알려진 gap으로 남겨둠).</li>
 *   <li>주문 조회(t0425): {@code fixtures/order-status-query.json} 확보, 검증 완료.
 *       실제 t0425는 종목코드 기준 목록 조회+페이지네이션이지만, core가 단건 조회만 지원해서
 *       MVP는 의도적으로 주문번호 1건 조회로 단순화했다(팀 확인 완료). ordno 타입과 status
 *       한글 텍스트 전체 목록은 여전히 알려진 gap.</li>
 *   <li>취소(CSPAT00801): {@code fixtures/cancel-order.json} 확보, 검증 완료.
 *       공식 응답에는 취소 수량·최종 상태 필드가 없어(취소는 새 접수번호를 매기는 별개 이벤트로
 *       취급) 그 정보는 t0425로 확인해야 한다는 걸 반영해 응답에서 뺐다. OrdNo/PrntOrdNo 타입과
 *       "새 취소 접수번호 대 원주문번호 구분"은 알려진 gap.</li>
 * </ul>
 *
 * <h2>다섯 API 완료</h2>
 * 토큰 발급, 잔고 조회, 매수, 주문 조회, 취소 다섯 API 모두 공식 fixture를 확보하고 구조·타입
 * 계약 테스트를 통과했다(ADAPTER-05 완료). 남은 알려진 gap은 각 fixture의 {@code knownGaps}/
 * {@code openQuestions}와 이 클래스의 타입 제외(exempt) 필드에 기록돼 있다.
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
                    Set.of("sunamt", "mamt", "tappamt"), Set.of());
            assertFieldsAreOfficialSubset(officialOutBlock1Fields, actual.path("t0424OutBlock1").get(0),
                    Set.of("expcode", "janqty", "mdposqt"), Set.of());
        }
    }

    @Test
    void cashBuyOrderResponseUsesTheOfficialOutBlockShapeAndFieldTypes() throws Exception {
        JsonNode fixture = readFixture("cash-buy-order.json");
        JsonNode officialOutBlock1Fields = fixture.path("response").path("outBlock1Fields");
        JsonNode officialOutBlock2Fields = fixture.path("response").path("outBlock2Fields");

        try (SimulationEngine engine = headlessEngine()) {
            CashBuyOrderHandler handler = new CashBuyOrderHandler(engine);
            JsonNode inBlock = objectMapper.readTree("""
                    {"IsuNo": "A005930", "OrdQty": 100, "OrdPrc": 70000, "BnsTpCode": "2"}
                    """);

            JsonNode actual = objectMapper.valueToTree(handler.handle(inBlock));

            assertThat(actual.path("rsp_cd").asText())
                    .as("매수 성공 rsp_cd는 공식 fixture의 값(%s)과 같아야 한다",
                            fixture.path("response").path("successRspCd").asText())
                    .isEqualTo(fixture.path("response").path("successRspCd").asText());

            assertThat(actual.path("CSPAT00601OutBlock1").isObject())
                    .as("CSPAT00601OutBlock1은 배열이 아니라 공식 응답처럼 object여야 한다")
                    .isTrue();
            assertThat(actual.path("CSPAT00601OutBlock2").isObject())
                    .as("CSPAT00601OutBlock2는 배열이 아니라 공식 응답처럼 object여야 한다")
                    .isTrue();

            assertFieldsAreOfficialSubset(officialOutBlock1Fields, actual.path("CSPAT00601OutBlock1"),
                    Set.of("RecCnt", "AcntNo", "IsuNo", "OrdQty", "OrdPrc", "BnsTpCode"), Set.of());
            // OrdNo는 알려진 타입 gap(공식 number, 현재 core 문자열)이라 타입 검사에서 제외한다.
            assertFieldsAreOfficialSubset(officialOutBlock2Fields, actual.path("CSPAT00601OutBlock2"),
                    Set.of("RecCnt", "OrdNo", "OrdTime", "OrdMktCode", "OrdPtnCode"), Set.of("OrdNo"));
        }
    }

    @Test
    void orderStatusResponseUsesTheOfficialOutBlockShapeAndFieldTypes() throws Exception {
        JsonNode fixture = readFixture("order-status-query.json");
        JsonNode officialOutBlock1Fields = fixture.path("response").path("outBlock1ItemFields");
        JsonNode officialOutBlockFields = fixture.path("response").path("outBlockFields");

        try (SimulationEngine engine = attachedEngine()) {
            OrderStatusHandler handler = new OrderStatusHandler(engine);
            var accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            JsonNode inBlock = objectMapper.readTree("{\"OrdNo\": \"" + accepted.orderId() + "\"}");

            JsonNode actual = objectMapper.valueToTree(handler.handle(inBlock));

            assertThat(actual.path("t0425OutBlock1").isArray())
                    .as("t0425OutBlock1은 공식 응답처럼 array여야 한다")
                    .isTrue();
            assertThat(actual.path("t0425OutBlock").isObject())
                    .as("t0425OutBlock은 배열이 아니라 공식 응답처럼 합계 object여야 한다")
                    .isTrue();

            // ordno는 알려진 타입 gap(공식 number, 현재 core 문자열)이라 타입 검사에서 제외한다.
            assertFieldsAreOfficialSubset(officialOutBlock1Fields, actual.path("t0425OutBlock1").get(0),
                    Set.of("ordno", "expcode", "medosu", "qty", "cheqty", "ordrem", "price", "status"),
                    Set.of("ordno"));
            assertFieldsAreOfficialSubset(officialOutBlockFields, actual.path("t0425OutBlock"),
                    Set.of("tqty", "tcheqty", "tordrem"), Set.of());
        }
    }

    @Test
    void cancelOrderResponseUsesTheOfficialOutBlockShapeAndFieldTypes() throws Exception {
        JsonNode fixture = readFixture("cancel-order.json");
        JsonNode officialOutBlock1Fields = fixture.path("response").path("outBlock1Fields");
        JsonNode officialOutBlock2Fields = fixture.path("response").path("outBlock2Fields");

        try (SimulationEngine engine = attachedEngine()) {
            CancelOrderHandler handler = new CancelOrderHandler(engine);
            var accepted = engine.submit(
                    new PlaceOrder("CLIENT-1", new Symbol("005930"), Side.BUY, 100, 70_000)).join();
            JsonNode inBlock = objectMapper.readTree(
                    "{\"OrgOrdNo\": \"" + accepted.orderId() + "\", \"IsuNo\": \"A005930\", \"OrdQty\": 100}");

            JsonNode actual = objectMapper.valueToTree(handler.handle(inBlock));

            assertThat(actual.path("rsp_cd").asText())
                    .as("취소 성공 rsp_cd는 공식 fixture의 값(%s)과 같아야 한다",
                            fixture.path("response").path("successRspCd").asText())
                    .isEqualTo(fixture.path("response").path("successRspCd").asText());

            assertThat(actual.path("CSPAT00801OutBlock1").isObject())
                    .as("CSPAT00801OutBlock1은 배열이 아니라 공식 응답처럼 object여야 한다")
                    .isTrue();
            assertThat(actual.path("CSPAT00801OutBlock2").isObject())
                    .as("CSPAT00801OutBlock2는 배열이 아니라 공식 응답처럼 object여야 한다")
                    .isTrue();

            // OrgOrdNo/OrdNo/PrntOrdNo는 알려진 타입 gap(공식 number, 현재 core 문자열)이라 타입 검사에서 제외한다.
            assertFieldsAreOfficialSubset(officialOutBlock1Fields, actual.path("CSPAT00801OutBlock1"),
                    Set.of("RecCnt", "AcntNo", "OrgOrdNo", "IsuNo", "OrdQty"), Set.of("OrgOrdNo"));
            assertFieldsAreOfficialSubset(officialOutBlock2Fields, actual.path("CSPAT00801OutBlock2"),
                    Set.of("RecCnt", "OrdNo", "PrntOrdNo", "OrdTime", "OrdMktCode", "OrdPtnCode"),
                    Set.of("OrdNo", "PrntOrdNo"));
        }
    }

    @Test
    void fixtureSampleValuesUseOurSanitizedPlaceholdersNotRealSecrets() throws Exception {
        assertSanitizedSample("token-issue.json", Map.of(
                "appkey", "SAMPLE",
                "appsecretkey", "SAMPLE",
                "access_token", "SAMPLE"));
        assertSanitizedSample("cash-buy-order.json", Map.of(
                "AcntNo", "SAMPLE",
                "MgempNo", "SAMPLE"));
        assertSanitizedSample("cancel-order.json", Map.of(
                "AcntNo", "SAMPLE",
                "AcntNm", "SAMPLE",
                "MgempNo", "SAMPLE"));
    }

    private void assertSanitizedSample(String fixtureFile, Map<String, String> sensitiveSampleFields) throws Exception {
        JsonNode fixture = readFixture(fixtureFile);

        sensitiveSampleFields.forEach((field, expectedPrefix) -> {
            JsonNode sample = findSampleValue(fixture, field);

            assertThat(sample.isMissingNode())
                    .as("%s의 fixture에 %s 샘플 필드가 있어야 한다", fixtureFile, field)
                    .isFalse();
            assertThat(sample.asText())
                    .as("%s의 %s 샘플 값은 실제 값이 아니라 %s로 시작하는 더미여야 한다", fixtureFile, field, expectedPrefix)
                    .startsWith(expectedPrefix);
        });
    }

    private JsonNode findSampleValue(JsonNode fixture, String field) {
        JsonNode requestSample = fixture.path("request").path("sample").path(field);
        if (!requestSample.isMissingNode()) {
            return requestSample;
        }
        JsonNode responseTopLevelSample = fixture.path("response").path("sample").path(field);
        if (!responseTopLevelSample.isMissingNode()) {
            return responseTopLevelSample;
        }
        Iterator<JsonNode> responseSampleChildren = fixture.path("response").path("sample").elements();
        while (responseSampleChildren.hasNext()) {
            JsonNode nested = responseSampleChildren.next().path(field);
            if (!nested.isMissingNode()) {
                return nested;
            }
        }
        return fixture.path("response").path("sample").path(field);
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
     * {@code typeExemptFields}에 속한 필드는 이름 존재만 확인하고 타입 비교는 건너뛴다 —
     * 아직 해결되지 않은 알려진 타입 gap을 명시적으로 표시하는 용도다.
     */
    private void assertFieldsAreOfficialSubset(JsonNode officialFields, JsonNode actual, Set<String> expectedSubset,
            Set<String> typeExemptFields) {
        var actualNames = new TreeSet<String>();
        actual.fieldNames().forEachRemaining(actualNames::add);
        assertThat(actualNames).as("실제 응답 필드 집합이 MVP 부분집합과 일치해야 한다").isEqualTo(new TreeSet<>(expectedSubset));

        for (String field : expectedSubset) {
            assertThat(officialFields.has(field))
                    .as("공식 fixture에 %s 필드가 있어야 한다", field)
                    .isTrue();
            if (typeExemptFields.contains(field)) {
                continue;
            }
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

    private SimulationEngine attachedEngine() {
        return new SimulationEngine(VirtualClock.attached(START), 10_000_000, 0.3, Duration.ofHours(1));
    }
}
