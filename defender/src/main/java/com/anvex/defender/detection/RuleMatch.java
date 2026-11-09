package com.anvex.defender.detection;

import java.time.Instant;

/**
 * One rule match with the observed evidence that produced it.
 */
public record RuleMatch(
        String ruleId,
        DetectionCategory category,
        int threshold,
        int observedCount,
        int observedDistinctAccounts,
        Instant windowStart,
        Instant windowEnd,
        String description
) {
    public static final String UNIT_REQUESTS = "requests";
    public static final String UNIT_FAILURES = "failed authentications";
    public static final String UNIT_ACCOUNTS = "distinct usernames";
}
