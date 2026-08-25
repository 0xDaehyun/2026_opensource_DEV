package io.github.stockmock.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mock.fill.delay=0s")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LsApiSmokeTest {
    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cashBuyOrderThenBalanceQueryUsesLsEnvelope() throws Exception {
        mockMvc.perform(post("/stock/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"CSPAT00601InBlock1": {
                                  "AcntNo": "12345678901",
                                  "IsuNo": "005930",
                                  "OrdQty": 100,
                                  "OrdPrc": 70000,
                                  "BnsTpCode": "2",
                                  "clientOrderId": "smoke-1"
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsp_cd").value("00040"))
                .andExpect(jsonPath("$.CSPAT00601OutBlock2.OrdNo").isNumber());

        mockMvc.perform(post("/stock/accno")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"t0424InBlock\": {\"accno\": \"12345678901\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsp_cd").value("00000"))
                .andExpect(jsonPath("$.t0424OutBlock.sunamt").value(3_000_000))
                .andExpect(jsonPath("$.t0424OutBlock.mamt").value(4_900_000))
                .andExpect(jsonPath("$.t0424OutBlock1[0].expcode").value("005930"))
                .andExpect(jsonPath("$.t0424OutBlock1[0].janqty").value(30));
    }

    @Test
    void tokenIssueEndpointReturnsBearerTokenAndRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "client_credentials")
                        .param("appkey", "smoke-app-key")
                        .param("appsecretkey", "smoke-app-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(86_400));

        mockMvc.perform(post("/oauth2/token")
                        .param("appkey", "smoke-app-key")
                        .param("appsecretkey", "smoke-app-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rsp_cd").value("40000"));
    }

    @Test
    void numericLsOrderNumberConnectsBuyStatusAndCancellation() throws Exception {
        MvcResult placed = mockMvc.perform(post("/stock/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"CSPAT00601InBlock1": {
                                  "AcntNo": "12345678901",
                                  "IsuNo": "A005930",
                                  "OrdQty": 100,
                                  "OrdPrc": 70000,
                                  "BnsTpCode": "2",
                                  "clientOrderId": "smoke-lifecycle-1"
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsp_cd").value("00040"))
                .andExpect(jsonPath("$.CSPAT00601OutBlock2.OrdNo").isNumber())
                .andReturn();

        JsonNode placedBody = objectMapper.readTree(placed.getResponse().getContentAsString());
        long originalOrderNumber = placedBody.path("CSPAT00601OutBlock2").path("OrdNo").asLong();

        mockMvc.perform(post("/stock/accno")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"t0425InBlock": {
                                  "expcode": "005930",
                                  "chegb": "0",
                                  "medosu": "0",
                                  "sortgb": "2",
                                  "cts_ordno": " "
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.t0425OutBlock1[0].ordno").value(originalOrderNumber))
                .andExpect(jsonPath("$.t0425OutBlock1[0].status").value("일부체결"));

        mockMvc.perform(post("/stock/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"CSPAT00801InBlock1": {
                                  "AcntNo": "12345678901",
                                  "OrgOrdNo": %d,
                                  "IsuNo": "A005930",
                                  "OrdQty": 70
                                }}
                                """.formatted(originalOrderNumber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsp_cd").value("00156"))
                .andExpect(jsonPath("$.CSPAT00801OutBlock2.OrdNo").isNumber())
                .andExpect(jsonPath("$.CSPAT00801OutBlock2.PrntOrdNo").value(originalOrderNumber));

        mockMvc.perform(post("/stock/accno")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"t0425InBlock": {
                                  "expcode": "005930",
                                  "chegb": "0",
                                  "medosu": "0",
                                  "sortgb": "2",
                                  "cts_ordno": " "
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.t0425OutBlock1[0].status").value("취소"));
    }
}
