package com.anvex.defender.detection;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Rolling activity for one source. Source-level and source/account-level counters are tracked
 * separately: ten usernames attempted from one IP count towards that source's activity, while the
 * repeated-guessing rule looks at one (source, account) pair.
 *
 * <p>All mutation happens while the owning source lock is held.</p>
 */
public class ActivityState {

    private final Deque<Instant> incomingRequests = new ArrayDeque<>();
    private final Deque<Instant> failures = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> failuresByAccount = new HashMap<>();

    private long totalIncoming;
    private long totalFailures;
    private long totalSuccesses;
    private Instant lastActivity;

    public void recordIncoming(Instant now) {
        incomingRequests.addLast(now);
        totalIncoming++;
        lastActivity = now;
    }

    public void recordFailure(String username, Instant now) {
        failures.addLast(now);
        failuresByAccount.computeIfAbsent(username, key -> new ArrayDeque<>()).addLast(now);
        totalFailures++;
        lastActivity = now;
    }

    public void recordSuccess(Instant now) {
        totalSuccesses++;
        lastActivity = now;
    }

    public int countIncomingSince(Instant windowStart) {
        return countSince(incomingRequests, windowStart);
    }

    public int countFailuresSince(Instant windowStart) {
        return countSince(failures, windowStart);
    }

    public int countAccountFailuresSince(String username, Instant windowStart) {
        Deque<Instant> accountFailures = failuresByAccount.get(username);
        return accountFailures == null ? 0 : countSince(accountFailures, windowStart);
    }

    public int countDistinctAccountsSince(Instant windowStart) {
        int distinct = 0;
        for (Map.Entry<String, Deque<Instant>> entry : failuresByAccount.entrySet()) {
            if (countSince(entry.getValue(), windowStart) > 0) {
                distinct++;
            }
        }
        return distinct;
    }

    /** Removes timestamps that can no longer influence any configured window. */
    public void prune(Instant cutoff) {
        removeOlderThan(incomingRequests, cutoff);
        removeOlderThan(failures, cutoff);
        failuresByAccount.entrySet().removeIf(entry -> {
            removeOlderThan(entry.getValue(), cutoff);
            return entry.getValue().isEmpty();
        });
    }

    private static int countSince(Deque<Instant> timestamps, Instant windowStart) {
        int count = 0;
        for (Instant timestamp : timestamps) {
            if (!timestamp.isBefore(windowStart)) {
                count++;
            }
        }
        return count;
    }

    private static void removeOlderThan(Deque<Instant> timestamps, Instant cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.removeFirst();
        }
    }

    public long totalIncoming() {
        return totalIncoming;
    }

    public long totalFailures() {
        return totalFailures;
    }

    public long totalSuccesses() {
        return totalSuccesses;
    }

    public int failuresByAccountSize() {
        return failuresByAccount.size();
    }

    /** Distinct usernames with at least one failure inside the window. */
    public java.util.List<String> distinctAccountsSince(Instant windowStart) {
        java.util.List<String> accounts = new java.util.ArrayList<>();
        for (Map.Entry<String, Deque<Instant>> entry : failuresByAccount.entrySet()) {
            if (countSince(entry.getValue(), windowStart) > 0) {
                accounts.add(entry.getKey());
            }
        }
        return accounts;
    }

    /** All usernames that have produced a failure at some point in the retained history. */
    public java.util.Set<String> trackedAccounts() {
        return java.util.Set.copyOf(failuresByAccount.keySet());
    }

    public Instant lastActivity() {
        return lastActivity;
    }
}
