package io.github.stockmock.adapter.ls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LS 공식 콘솔 fixture와 우리 Handler의 실제 출력 구조·타입을 비교하는 ADAPTER-05 계약 테스트다.
 * 값이 아니라 필드 이름과 JSON 타입만 비교한다.
 *
 * <h2>진행 상황</h2>
 * <ul>
 *   <li>토큰 발급(oauth2/token): {@code fixtures/token-issue.json} 확보, 검증 완료.</li>
 *   <li>잔고 조회(t0424), 현물 매수(CSPAT00601), 주문 조회(t0425), 취소(CSPAT00801):
 *       공식 콘솔 fixture 미확보. fixture가 도착하면 이 클래스에 검증 메서드를 추가한다.</li>
 * </ul>
 */
class LsFixtureContractTest {
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
}
