package com.anvex.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ComparisonReport(
        String comparisonId,
        String baselineRunId,
        String candidateRunId,
        Instant createdAt,
        Map<String, Object> settingsSnapshot,
        Map<String, Object> parameters,
        Side baseline,
        Side candidate,
        Reduction reduction,
        String verdict,
        List<String> caveats
) {
    public record Side(
            String runId,
            String state,
            String scenarioId,
            long sent,
            long completed,
            long authenticationSuccess,
            long authenticationFailure,
            long rateLimited,
            long sourceBlocked,
            long transportErrors,
            Double defenderReceived,
            Double defenderReachedAuthentication,
            Double defenderRateLimited,
            Double defenderSourceBlocked,
            Double achievedRatePerSecond,
            Instant startedAt,
            Instant endedAt
    ) {
    }

    public record Reduction(
            Double sentReductionPercent,
            Double defenderReceivedReductionPercent,
            Double authenticationAdmissionReductionPercent,
            Double rateLimitedIncrease,
            Boolean meaningful,
            String label
    ) {
    }
}
