package io.github.stockmock.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mock.scenario=classpath:fast-partial-fill.yml")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void servesTheDashboardWithoutANodeRuntime() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stock Mock Console")));
    }

    @Test
    void reportsAccountOrdersAndRecentEvents() throws Exception {
        String token = issueToken();
        mockMvc.perform(post("/stock/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"CSPAT00601InBlock1": {
                                  "AcntNo": "12345678901",
                                  "IsuNo": "005930",
                                  "OrdQty": 100,
                                  "OrdPrc": 70000,
                                  "BnsTpCode": "2",
                                  "clientOrderId": "dashboard-test-1"
                                }}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mock/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverStatus").value("RUNNING"))
                .andExpect(jsonPath("$.account.cash").value(3_000_000))
                .andExpect(jsonPath("$.account.lockedCash").value(4_900_000))
                .andExpect(jsonPath("$.account.positions[0].symbol").value("005930"))
                .andExpect(jsonPath("$.account.positions[0].quantity").value(30))
                .andExpect(jsonPath("$.orderCounts.partiallyFilled").value(1))
                .andExpect(jsonPath("$.orders[0].state").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.events[0].type").value("PARTIAL_FILL"));
    }

    private String issueToken() throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "client_credentials")
                        .param("appkey", "dashboard-test")
                        .param("appsecretkey", "dashboard-secret"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).path("access_token").asText();
    }
}
