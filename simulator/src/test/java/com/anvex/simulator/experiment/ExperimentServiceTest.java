package com.anvex.simulator.experiment;

import com.anvex.common.*;
import com.anvex.simulator.client.DefenderClient;
import com.anvex.simulator.config.TargetSettings;
import com.anvex.simulator.run.RunManager;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ExperimentServiceTest {
    private static RunConfig config(String scenario, List<String> users) {
        return new RunConfig(scenario, 2, 12, users, 100, 4);
    }

    private static RunSnapshot run(String id, String state, RunConfig config, int sent) {
        return new RunSnapshot(id, state, config.scenarioId(), Instant.parse("2026-11-09T10:00:00Z"),
            Instant.parse("2026-11-09T10:00:12Z"),
            new RunCounters(sent, sent, sent, 0, 0, 0, sent, 0, 0, 0),
            LatencySummary.empty(), 2.0, "http://127.0.0.1:8080", config, null);
    }

    private static ComparisonReport compare(RunSnapshot base, RunSnapshot candidate) {
        RunManager manager = mock(RunManager.class);
        DefenderClient defender = mock(DefenderClient.class);
        TargetSettings target = mock(TargetSettings.class);
        when(manager.find(base.runId())).thenReturn(Optional.of(base));
        when(manager.find(candidate.runId())).thenReturn(Optional.of(candidate));
        when(target.baseUrl()).thenReturn("http://127.0.0.1:8080");
        when(defender.fetchMetrics(anyString(), anyString())).thenReturn(Map.of("runCounters", Map.of(
            "incomingRequests", 24, "requestsReachingAuthentication", 18,
            "rateLimitedRequests", 6, "sourceBlockedRequests", 0)));
        when(defender.fetchPolicy(anyString())).thenReturn(Map.of("policyVersion", "test-policy"));
        return new ExperimentService(manager, defender, target).compare(base.runId(), candidate.runId());
    }

    @Test void matchingRunsAreMeasurable() {
        RunConfig c = config("repeated-guessing", List.of("asha.raman"));
        ComparisonReport report = compare(run("base", RunSnapshot.COMPLETED,c,24),run("cand",RunSnapshot.COMPLETED,c,24));
        assertThat(report.verdict()).isEqualTo("measured-comparison");
        assertThat(report.parameters().get("identicalParameters")).isEqualTo(true);
        assertThat(report.reduction().meaningful()).isTrue();
        assertThat(report.reduction().sentReductionPercent()).isEqualTo(0.0);
    }

    @Test void differentScenariosSuppressReduction() {
        ComparisonReport report = compare(run("base", RunSnapshot.COMPLETED,config("repeated-guessing",List.of("asha.raman")),24),
             run("cand",RunSnapshot.COMPLETED,config("password-spraying",List.of("asha.raman")),24));
        assertThat(report.verdict()).isEqualTo("incomparable-parameters");
        assertThat(report.reduction().meaningful()).isFalse();
        assertThat(report.reduction().sentReductionPercent()).isNull();
        assertThat(report.reduction().authenticationAdmissionReductionPercent()).isNull();
    }

    @Test void differentAccountsSuppressReduction() {
        ComparisonReport report = compare(run("base",RunSnapshot.COMPLETED,config("repeated-guessing",List.of("asha.raman")),24),
            run("cand",RunSnapshot.COMPLETED,config("repeated-guessing",List.of("rohan.mehta")),24));
        assertThat(report.verdict()).isEqualTo("incomparable-parameters");
        assertThat(report.parameters().get("identicalParameters")).isEqualTo(false);
    }

    @Test void failedRunDoesNotGetPercentReduction() {
        RunConfig c = config("repeated-guessing",List.of("asha.raman"));
        ComparisonReport report = compare(run("base",RunSnapshot.COMPLETED,c,24),run("cand",RunSnapshot.FAILED,c,24));
        assertThat(report.verdict()).isEqualTo("incomplete-comparison");
        assertThat(report.reduction().defenderReceivedReductionPercent()).isNull();
    }

    @Test void tinyComparisonNotMarkedMeaningful() {
        RunConfig c = config("repeated-guessing",List.of("asha.raman"));
        ComparisonReport report = compare(run("base",RunSnapshot.COMPLETED,c,6),run("cand",RunSnapshot.COMPLETED,c,6));
        assertThat(report.verdict()).isEqualTo("small-sample");
        assertThat(report.reduction().meaningful()).isFalse();
    }

    @Test void selfComparisonIsRejected() {
        RunManager manager=mock(RunManager.class);
        RunConfig c=config("repeated-guessing",List.of("asha.raman"));
        when(manager.find("same")).thenReturn(Optional.of(run("same",RunSnapshot.COMPLETED,c,24)));
        assertThatThrownBy(()->new ExperimentService(manager,mock(DefenderClient.class),mock(TargetSettings.class)).compare("same","same"))
             .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different runs");
    }
}
