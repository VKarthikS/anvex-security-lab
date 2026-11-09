package com.anvex.defender.detection;

import java.time.Instant;

/**
 * The observable values a rule is evaluated against. Counts are already window-bounded and include
 * the sample that triggered the evaluation.
 */
public record RuleEvaluationContext(
        String sourceIp,
        String username,
        Instant now,
        PolicySnapshot policy,
        String triggeringEventId,
        int incomingCount,
        Instant incomingWindowStart,
        int sourceFailureCount,
        Instant sourceFailureWindowStart,
        int accountFailureCount,
        Instant accountFailureWindowStart,
        int distinctAccountCount,
        Instant distinctWindowStart
) {
}
