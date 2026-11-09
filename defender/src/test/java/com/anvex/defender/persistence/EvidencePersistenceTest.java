package com.anvex.defender.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anvex-evidence-test;DB_CLOSE_DELAY=-1",
        "anvex.data-dir=./target/test-data-evidence"
})
class EvidencePersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Test
    void detectionsArePersistedWithoutCredentialMaterial() throws Exception {
        String password = "not-the-password";
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"asha.raman\",\"password\":\"" + password + "\"}"))
                    .andReturn();
        }

        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline && evidenceRepository.count() == 0) {
            Thread.sleep(100);
        }

        List<Entities.EvidenceEntity> evidence = evidenceRepository.findAll();
        assertThat(evidence).isNotEmpty();
        assertThat(evidence.get(0).getDecisionId()).isNotBlank();
        assertThat(evidence.get(0).getSourceIp()).isNotBlank();

        String allJson = evidence.stream()
                .map(entity -> String.valueOf(entity.getObservedJson())
                        + String.valueOf(entity.getMatchedRulesJson())
                        + String.valueOf(entity.getEventRefsJson())
                        + String.valueOf(entity.getAffectedUsernamesJson()))
                .reduce("", String::concat);
        assertThat(allJson).doesNotContain(password);
        assertThat(allJson).doesNotContain("not-the-password");

        assertThat(securityEventRepository.count()).isPositive();
        assertThat(securityEventRepository.findAll())
                .allSatisfy(event -> assertThat(event.getPayloadJson() == null
                        || !event.getPayloadJson().contains(password)).isTrue());
    }
}
