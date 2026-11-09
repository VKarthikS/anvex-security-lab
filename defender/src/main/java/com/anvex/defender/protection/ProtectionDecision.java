package com.anvex.defender.protection;

import java.time.Instant;

/**
 * The outcome of the protection gate for one request.
 */
public record ProtectionDecision(
        boolean allowed,
        boolean blocked,
        boolean rateLimited,
        int retryAfterSeconds,
        SourceBlock block,
        String reason
) {
    public static ProtectionDecision allowed(String reason) {
        return new ProtectionDecision(true, false, false, 0, null, reason);
    }

    public static ProtectionDecision blocked(SourceBlock block, int retryAfterSeconds) {
        return new ProtectionDecision(false, true, false, retryAfterSeconds, block,
                "Source is blocked until " + block.expiry() + " (" + block.reason() + ").");
    }

    public static ProtectionDecision rateLimited(int retryAfterSeconds) {
        return new ProtectionDecision(false, false, true, retryAfterSeconds, null,
                "Request rate exceeded the configured token bucket.");
    }
}
