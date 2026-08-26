package io.github.stockmock.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "mock.scenario=classpath:policy-scenario.yml",
        "mock.clock.mode=HEADLESS"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScenarioPolicyIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appliesRateLimitBeforeTheEngine() throws Exception {
        String token = issueToken();
        String balanceQuery = "{\"t0424InBlock\": {\"accno\": \"12345678901\"}}";

        mockMvc.perform(post("/stock/accno")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceQuery))
                .andExpect(status().isOk());

        mockMvc.perform(post("/stock/accno")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceQuery))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.rsp_cd").value("42900"));
    }

    @Test
    void delaysTheResponseAfterTheOrderHasBeenCommitted() throws Exception {
        String token = issueToken();
        long started = System.nanoTime();

        mockMvc.perform(post("/stock/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"CSPAT00601InBlock1": {
                                  "AcntNo": "12345678901",
                                  "IsuNo": "005930",
                                  "OrdQty": 100,
                                  "OrdPrc": 70000,
                                  "BnsTpCode": "2",
                                  "clientOrderId": "delayed-order"
                                }}
                                """))
                .andExpect(status().isOk());

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(25);
    }

    private String issueToken() throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "client_credentials")
                        .param("appkey", "policy-test")
                        .param("appsecretkey", "policy-secret"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("access_token").asText();
    }
}
