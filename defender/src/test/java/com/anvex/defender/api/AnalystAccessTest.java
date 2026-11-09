package com.anvex.defender.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "spring.datasource.url=jdbc:h2:mem:anvex-analyst-test;DB_CLOSE_DELAY=-1",
        "anvex.data-dir=./target/test-data"
})
class AnalystAccessTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @Test
    void remoteCallersCannotControlProtection() throws Exception {
        mockMvc.perform(post("/api/v1/protection")
                        .with(from("10.20.30.40"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("analyst_loopback_required"));
    }

    @Test
    void remoteCallersCannotResetDefenderState() throws Exception {
        mockMvc.perform(post("/api/v1/analyst/reset")
                        .with(from("192.168.5.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"demo\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void remoteCallersCannotReadDiagnostics() throws Exception {
        mockMvc.perform(get("/api/v1/diagnostics").with(from("192.168.5.5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loopbackCallersCanControlProtection() throws Exception {
        mockMvc.perform(post("/api/v1/protection")
                        .with(from("127.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"ON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ON"));
    }
}
