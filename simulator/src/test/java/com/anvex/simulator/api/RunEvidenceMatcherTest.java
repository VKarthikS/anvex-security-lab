package com.anvex.simulator.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RunEvidenceMatcherTest {

    @Test
    void exactServerIssuedRequestIdLinksTheDecisionToTheRun() {
        Map<String, Object> linked = Map.of("decisionId", "decision-1",
                "triggeringEventIds", List.of("server-request-7"));
        Map<String, Object> other = Map.of("decisionId", "decision-2",
                "triggeringEventIds", List.of("server-request-99"));
        RunEvidence result = RunEvidenceMatcher.match("run-1", Optional.of(Set.of("server-request-7")),
                Map.of("recentEvidence", List.of(other, linked)));
        assertThat(result.correlationAvailable()).isTrue();
        assertThat(result.observedRequestIds()).isEqualTo(1);
        assertThat(result.inspectedRecentDecisions()).isEqualTo(2);
        assertThat(result.decisions()).extracting(decision -> decision.get("decisionId"))
                .containsExactly("decision-1");
    }

    @Test
    void matchingTimestampsOrScenarioLabelsAloneNeverLinkEvidence() {
        Map<String, Object> unrelated = Map.of("decisionId", "decision-2",
                "createdAt", "2026-11-09T10:00:00Z",
                "scenarioId", "rapid-requests",
                "triggeringEventIds", List.of("somebody-else"));
        RunEvidence result = RunEvidenceMatcher.match("run-1", Optional.of(Set.of("server-request-7")),
                Map.of("recentEvidence", List.of(unrelated)));
        assertThat(result.decisions()).isEmpty();
    }

    @Test
    void noRetainedRequestIdsDoesNotPretendToProveNoDetection() {
        RunEvidence result = RunEvidenceMatcher.match("run-old", Optional.empty(),
                Map.of("recentEvidence", List.of()));
        assertThat(result.correlationAvailable()).isFalse();
        assertThat(result.caveat()).contains("no longer retained");
    }

    @Test
    void malformedOrMissingRecentDecisionsAreSafelyIgnored() {
        RunEvidence result = RunEvidenceMatcher.match("run-1", Optional.of(Set.of("r1")),
                Map.of("recentEvidence", List.of("bad", Map.of("triggeringEventIds", "not-a-list"),
                        Map.of("decisionId", "okay", "triggeringEventIds", List.of("r1")))));
        assertThat(result.decisions()).hasSize(1);
        RunEvidence missing = RunEvidenceMatcher.match("run-1", Optional.of(Set.of("r1")), Map.of());
        assertThat(missing.decisions()).isEmpty();
        assertThat(missing.inspectedRecentDecisions()).isZero();
    }
}
