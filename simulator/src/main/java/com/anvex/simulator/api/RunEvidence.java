package com.anvex.simulator.api;

import java.util.List;
import java.util.Map;

/** Correlation of defender decisions to returned, server-generated request IDs of a simulator run. */
public record RunEvidence(
        String runId,
        boolean correlationAvailable,
        int observedRequestIds,
        int inspectedRecentDecisions,
        List<Map<String, Object>> decisions,
        String caveat
) {
}
