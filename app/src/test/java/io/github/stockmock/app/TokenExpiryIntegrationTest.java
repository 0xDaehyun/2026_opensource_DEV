package io.github.stockmock.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "mock.scenario=classpath:short-token-scenario.yml")
@AutoConfigureMockMvc
class TokenExpiryIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void rejectsAnExpiredTokenUsingVirtualTime() throws Exception {
        String issued = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "client_credentials")
                        .param("appkey", "expiry-test")
                        .param("appsecretkey", "expiry-secret"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(issued).path("access_token").asText();

        Thread.sleep(35);

        mockMvc.perform(post("/stock/accno")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"t0424InBlock\": {\"accno\": \"12345678901\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.rsp_cd").value("40100"));
    }
}
