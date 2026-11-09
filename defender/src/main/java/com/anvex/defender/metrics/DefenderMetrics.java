package com.anvex.defender.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Authoritative defender counters. Terminal request outcomes are mutually exclusive:
 * a request is either admitted to authentication, rate limited, source blocked or invalid/overloaded.
 *
 * <p>Counters are additionally grouped by the simulator run id. The run id is untrusted experiment
 * metadata: it is never used as a source identity and never influences detection, it only allows a
 * fair per-run comparison.</p>
 */
@Component
public class DefenderMetrics {

    private static final int MAX_TRACKED_RUNS = 50;

    private final AtomicLong incomingRequests = new AtomicLong();
    private final AtomicLong requestsReachingAuthentication = new AtomicLong();
    private final AtomicLong authenticationSuccess = new AtomicLong();
    private final AtomicLong authenticationFailure = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong();
    private final AtomicLong sourceBlocked = new AtomicLong();
    private final AtomicLong invalidOrOverload = new AtomicLong();
    private final AtomicLong detectionAlerts = new AtomicLong();

    private final Map<String, RunCounters> perRun = Collections.synchronizedMap(new LinkedHashMap<>());

    public void recordIncoming(String runId) {
        incomingRequests.incrementAndGet();
        run(runId).incoming.incrementAndGet();
    }

    public void recordAdmittedToAuthentication(String runId) {
        requestsReachingAuthentication.incrementAndGet();
        run(runId).admitted.incrementAndGet();
    }

    public void recordAuthenticationSuccess(String runId) {
        authenticationSuccess.incrementAndGet();
        run(runId).success.incrementAndGet();
    }

    public void recordAuthenticationFailure(String runId) {
        authenticationFailure.incrementAndGet();
        run(runId).failure.incrementAndGet();
    }

    public void recordRateLimited(String runId) {
        rateLimited.incrementAndGet();
        run(runId).rateLimited.incrementAndGet();
    }

    public void recordSourceBlocked(String runId) {
        sourceBlocked.incrementAndGet();
        run(runId).sourceBlocked.incrementAndGet();
    }

    public void recordInvalidOrOverload(String runId) {
        invalidOrOverload.incrementAndGet();
        run(runId).invalidOrOverload.incrementAndGet();
    }

    public void recordDetectionAlert() {
        detectionAlerts.incrementAndGet();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("incomingRequests", incomingRequests.get());
        values.put("requestsReachingAuthentication", requestsReachingAuthentication.get());
        values.put("authenticationSuccess", authenticationSuccess.get());
        values.put("authenticationFailure", authenticationFailure.get());
        values.put("rateLimitedRequests", rateLimited.get());
        values.put("sourceBlockedRequests", sourceBlocked.get());
        values.put("invalidOrOverloadRequests", invalidOrOverload.get());
        values.put("detectionAlerts", detectionAlerts.get());
        return values;
    }

    public Map<String, Long> snapshotForRun(String runId) {
        RunCounters counters = perRun.get(runId);
        if (counters == null) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("incomingRequests", counters.incoming.get());
        values.put("requestsReachingAuthentication", counters.admitted.get());
        values.put("authenticationSuccess", counters.success.get());
        values.put("authenticationFailure", counters.failure.get());
        values.put("rateLimitedRequests", counters.rateLimited.get());
        values.put("sourceBlockedRequests", counters.sourceBlocked.get());
        values.put("invalidOrOverloadRequests", counters.invalidOrOverload.get());
        return values;
    }

    public List<String> trackedRunIds() {
        synchronized (perRun) {
            return List.copyOf(perRun.keySet());
        }
    }

    public long incomingRequests() {
        return incomingRequests.get();
    }

    public long requestsReachingAuthentication() {
        return requestsReachingAuthentication.get();
    }

    private RunCounters run(String runId) {
        if (runId == null || runId.isBlank()) {
            return new RunCounters();
        }
        synchronized (perRun) {
            RunCounters counters = perRun.get(runId);
            if (counters == null) {
                counters = new RunCounters();
                perRun.put(runId, counters);
                while (perRun.size() > MAX_TRACKED_RUNS) {
                    String oldest = perRun.keySet().iterator().next();
                    perRun.remove(oldest);
                }
            }
            return counters;
        }
    }

    private static final class RunCounters {
        private final AtomicLong incoming = new AtomicLong();
        private final AtomicLong admitted = new AtomicLong();
        private final AtomicLong success = new AtomicLong();
        private final AtomicLong failure = new AtomicLong();
        private final AtomicLong rateLimited = new AtomicLong();
        private final AtomicLong sourceBlocked = new AtomicLong();
        private final AtomicLong invalidOrOverload = new AtomicLong();
    }
}
