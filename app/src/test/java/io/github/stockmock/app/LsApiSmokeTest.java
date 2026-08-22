package io.github.stockmock.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mock.fill.delay=0s")
@AutoConfigureMockMvc
class LsApiSmokeTest {
    @Autowired
    MockMvc mockMvc;

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
                .andExpect(jsonPath("$.CSPAT00601OutBlock2.OrdNo").value("ORD-000001"));

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
}
