package com.anvex.defender.detection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.anvex.defender.config.DefenderProperties;

/**
 * The detection engine.
 *
 * <p>Each source owns a lock and its rolling activity state. The counter update, the rule
 * evaluation and the decision snapshot therefore form one atomic step per source: a
 * {@link ConcurrentHashMap} alone would allow a threshold to be crossed twice concurrently, because
 * reading a count, comparing it and writing the incremented value are separate operations.</p>
 *
 * <p>Lock order: policy snapshot (lock-free read) → source lock. No database or UI work happens
 * while a source lock is held.</p>
 */
@Service
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);
    private static final int PRUNE_INTERVAL = 256;

    private final PolicyService policyService;
    private final List<DetectionRule> rules;
    private final DefenderProperties properties;
    private final ConcurrentHashMap<String, SourceActivity> sources = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();

    public DetectionEngine(PolicyService policyService, List<DetectionRule> rules, DefenderProperties properties) {
        this.policyService = policyService;
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    /** Counts an incoming login request and evaluates request-rate rules. */
    public DetectionOutcome recordIncoming(String sourceIp, Instant now, String eventId) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = activityFor(sourceIp);
        activity.lock().lock();
        try {
            activity.state().recordIncoming(now);
            activity.state().prune(cutoff(now, policy));
            RuleEvaluationContext context = context(sourceIp, null, now, policy, eventId, activity.state());
            return evaluate(context);
        } finally {
            activity.lock().unlock();
        }
    }

    /** Records a failed authentication and evaluates the failure-based rules. */
    public DetectionOutcome recordFailure(String sourceIp, String username, Instant now, String eventId) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = activityFor(sourceIp);
        activity.lock().lock();
        try {
            activity.state().recordFailure(username, now);
            activity.state().prune(cutoff(now, policy));
            RuleEvaluationContext context = context(sourceIp, username, now, policy, eventId, activity.state());
            return evaluate(context);
        } finally {
            activity.lock().unlock();
        }
    }

    /**
     * Records a successful authentication. A success never erases earlier failures: the rolling
     * counters simply keep the failure samples until they expire.
     */
    public DetectionOutcome recordSuccess(String sourceIp, String username, Instant now, String eventId) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = activityFor(sourceIp);
        activity.lock().lock();
        try {
            activity.state().recordSuccess(now);
            activity.state().prune(cutoff(now, policy));
            return DetectionOutcome.none(now);
        } finally {
            activity.lock().unlock();
        }
    }

    public DetectionOutcome evaluateNow(String sourceIp, Instant now) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = sources.get(sourceIp);
        if (activity == null) {
            return DetectionOutcome.none(now);
        }
        activity.lock().lock();
        try {
            RuleEvaluationContext context = context(sourceIp, null, now, policy, "reevaluation", activity.state());
            return evaluate(context);
        } finally {
            activity.lock().unlock();
        }
    }

    /** Restores a persisted observation into the rolling state (used after a restart). */
    public void seedIncoming(String sourceIp, Instant observedAt) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = activityFor(sourceIp);
        activity.lock().lock();
        try {
            activity.state().recordIncoming(observedAt);
            activity.state().prune(cutoff(observedAt, policy));
        } finally {
            activity.lock().unlock();
        }
    }

    /** Restores a persisted failure observation into the rolling state. */
    public void seedFailure(String sourceIp, String username, Instant observedAt) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = activityFor(sourceIp);
        activity.lock().lock();
        try {
            activity.state().recordFailure(username, observedAt);
            activity.state().prune(cutoff(observedAt, policy));
        } finally {
            activity.lock().unlock();
        }
    }

    /** Clears rolling counters for every source (analyst reset / comparison baseline). */
    public void resetCounters() {
        for (SourceActivity activity : sources.values()) {
            activity.lock().lock();
            try {
                activity.state().prune(Instant.EPOCH);
            } finally {
                activity.lock().unlock();
            }
        }
        sources.clear();
    }

    /**
     * Re-evaluates every rule that can apply to a source, including the account scoped rules. Used
     * when protection is switched on: still-valid rolling activity is enforced immediately, while
     * expired activity can never match.
     */
    public DetectionOutcome reevaluate(String sourceIp, Instant now) {
        PolicySnapshot policy = policyService.current();
        SourceActivity activity = sources.get(sourceIp);
        if (activity == null) {
            return DetectionOutcome.none(now);
        }
        activity.lock().lock();
        try {
            List<RuleMatch> matches = new ArrayList<>();
            matches.addAll(evaluate(context(sourceIp, null, now, policy, "reevaluation", activity.state())).matches());
            for (String account : activity.state().trackedAccounts()) {
                matches.addAll(evaluate(context(sourceIp, account, now, policy, "reevaluation", activity.state())).matches());
            }
            DetectionCategory primary = matches.stream()
                    .map(RuleMatch::category)
                    .min(Comparator.comparingInt(DetectionCategory::priority))
                    .orElse(DetectionCategory.NONE);
            return new DetectionOutcome(List.copyOf(matches), primary, now);
        } finally {
            activity.lock().unlock();
        }
    }

    /** Failed authentications from a source at or after the given instant. */
    public int failureCountSince(String sourceIp, Instant since) {
        SourceActivity activity = sources.get(sourceIp);
        if (activity == null) {
            return 0;
        }
        activity.lock().lock();
        try {
            return activity.state().countFailuresSince(since);
        } finally {
            activity.lock().unlock();
        }
    }

    /** Incoming requests from a source at or after the given instant. */
    public int incomingCountSince(String sourceIp, Instant since) {
        SourceActivity activity = sources.get(sourceIp);
        if (activity == null) {
            return 0;
        }
        activity.lock().lock();
        try {
            return activity.state().countIncomingSince(since);
        } finally {
            activity.lock().unlock();
        }
    }

    /** Distinct usernames that produced at least one failure inside the given window. */
    public List<String> distinctAccountsSince(String sourceIp, Instant windowStart) {
        SourceActivity activity = sources.get(sourceIp);
        if (activity == null) {
            return List.of();
        }
        activity.lock().lock();
        try {
            return List.copyOf(activity.state().distinctAccountsSince(windowStart));
        } finally {
            activity.lock().unlock();
        }
    }

    public Map<String, SourceSummary> summaries() {        Map<String, SourceSummary> summary = new ConcurrentHashMap<>();
        sources.forEach((ip, activity) -> {
            activity.lock().lock();
            try {
                ActivityState state = activity.state();
                summary.put(ip, new SourceSummary(
                        ip,
                        state.totalIncoming(),
                        state.totalFailures(),
                        state.totalSuccesses(),
                        state.lastActivity(),
                        state.failuresByAccountSize()));
            } finally {
                activity.lock().unlock();
            }
        });
        return summary;
    }

    private RuleEvaluationContext context(String sourceIp, String username, Instant now, PolicySnapshot policy,
                                          String eventId, ActivityState state) {
        Instant incomingStart = now.minusMillis(policy.rapidRequestWindowMillis());
        Instant sprayingStart = now.minusMillis(policy.sprayingWindowMillis());
        Instant guessingStart = now.minusMillis(policy.repeatedGuessingWindowMillis());
        return new RuleEvaluationContext(
                sourceIp,
                username,
                now,
                policy,
                eventId,
                state.countIncomingSince(incomingStart),
                incomingStart,
                state.countFailuresSince(sprayingStart),
                sprayingStart,
                username == null ? 0 : state.countAccountFailuresSince(username, guessingStart),
                guessingStart,
                state.countDistinctAccountsSince(sprayingStart),
                sprayingStart);
    }

    private DetectionOutcome evaluate(RuleEvaluationContext context) {
        List<RuleMatch> matches = new ArrayList<>();
        for (DetectionRule rule : rules) {
            rule.evaluate(context).ifPresent(matches::add);
        }
        DetectionCategory primary = matches.stream()
                .map(RuleMatch::category)
                .min(Comparator.comparingInt(DetectionCategory::priority))
                .orElse(DetectionCategory.NONE);
        return new DetectionOutcome(List.copyOf(matches), primary, context.now());
    }

    private SourceActivity activityFor(String sourceIp) {
        SourceActivity activity = sources.computeIfAbsent(sourceIp,
                ip -> new SourceActivity(new ReentrantLock(true), new ActivityState()));
        if (sources.size() > properties.getDetection().getMaxTrackedSources()) {
            evictOldest();
        }
        if (operations.incrementAndGet() % PRUNE_INTERVAL == 0) {
            pruneStaleSources();
        }
        return activity;
    }

    private void evictOldest() {
        Instant cutoff = Instant.now().minus(properties.getDetection().getStaleEntryRetention());
        sources.entrySet().removeIf(entry -> entry.getValue().state().lastActivity() == null
                || entry.getValue().state().lastActivity().isBefore(cutoff));
        if (sources.size() > properties.getDetection().getMaxTrackedSources()) {
            sources.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().state().lastActivity()))
                    .map(Map.Entry::getKey)
                    .ifPresent(key -> sources.remove(key));
        }
    }

    private void pruneStaleSources() {
        Instant cutoff = Instant.now().minus(properties.getDetection().getStaleEntryRetention());
        int before = sources.size();
        sources.entrySet().removeIf(entry -> entry.getValue().state().lastActivity() == null
                || entry.getValue().state().lastActivity().isBefore(cutoff));
        int removed = before - sources.size();
        if (removed > 0) {
            log.debug("Pruned {} stale source state(s)", removed);
        }
    }

    private static Instant cutoff(Instant now, PolicySnapshot policy) {
        long longest = Math.max(policy.repeatedGuessingWindowMillis(),
                Math.max(policy.sprayingWindowMillis(), policy.rapidRequestWindowMillis()));
        return now.minusMillis(longest);
    }

    private record SourceActivity(ReentrantLock lock, ActivityState state) {
    }
}
