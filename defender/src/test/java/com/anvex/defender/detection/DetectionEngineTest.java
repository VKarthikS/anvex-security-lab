package com.anvex.defender.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anvex.defender.config.DefenderProperties;
import com.anvex.defender.support.MutableClock;

class DetectionEngineTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private DetectionEngine engine;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at(START.toString());
        DefenderProperties properties = new DefenderProperties();
        PolicyService policyService = new PolicyService(properties);
        engine = new DetectionEngine(policyService, List.of(
                new RepeatedGuessingRule(),
                new PasswordSprayingRule(),
                new RapidRequestsRule()), properties);
    }

    private DetectionOutcome fail(String source, String username) {
        return engine.recordFailure(source, username, clock.instant(), "evt");
    }

    private DetectionOutcome request(String source) {
        return engine.recordIncoming(source, clock.instant(), "evt");
    }

    @Test
    void repeatedGuessingTriggersOnlyAtTheThreshold() {
        for (int i = 1; i <= 7; i++) {
            DetectionOutcome outcome = fail("10.0.0.5", "asha.raman");
            assertThat(outcome.hasMatches()).as("failure %d must not match", i).isFalse();
        }
        DetectionOutcome outcome = fail("10.0.0.5", "asha.raman");
        assertThat(outcome.primaryCategory()).isEqualTo(DetectionCategory.SUSPECTED_REPEATED_GUESSING);
        assertThat(outcome.matches()).extracting(RuleMatch::ruleId).contains(RuleIds.REPEATED_GUESSING);
        assertThat(outcome.matches()).extracting(RuleMatch::observedCount).contains(8);
    }

    @Test
    void failuresOutsideTheWindowExpire() {
        for (int i = 0; i < 7; i++) {
            fail("10.0.0.5", "asha.raman");
        }
        clock.advance(Duration.ofSeconds(31));
        DetectionOutcome outcome = fail("10.0.0.5", "asha.raman");
        assertThat(outcome.hasMatches()).isFalse();
    }

    @Test
    void failuresAreTrackedPerSourceAndPerAccount() {
        for (int i = 0; i < 7; i++) {
            fail("10.0.0.5", "asha.raman");
        }
        DetectionOutcome otherAccount = fail("10.0.0.5", "rohan.mehta");
        assertThat(otherAccount.hasMatches()).isFalse();

        DetectionOutcome otherSource = fail("10.0.0.6", "asha.raman");
        assertThat(otherSource.hasMatches()).isFalse();

        DetectionOutcome crossing = fail("10.0.0.5", "asha.raman");
        assertThat(crossing.primaryCategory()).isEqualTo(DetectionCategory.SUSPECTED_REPEATED_GUESSING);
    }

    @Test
    void sprayingRequiresEnoughFailuresAcrossEnoughDistinctAccounts() {
        String[] accounts = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"};
        DetectionOutcome outcome = null;
        for (String account : accounts) {
            outcome = fail("10.0.0.9", account);
        }
        assertThat(outcome).isNotNull();
        assertThat(outcome.matches()).extracting(RuleMatch::ruleId).contains(RuleIds.PASSWORD_SPRAYING);
        RuleMatch match = outcome.matches().stream()
                .filter(candidate -> candidate.ruleId().equals(RuleIds.PASSWORD_SPRAYING))
                .findFirst()
                .orElseThrow();
        assertThat(match.observedDistinctAccounts()).isGreaterThanOrEqualTo(8);
        assertThat(match.description()).contains("not proven");
    }

    @Test
    void sprayingDoesNotTriggerWithTooFewDistinctAccounts() {
        DetectionOutcome outcome = null;
        for (int i = 0; i < 12; i++) {
            outcome = fail("10.0.0.9", "same-account");
        }
        assertThat(outcome).isNotNull();
        assertThat(outcome.matches()).extracting(RuleMatch::ruleId).doesNotContain(RuleIds.PASSWORD_SPRAYING);
    }

    @Test
    void rapidRequestsTriggersAtThirtyRequestsWithinFiveSeconds() {
        DetectionOutcome outcome = null;
        for (int i = 1; i <= 30; i++) {
            outcome = request("10.0.0.7");
            if (i < 30) {
                assertThat(outcome.hasMatches()).as("request %d must not match", i).isFalse();
            }
        }
        assertThat(outcome).isNotNull();
        assertThat(outcome.primaryCategory()).isEqualTo(DetectionCategory.SUSPECTED_RAPID_REQUESTS);
    }

    @Test
    void rapidRequestsExpireWithTheWindow() {
        for (int i = 0; i < 29; i++) {
            request("10.0.0.7");
        }
        clock.advance(Duration.ofSeconds(6));
        DetectionOutcome outcome = request("10.0.0.7");
        assertThat(outcome.hasMatches()).isFalse();
    }

    @Test
    void aSuccessfulLoginDoesNotEraseEarlierFailures() {
        for (int i = 0; i < 7; i++) {
            fail("10.0.0.5", "asha.raman");
        }
        engine.recordSuccess("10.0.0.5", "asha.raman", clock.instant(), "evt-success");
        DetectionOutcome outcome = fail("10.0.0.5", "asha.raman");
        assertThat(outcome.primaryCategory()).isEqualTo(DetectionCategory.SUSPECTED_REPEATED_GUESSING);
    }

    @Test
    void multipleRulesCanMatchTheSameDecision() {
        for (int i = 0; i < 8; i++) {
            request("10.0.0.8");
        }
        clock.advance(Duration.ofSeconds(4));
        for (int i = 0; i < 24; i++) {
            request("10.0.0.8");
        }
        DetectionOutcome outcome = null;
        String[] accounts = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"};
        for (String account : accounts) {
            outcome = fail("10.0.0.8", account);
        }
        assertThat(outcome).isNotNull();
        assertThat(outcome.matches()).extracting(RuleMatch::ruleId)
                .contains(RuleIds.PASSWORD_SPRAYING, RuleIds.RAPID_REQUESTS);
        assertThat(outcome.primaryCategory()).isEqualTo(DetectionCategory.SUSPECTED_PASSWORD_SPRAYING);
    }
}
