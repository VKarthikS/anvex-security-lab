package com.anvex.defender.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anvex.defender.alert.AlertService;
import com.anvex.defender.config.DefenderProperties;
import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.DetectionOutcome;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.detection.RepeatedGuessingRule;
import com.anvex.defender.detection.PasswordSprayingRule;
import com.anvex.defender.detection.RapidRequestsRule;
import com.anvex.defender.evidence.EvidenceService;
import com.anvex.defender.metrics.DefenderMetrics;
import com.anvex.defender.support.MutableClock;

class ProtectionServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SOURCE = "10.0.0.5";

    private MutableClock clock;
    private PolicyService policyService;
    private DetectionEngine detectionEngine;
    private ProtectionService protectionService;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at(START.toString());
        DefenderProperties properties = new DefenderProperties();
        policyService = new PolicyService(properties);
        detectionEngine = new DetectionEngine(policyService, List.of(
                new RepeatedGuessingRule(), new PasswordSprayingRule(), new RapidRequestsRule()), properties);
        protectionService = new ProtectionService(policyService, detectionEngine, new BlockRegistry(),
                new EvidenceService(), new AlertService(new com.anvex.defender.events.EventQueueService(properties)),
                new DefenderMetrics(), clock, new com.anvex.defender.events.EventQueueService(properties),
                org.mockito.Mockito.mock(com.anvex.defender.persistence.BlockPersistenceService.class),
                org.mockito.Mockito.mock(com.anvex.defender.persistence.SettingsPersistenceService.class));
    }

    private DetectionOutcome burst(int count) {
        DetectionOutcome outcome = null;
        for (int i = 0; i < count; i++) {
            outcome = detectionEngine.recordIncoming(SOURCE, clock.instant(), "evt-" + i);
        }
        return outcome;
    }

    @Test
    void protectionOffObservesWithoutEnforcing() {
        assertThat(protectionService.isProtectionOn()).isFalse();
        DetectionOutcome outcome = burst(40);
        ProtectionDecision decision = protectionService.gate(SOURCE, outcome, clock.instant(), "run-1");
        assertThat(decision.allowed()).isTrue();
        assertThat(protectionService.activeBlocks()).isEmpty();
    }

    @Test
    void protectionOnBlocksTheThresholdCrossingRapidRequest() {
        protectionService.setMode(ProtectionMode.ON, "test");
        DetectionOutcome outcome = burst(30);
        ProtectionDecision decision = protectionService.gate(SOURCE, outcome, clock.instant(), "run-1");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.retryAfterSeconds()).isGreaterThan(0);
        assertThat(protectionService.activeBlocks()).hasSize(1);
    }

    @Test
    void protectionOnAppliesTheTokenBucket() {
        protectionService.setMode(ProtectionMode.ON, "test");
        int allowed = 0;
        int limited = 0;
        for (int i = 0; i < 14; i++) {
            DetectionOutcome outcome = detectionEngine.recordIncoming("10.0.0.77", clock.instant(), "evt-" + i);
            ProtectionDecision decision = protectionService.gate("10.0.0.77", outcome, clock.instant(), "run-1");
            if (decision.allowed()) {
                allowed++;
            } else if (decision.rateLimited()) {
                limited++;
            }
        }
        assertThat(allowed).isEqualTo(10);
        assertThat(limited).isEqualTo(4);
    }

    @Test
    void switchingProtectionOnEnforcesStillValidHistoricalActivity() {
        for (int i = 0; i < 8; i++) {
            detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-" + i);
        }
        assertThat(protectionService.activeBlocks()).isEmpty();
        protectionService.setMode(ProtectionMode.ON, "test");
        assertThat(protectionService.activeBlocks()).hasSize(1);
        assertThat(protectionService.activeBlocks().get(0).sourceIp()).isEqualTo(SOURCE);
    }

    @Test
    void expiredActivityDoesNotBlockWhenProtectionIsEnabled() {
        for (int i = 0; i < 8; i++) {
            detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-" + i);
        }
        clock.advance(Duration.ofSeconds(45));
        protectionService.setMode(ProtectionMode.ON, "test");
        assertThat(protectionService.activeBlocks()).isEmpty();
    }

    @Test
    void manualUnblockRestoresAccessAndStartsARearmCooldown() {
        protectionService.setMode(ProtectionMode.ON, "test");
        for (int i = 0; i < 8; i++) {
            detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-" + i);
        }
        DetectionOutcome failureOutcome = detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-x");
        Optional<SourceBlock> block = protectionService.applyFailureRules(SOURCE, failureOutcome, "fail-x", clock.instant());
        assertThat(block).isPresent();

        ProtectionDecision blocked = protectionService.gate(SOURCE, DetectionOutcome.none(clock.instant()), clock.instant(), "run-1");
        assertThat(blocked.allowed()).isFalse();

        Optional<SourceBlock> released = protectionService.unblock(SOURCE, "analyst@loopback");
        assertThat(released).isPresent();

        ProtectionDecision allowed = protectionService.gate(SOURCE, DetectionOutcome.none(clock.instant()), clock.instant(), "run-1");
        assertThat(allowed.allowed()).isTrue();

        DetectionOutcome staleOutcome = detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-y");
        Optional<SourceBlock> recreated = protectionService.applyFailureRules(SOURCE, staleOutcome, "fail-y",
                clock.instant());
        assertThat(recreated).as("unchanged old evidence must not instantly recreate the block").isEmpty();

        clock.advance(Duration.ofSeconds(31));
        DetectionOutcome freshOutcome = null;
        for (int i = 0; i < 8; i++) {
            freshOutcome = detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fresh-" + i);
        }
        Optional<SourceBlock> freshBlock = protectionService.applyFailureRules(SOURCE, freshOutcome, "fresh-7",
                clock.instant());
        assertThat(freshBlock).as("new qualifying activity may trigger a new restriction").isPresent();
    }

    @Test
    void blocksExpireNaturallyWithoutBeingExtended() {
        protectionService.setMode(ProtectionMode.ON, "test");
        for (int i = 0; i < 8; i++) {
            detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-" + i);
        }
        DetectionOutcome failureOutcome = detectionEngine.recordFailure(SOURCE, "asha.raman", clock.instant(), "fail-x");
        protectionService.applyFailureRules(SOURCE, failureOutcome, "fail-x", clock.instant());
        SourceBlock block = protectionService.activeBlocks().get(0);
        Instant expiry = block.expiry();

        clock.advance(Duration.ofSeconds(31));
        assertThat(protectionService.activeBlocks()).isEmpty();
        assertThat(expiry).isEqualTo(block.expiry());
    }
}
