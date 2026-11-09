package com.anvex.defender.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anvex-login-status-test;DB_CLOSE_DELAY=-1",
        "anvex.data-dir=./target/test-data-login-status"
})
class LoginResponseStatusTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @AfterEach
    void resetProtection() throws Exception {
        mockMvc.perform(post("/api/v1/protection")
                .with(from("127.0.0.1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"OFF\"}"));
    }

    @Test
    void validCredentialsReturnAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(from("10.10.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"asha.raman\",\"password\":\"Asha!2345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("authenticated"))
                .andExpect(jsonPath("$.user.username").value("asha.raman"));
    }

    @Test
    void invalidCredentialsReturnGenericFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(from("10.10.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"asha.raman\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("invalid_credentials"))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void protectionOnReturnsTooManyRequestsWithRetryAfter() throws Exception {
        mockMvc.perform(post("/api/v1/protection")
                .with(from("127.0.0.1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"ON\"}"))
                .andExpect(status().isOk());

        int rateLimited = 0;
        for (int attempt = 0; attempt < 40; attempt++) {
            int result = mockMvc.perform(post("/api/v1/auth/login")
                            .with(from("10.10.0.3"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"asha.raman\",\"password\":\"wrong-password\"}"))
                    .andReturn().getResponse().getStatus();
            if (result == 429) {
                rateLimited++;
            }
        }

        assertThat(rateLimited).isPositive();
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(from("10.10.0.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"asha.raman\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo("rate_limited"),
                        org.hamcrest.Matchers.equalTo("source_blocked"))));
    }
}
