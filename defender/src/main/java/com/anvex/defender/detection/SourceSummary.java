package com.anvex.defender.detection;

import java.time.Instant;

/**
 * A compact per-source activity summary used by the monitoring interface.
 */
public record SourceSummary(
        String sourceIp,
        long totalIncoming,
        long totalFailures,
        long totalSuccesses,
        Instant lastActivity,
        int trackedAccounts
) {
}
