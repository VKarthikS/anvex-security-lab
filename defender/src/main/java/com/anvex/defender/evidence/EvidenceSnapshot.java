package com.anvex.defender.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.anvex.defender.detection.DetectionCategory;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.detection.RuleMatch;

/**
 * An immutable evidence snapshot for one security decision. Evidence is written independently of
 * the optional raw-event detail so it stays readable even when raw events were aggregated.
 */
public record EvidenceSnapshot(
        String decisionId,
        String sourceIp,
        List<String> affectedUsernames,
        DetectionCategory category,
        List<RuleMatch> matchedRules,
        Map<String, Object> observed,
        Map<String, Object> windows,
        List<String> triggeringEventIds,
        String policyVersion,
        PolicySnapshot policySnapshot,
        ProtectionMode protectionMode,
        String action,
        String reason,
        Instant blockExpiry,
        Instant createdAt
) {
    public static final String ACTION_OBSERVATION_ONLY = "observation-only";
    public static final String ACTION_ENFORCED = "enforced";
}
