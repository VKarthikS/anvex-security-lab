package com.anvex.defender.protection;

import java.time.Instant;

/**
 * A temporary source block. Blocks are source scoped: no account lockout exists in v1.
 */
public record SourceBlock(
        String blockId,
        String sourceIp,
        String ruleId,
        String reason,
        Instant createdAt,
        Instant expiry,
        Instant releasedAt,
        String releasedBy,
        String state
) {
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_EXPIRED = "EXPIRED";
    public static final String STATE_RELEASED = "RELEASED";

    public boolean isActiveAt(Instant now) {
        return STATE_ACTIVE.equals(state) && now.isBefore(expiry);
    }

    public SourceBlock released(Instant now, String actor) {
        return new SourceBlock(blockId, sourceIp, ruleId, reason, createdAt, expiry, now, actor, STATE_RELEASED);
    }

    public SourceBlock expired() {
        return new SourceBlock(blockId, sourceIp, ruleId, reason, createdAt, expiry, releasedAt, releasedBy, STATE_EXPIRED);
    }
}
