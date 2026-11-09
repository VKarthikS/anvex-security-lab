package com.anvex.defender.evidence;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.anvex.defender.detection.DetectionCategory;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.detection.RuleMatch;

/**
 * Creates and keeps evidence snapshots. The in-memory index is a bounded read cache; the durable
 * copy is written by the event pipeline.
 */
@Service
public class EvidenceService {

    private static final int RECENT_LIMIT = 500;

    private final Map<String, EvidenceSnapshot> byDecisionId = new ConcurrentHashMap<>();
    private final Deque<EvidenceSnapshot> recent = new ArrayDeque<>();

    public EvidenceSnapshot record(String sourceIp,
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
                                   Instant createdAt) {
        EvidenceSnapshot snapshot = new EvidenceSnapshot(
                UUID.randomUUID().toString(),
                sourceIp,
                List.copyOf(affectedUsernames),
                category,
                List.copyOf(matchedRules),
                Map.copyOf(observed),
                Map.copyOf(windows),
                List.copyOf(triggeringEventIds),
                policyVersion,
                policySnapshot,
                protectionMode,
                action,
                reason,
                blockExpiry,
                createdAt);
        byDecisionId.put(snapshot.decisionId(), snapshot);
        synchronized (recent) {
            recent.addLast(snapshot);
            while (recent.size() > RECENT_LIMIT) {
                recent.removeFirst();
            }
        }
        return snapshot;
    }

    public Optional<EvidenceSnapshot> byDecisionId(String decisionId) {
        return Optional.ofNullable(byDecisionId.get(decisionId));
    }

    public List<EvidenceSnapshot> recent(int limit) {
        synchronized (recent) {
            List<EvidenceSnapshot> ordered = new ArrayList<>(recent);
            ordered.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
            return ordered.stream().limit(Math.max(1, limit)).toList();
        }
    }

    /** Clears the in-memory evidence index (demo history reset). */
    public void clear() {
        byDecisionId.clear();
        synchronized (recent) {
            recent.clear();
        }
    }
}
