package com.anvex.defender.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Analyst supplied detection and protection settings. Every field is optional: omitted fields keep
 * their current value.
 */
public record SettingsUpdateRequest(
        @Min(2) @Max(100) Integer repeatedGuessingFailureThreshold,
        @Min(5) @Max(600) Integer repeatedGuessingWindowSeconds,
        @Min(2) @Max(200) Integer sprayingFailureThreshold,
        @Min(2) @Max(200) Integer sprayingDistinctAccountThreshold,
        @Min(5) @Max(900) Integer sprayingWindowSeconds,
        @Min(5) @Max(500) Integer rapidRequestThreshold,
        @Min(1) @Max(120) Integer rapidRequestWindowSeconds,
        @Min(1) @Max(100) Integer tokenBucketRequestsPerSecond,
        @Min(1) @Max(200) Integer tokenBucketBurstCapacity,
        @Min(5) @Max(600) Integer blockDurationSeconds,
        @Size(max = 80) String policyVersion
) {
}
