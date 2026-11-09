package com.anvex.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunSnapshot(
        String runId,
        String state,
        String scenarioId,
        Instant startedAt,
        Instant endedAt,
        RunCounters counters,
        LatencySummary latency,
        double achievedRatePerSecond,
        String targetBaseUrl,
        RunConfig config,
        String message
) {
    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String STOPPING = "STOPPING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    public static RunSnapshot idle(String targetBaseUrl) {
        return new RunSnapshot(null, IDLE, null, null, null, RunCounters.empty(), LatencySummary.empty(), 0,
                targetBaseUrl, null, null);
    }

    public static List<RunSnapshot> noRuns() {
        return List.of();
    }
}
