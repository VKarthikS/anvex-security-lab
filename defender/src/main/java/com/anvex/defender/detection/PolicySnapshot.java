package com.anvex.defender.detection;

import java.time.Instant;

/**
 * An immutable snapshot of the detection and protection policy. Every decision stores the snapshot
 * that was in force, so later configuration changes cannot rewrite history.
 */
public record PolicySnapshot(
        String policyVersion,
        int repeatedGuessingFailureThreshold,
        long repeatedGuessingWindowMillis,
        int sprayingFailureThreshold,
        int sprayingDistinctAccountThreshold,
        long sprayingWindowMillis,
        int rapidRequestThreshold,
        long rapidRequestWindowMillis,
        int tokenBucketRequestsPerSecond,
        int tokenBucketBurstCapacity,
        long blockDurationMillis,
        ProtectionMode protectionMode,
        Instant createdAt
) {
}
