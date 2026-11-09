package com.anvex.simulator.run;

import com.anvex.common.LatencySummary;
import com.anvex.common.RunConfig;
import com.anvex.common.RunCounters;
import com.anvex.common.RunSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live state of one simulation run. All counters are updated independently of the UI and are the
 * authoritative simulator-side numbers.
 */
public class ActiveRun {

    private final String runId;
    private final RunConfig config;
    private final String baseUrl;
    private final Instant startedAt;
    private final AtomicReference<Instant> endedAt = new AtomicReference<>();
    private final AtomicReference<String> state = new AtomicReference<>(RunSnapshot.RUNNING);
    private final AtomicReference<String> message = new AtomicReference<>();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean countingStopped = new AtomicBoolean();

    private final AtomicLong planned = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong transportErrors = new AtomicLong();
    private final AtomicLong authenticationSuccess = new AtomicLong();
    private final AtomicLong authenticationFailure = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong();
    private final AtomicLong sourceBlocked = new AtomicLong();
    private final AtomicLong invalidOrOverload = new AtomicLong();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
    private volatile java.util.concurrent.ScheduledFuture<?> tickTask;
    private volatile java.util.concurrent.ScheduledFuture<?> monitorTask;

    public ActiveRun(String runId, RunConfig config, String baseUrl, Instant startedAt) {
        this.runId = runId;
        this.config = config;
        this.baseUrl = baseUrl;
        this.startedAt = startedAt;
    }

    public String runId() {
        return runId;
    }

    public RunConfig config() {
        return config;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public String state() {
        return state.get();
    }

    public void transitionTo(String newState, String note) {
        state.set(newState);
        if (note != null) {
            message.set(note);
        }
        if (newState.equals(RunSnapshot.COMPLETED) || newState.equals(RunSnapshot.CANCELLED)
                || newState.equals(RunSnapshot.FAILED)) {
            endedAt.compareAndSet(null, Instant.now());
        }
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean countingStopped() {
        return countingStopped.get();
    }

    public void stopCounting() {
        countingStopped.set(true);
    }

    public void addPlanned(long count) {
        planned.addAndGet(count);
    }

    public void recordSent() {
        sent.incrementAndGet();
        inFlight.incrementAndGet();
    }

    public void recordCompleted() {
        completed.incrementAndGet();
        inFlight.decrementAndGet();
    }

    public void recordCancelled(long count) {
        cancelled.addAndGet(count);
    }

    /** A finished attempt that failed at the transport level (no defender outcome). */
    public void recordTransportError() {
        transportErrors.incrementAndGet();
    }

    public void recordOutcome(String status) {
        switch (status) {
            case "authenticated" -> authenticationSuccess.incrementAndGet();
            case "invalid_credentials" -> authenticationFailure.incrementAndGet();
            case "rate_limited_or_blocked" -> rateLimited.incrementAndGet();
            case "rate_limited" -> rateLimited.incrementAndGet();
            case "source_blocked" -> sourceBlocked.incrementAndGet();
            case "service_overloaded", "invalid_or_overload" -> invalidOrOverload.incrementAndGet();
            default -> {
            }
        }
    }

    public void recordLatency(double latencyMs) {
        if (latencies.size() < 2000) {
            latencies.add(latencyMs);
        }
    }

    public void setTasks(java.util.concurrent.ScheduledFuture<?> tick, java.util.concurrent.ScheduledFuture<?> monitor) {
        this.tickTask = tick;
        this.monitorTask = monitor;
    }

    public java.util.concurrent.ScheduledFuture<?> tickTask() {
        return tickTask;
    }

    public java.util.concurrent.ScheduledFuture<?> monitorTask() {
        return monitorTask;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public RunCounters counters() {
        return new RunCounters(planned.get(), sent.get(), completed.get(), cancelled.get(), transportErrors.get(),
                authenticationSuccess.get(), authenticationFailure.get(), rateLimited.get(), sourceBlocked.get(),
                invalidOrOverload.get());
    }

    public LatencySummary latency() {
        List<Double> snapshot;
        synchronized (latencies) {
            snapshot = new ArrayList<>(latencies);
        }
        if (snapshot.isEmpty()) {
            return LatencySummary.empty();
        }
        snapshot.sort(Double::compareTo);
        double sum = 0;
        for (double value : snapshot) {
            sum += value;
        }
        return new LatencySummary(
                snapshot.size(),
                round(sum / snapshot.size()),
                round(percentile(snapshot, 0.50)),
                round(percentile(snapshot, 0.95)),
                round(snapshot.get(snapshot.size() - 1)));
    }

    public double achievedRate() {
        Instant end = endedAt.get() == null ? Instant.now() : endedAt.get();
        double seconds = Duration.between(startedAt, end).toMillis() / 1000.0;
        if (seconds <= 0) {
            return 0;
        }
        return round(sent.get() / seconds);
    }

    public RunSnapshot snapshot() {
        return new RunSnapshot(runId, state.get(), config.scenarioId(), startedAt, endedAt.get(), counters(), latency(),
                achievedRate(), baseUrl, config, message.get());
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
