package com.anvex.simulator.experiment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.anvex.common.ComparisonReport;
import com.anvex.common.RunSnapshot;
import com.anvex.simulator.client.DefenderClient;
import com.anvex.simulator.config.TargetSettings;
import com.anvex.simulator.run.RunManager;

/**
 * Builds the OFF-versus-ON comparison from measured values only. Both runs keep their own report;
 * the first run is never deleted. Reductions are calculated from the counters that were actually
 * observed, and small or incomplete samples are labelled as such.
 */
@Service
public class ExperimentService {

    private final RunManager runManager;
    private final DefenderClient defenderClient;
    private final TargetSettings targetSettings;
    private final List<ComparisonReport> reports = Collections.synchronizedList(new ArrayList<>());

    public ExperimentService(RunManager runManager, DefenderClient defenderClient, TargetSettings targetSettings) {
        this.runManager = runManager;
        this.defenderClient = defenderClient;
        this.targetSettings = targetSettings;
    }

    public List<ComparisonReport> reports() {
        synchronized (reports) {
            return List.copyOf(reports);
        }
    }

    public ComparisonReport compare(String baselineRunId, String candidateRunId) {
        RunSnapshot baseline = runManager.find(baselineRunId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown baseline run: " + baselineRunId));
        RunSnapshot candidate = runManager.find(candidateRunId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown candidate run: " + candidateRunId));

        if (baselineRunId.equals(candidateRunId)) {
            throw new IllegalArgumentException("Baseline and candidate must be different runs.");
        }

        Map<String, Object> baselineCounters = defenderCounters(baselineRunId);
        Map<String, Object> candidateCounters = defenderCounters(candidateRunId);
        Map<String, Object> settings = defenderPolicy();

        ComparisonReport.Side baselineSide = side(baseline, baselineCounters);
        ComparisonReport.Side candidateSide = side(candidate, candidateCounters);

        String verdict = label(baseline, candidate);
        boolean comparable = sameConfiguration(baseline, candidate)
                && RunSnapshot.COMPLETED.equals(baseline.state())
                && RunSnapshot.COMPLETED.equals(candidate.state());
        // Never show reduction percentages for different scenarios/configurations, or unfinished runs.
        // Raw measurements remain available in the report for debugging and investigation.
        ComparisonReport.Reduction reduction = new ComparisonReport.Reduction(
                comparable ? percent(toDouble(baselineSide.sent()), toDouble(candidateSide.sent())) : null,
                comparable ? percent(baselineSide.defenderReceived(), candidateSide.defenderReceived()) : null,
                comparable ? percent(baselineSide.defenderReachedAuthentication(), candidateSide.defenderReachedAuthentication()) : null,
                comparable && baselineSide.defenderRateLimited() != null && candidateSide.defenderRateLimited() != null
                        ? candidateSide.defenderRateLimited() - baselineSide.defenderRateLimited()
                        : null,
                "measured-comparison".equals(verdict),
                verdict);

        List<String> caveats = new ArrayList<>(List.of(
                "Incoming traffic continues while protection is ON: the reduction is in requests reaching authentication.",
                "The run id is experiment metadata only; it never influences defender classification.",
                "Protection does not make failed credentials succeed: the outcome remains a failed authentication.",
                "The policy snapshot is read at comparison time; it is not a per-run policy history."));
        if (!sameConfiguration(baseline, candidate)) {
            caveats.add("Scenarios, target URLs, or accepted run settings differ; percentage reductions are suppressed.");
        }
        if (baseline.counters().transportErrors() > 0 || candidate.counters().transportErrors() > 0) {
            caveats.add("At least one run experienced transport errors. Interpret the outcome carefully.");
        }

        ComparisonReport report = new ComparisonReport(
                UUID.randomUUID().toString(),
                baselineRunId,
                candidateRunId,
                Instant.now(),
                settings,
                parameters(baseline, candidate),
                baselineSide,
                candidateSide,
                reduction,
                verdict,
                List.copyOf(caveats));
        reports.add(report);
        synchronized (reports) {
            while (reports.size() > 20) {
                reports.remove(0);
            }
        }
        return report;
    }

    private ComparisonReport.Side side(RunSnapshot snapshot, Map<String, Object> defenderCounters) {
        return new ComparisonReport.Side(
                snapshot.runId(),
                snapshot.state(),
                snapshot.scenarioId(),
                snapshot.counters().sent(),
                snapshot.counters().completed(),
                snapshot.counters().authenticationSuccess(),
                snapshot.counters().authenticationFailure(),
                snapshot.counters().rateLimited(),
                snapshot.counters().sourceBlocked(),
                snapshot.counters().transportErrors(),
                number(defenderCounters, "incomingRequests"),
                number(defenderCounters, "requestsReachingAuthentication"),
                number(defenderCounters, "rateLimitedRequests"),
                number(defenderCounters, "sourceBlockedRequests"),
                snapshot.achievedRatePerSecond(),
                snapshot.startedAt(),
                snapshot.endedAt());
    }

    private Map<String, Object> defenderCounters(String runId) {
        Map<String, Object> body = defenderClient.fetchMetrics(targetSettings.baseUrl(), runId);
        Object runCounters = body.get("runCounters");
        if (runCounters instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, value) -> typed.put(String.valueOf(key), value));
            return typed;
        }
        return Map.of();
    }

    private Map<String, Object> defenderPolicy() {
        return defenderClient.fetchPolicy(targetSettings.baseUrl());
    }

    private Map<String, Object> parameters(RunSnapshot baseline, RunSnapshot candidate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baselineScenario", baseline.scenarioId());
        body.put("candidateScenario", candidate.scenarioId());
        body.put("baselineRatePerSecond", baseline.config() == null ? null : baseline.config().ratePerSecond());
        body.put("candidateRatePerSecond", candidate.config() == null ? null : candidate.config().ratePerSecond());
        body.put("baselineDurationSeconds", baseline.config() == null ? null : baseline.config().durationSeconds());
        body.put("candidateDurationSeconds", candidate.config() == null ? null : candidate.config().durationSeconds());
        body.put("baselineTargetUrl", baseline.targetBaseUrl());
        body.put("candidateTargetUrl", candidate.targetBaseUrl());
        body.put("baselineUsernames", baseline.config() == null ? List.of() : baseline.config().targetUsernames());
        body.put("candidateUsernames", candidate.config() == null ? List.of() : candidate.config().targetUsernames());
        body.put("identicalParameters", sameConfiguration(baseline, candidate));
        return body;
    }

    private static Double number(Map<String, Object> counters, String key) {
        Object value = counters.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Double toDouble(long value) {
        return (double) value;
    }

    private static Double percent(Double baseline, Double candidate) {
        if (baseline == null || candidate == null || baseline == 0.0) {
            return null;
        }
        return Math.round((baseline - candidate) / baseline * 10000.0) / 100.0;
    }

    /** A comparison is only like-for-like when its full accepted run settings and target match. */
    static boolean sameConfiguration(RunSnapshot baseline, RunSnapshot candidate) {
        return baseline.config() != null
                && baseline.config().equals(candidate.config())
                && Objects.equals(baseline.scenarioId(), candidate.scenarioId())
                && Objects.equals(baseline.targetBaseUrl(), candidate.targetBaseUrl());
    }

    static String label(RunSnapshot baseline, RunSnapshot candidate) {
        if (!RunSnapshot.COMPLETED.equals(baseline.state()) || !RunSnapshot.COMPLETED.equals(candidate.state())) {
            return "incomplete-comparison";
        }
        if (!sameConfiguration(baseline, candidate)) {
            return "incomparable-parameters";
        }
        if (baseline.counters().sent() < 10 || candidate.counters().sent() < 10) {
            return "small-sample";
        }
        return "measured-comparison";
    }
}
