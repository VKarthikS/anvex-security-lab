package com.anvex.simulator.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Match by exact defender-issued request ID. Timestamps or attacker-provided scenario labels
 * alone must never count as proof that a run generated a security decision.
 */
public final class RunEvidenceMatcher {
    private RunEvidenceMatcher() {}

    public static RunEvidence match(String runId, Optional<Set<String>> requestIds,
                                    Map<String, Object> defenderSnapshot) {
        String caveat = "Only the defender's 20 most recent evidence records are searched. "
                + "Older decisions may be omitted. A matching request ID establishes that "
                + "a request from this run triggered the decision, not that every contributing "
                + "attempt came from this run or that malicious intent was proven.";
        if (requestIds.isEmpty()) {
            return new RunEvidence(runId, false, 0, 0, List.of(),
                    "Request IDs for this run are no longer retained by the simulator. " + caveat);
        }
        Object raw = defenderSnapshot.get("recentEvidence");
        if (!(raw instanceof List<?> records)) {
            return new RunEvidence(runId, true, requestIds.get().size(), 0, List.of(),
                    "The defender's evidence list is unavailable. " + caveat);
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        Set<String> ids = requestIds.get();
        for (Object value : records) {
            if (!(value instanceof Map<?, ?> decision)) continue;
            Object refs = decision.get("triggeringEventIds");
            if (!(refs instanceof List<?> triggerIds)) continue;
            boolean matched = triggerIds.stream().anyMatch(id -> id instanceof String s && ids.contains(s));
            if (!matched) continue;
            Map<String, Object> safeCopy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : decision.entrySet()) {
                if (entry.getKey() instanceof String key) safeCopy.put(key, entry.getValue());
            }
            matches.add(java.util.Collections.unmodifiableMap(safeCopy));
        }
        return new RunEvidence(runId, true, ids.size(), records.size(), List.copyOf(matches), caveat);
    }
}
