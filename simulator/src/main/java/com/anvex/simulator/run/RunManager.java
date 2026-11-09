package com.anvex.simulator.run;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.RunSnapshot;
import com.anvex.simulator.client.DefenderClient;
import com.anvex.simulator.client.DefenderLoginResult;
import com.anvex.simulator.client.TargetHandshake;
import com.anvex.simulator.config.SimulatorProperties;
import com.anvex.simulator.config.TargetSettings;
import com.anvex.simulator.live.RunLiveHub;
import com.anvex.simulator.scenario.ScenarioCatalog;
import com.anvex.simulator.scenario.ScenarioRequest;
import com.anvex.simulator.scenario.ScenarioStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the simulation run lifecycle: exactly one active run at a time, bounded scheduling,
 * prompt cancellation and honest reconciliation of sent / completed / cancelled requests.
 */
@Service
public class RunManager {

    private static final Logger log = LoggerFactory.getLogger(RunManager.class);

    private final SimulatorProperties properties;
    private final TargetSettings targetSettings;
    private final DefenderClient defenderClient;
    private final ScenarioCatalog catalog;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final Clock clock;
    private final RunLiveHub liveHub;

    private final AtomicReference<ActiveRun> active = new AtomicReference<>();
    private final List<RunSnapshot> history = Collections.synchronizedList(new ArrayList<>());

    // Correlation uses server-issued request IDs observed in real defender responses, never the
    // client-supplied runId as proof of an attack. Retain a bounded set for recent experiments.
    private static final int MAX_CORRELATION_RUNS = 40;
    private final Map<String, Set<String>> requestIdsByRun = new LinkedHashMap<>();

    private void trackRun(String runId) {
        synchronized (requestIdsByRun) {
            requestIdsByRun.put(runId, ConcurrentHashMap.newKeySet());
            while (requestIdsByRun.size() > MAX_CORRELATION_RUNS) {
                requestIdsByRun.remove(requestIdsByRun.keySet().iterator().next());
            }
        }
    }

    /** Only request IDs actually returned by the defender are eligible for evidence correlation. */
    public Optional<Set<String>> observedRequestIds(String runId) {
        synchronized (requestIdsByRun) {
            Set<String> ids = requestIdsByRun.get(runId);
            return ids == null ? Optional.empty() : Optional.of(Set.copyOf(ids));
        }
    }

    private void trackRequestId(String runId, String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        synchronized (requestIdsByRun) {
            Set<String> ids = requestIdsByRun.get(runId);
            if (ids != null) ids.add(requestId);
        }
    }

    public RunManager(SimulatorProperties properties,
                      TargetSettings targetSettings,
                      DefenderClient defenderClient,
                      ScenarioCatalog catalog,
                      ScheduledExecutorService scenarioScheduler,
                      ExecutorService scenarioWorkers,
                      Clock clock,
                      RunLiveHub liveHub) {
        this.properties = properties;
        this.targetSettings = targetSettings;
        this.defenderClient = defenderClient;
        this.catalog = catalog;
        this.scheduler = scenarioScheduler;
        this.workers = scenarioWorkers;
        this.clock = clock;
        this.liveHub = liveHub;
    }

    public Optional<RunSnapshot> current() {
        ActiveRun run = active.get();
        return run == null ? Optional.empty() : Optional.of(run.snapshot());
    }

    public List<RunSnapshot> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public Optional<RunSnapshot> find(String runId) {
        ActiveRun run = active.get();
        if (run != null && run.runId().equals(runId)) {
            return Optional.of(run.snapshot());
        }
        synchronized (history) {
            return history.stream().filter(snapshot -> runId.equals(snapshot.runId())).findFirst();
        }
    }

    public RunSnapshot start(RunConfig config) {
        validate(config);
        if (isBusy(active.get())) {
            throw new RunInProgressException(active.get().runId());
        }
        ScenarioStrategy strategy = catalog.require(config.scenarioId());
        String baseUrl = targetSettings.baseUrl();
        String runId = UUID.randomUUID().toString();
        ActiveRun run = new ActiveRun(runId, config, baseUrl, clock.instant());
        trackRun(runId);

        TargetHandshake handshake = defenderClient.handshake(baseUrl);
        if (!handshake.reachable() || !handshake.compatible()) {
            run.transitionTo(RunSnapshot.FAILED, handshake.message());
            active.set(run);
            history.add(run.snapshot());
            liveHub.publish("RUN_FAILED", Map.of("runId", runId, "message", handshake.message()));
            return run.snapshot();
        }

        List<DemoAccount> accounts = defenderClient.fetchDemoAccounts(baseUrl);
        List<ScenarioRequest> plan = strategy.plan(config, accounts);
        run.addPlanned(plan.size());

        active.set(run);
        liveHub.publish("RUN_STARTED", Map.of("runId", runId, "scenarioId", config.scenarioId(), "target", baseUrl));
        long periodNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, config.ratePerSecond());
        ScheduledFuture<?> tickFuture = scheduler.scheduleAtFixedRate(() -> tick(run, plan), periodNanos, periodNanos,
                TimeUnit.NANOSECONDS);
        ScheduledFuture<?> monitor = scheduler.scheduleAtFixedRate(() -> monitor(run), 200, 200, TimeUnit.MILLISECONDS);
        run.setTasks(tickFuture, monitor);
        return run.snapshot();
    }

    public RunSnapshot stop(String runId) {
        ActiveRun run = active.get();
        if (run == null || !run.runId().equals(runId)) {
            throw new RunNotFoundException(runId);
        }
        if (RunSnapshot.COMPLETED.equals(run.state()) || RunSnapshot.CANCELLED.equals(run.state())
                || RunSnapshot.FAILED.equals(run.state())) {
            return run.snapshot();
        }
        run.requestStop();
        run.transitionTo(RunSnapshot.STOPPING, "Stop requested; waiting for in-flight requests to settle.");
        cancelTick(run);
        return run.snapshot();
    }

    public RunSnapshot manualLogin(String username, String password) {
        RunConfig config = new RunConfig("manual-login", 1, 1, List.of(username), 1, 1);
        String baseUrl = targetSettings.baseUrl();
        String runId = UUID.randomUUID().toString();
        ActiveRun run = new ActiveRun(runId, config, baseUrl, clock.instant());
        trackRun(runId);
        run.addPlanned(1);
        TargetHandshake handshake = defenderClient.handshake(baseUrl);
        if (!handshake.reachable() || !handshake.compatible()) {
            run.transitionTo(RunSnapshot.FAILED, handshake.message());
            active.set(run);
            history.add(run.snapshot());
            return run.snapshot();
        }
        run.recordSent();
        DefenderLoginResult result = defenderClient.login(baseUrl, username, password, runId);
        applyResult(run, result);
        run.recordCompleted();
        run.transitionTo(RunSnapshot.COMPLETED, "Manual login completed.");
        active.set(run);
        history.add(run.snapshot());
        return run.snapshot();
    }

    private void tick(ActiveRun run, List<ScenarioRequest> plan) {
        try {
            if (run.stopRequested() || run.countingStopped()) {
                return;
            }
            if (elapsedMillis(run) >= run.config().durationSeconds() * 1000L) {
                run.stopCounting();
                return;
            }
            while (run.inFlight() < run.config().concurrency()
                    && run.counters().sent() < plan.size()
                    && !run.stopRequested()) {
                int index = (int) run.counters().sent();
                ScenarioRequest request = plan.get(index);
                run.recordSent();
                workers.submit(() -> execute(run, request));
            }
        } catch (RuntimeException ex) {
            log.warn("Simulation tick failed for run {}: {}", run.runId(), ex.getMessage());
        }
    }

    private void execute(ActiveRun run, ScenarioRequest request) {
        try {
            DefenderLoginResult result = defenderClient.login(run.baseUrl(), request.username(), request.password(),
                    run.runId());
            applyResult(run, result);
            run.recordCompleted();
        } catch (RuntimeException ex) {
            run.recordTransportError();
            run.recordCompleted();
        }
    }

    private void applyResult(ActiveRun run, DefenderLoginResult result) {
        if (!result.transportFailure()) trackRequestId(run.runId(), result.requestId());
        if (result.transportFailure()) {
            run.recordTransportError();
            return;
        }
        run.recordOutcome(result.status());
        run.recordLatency(result.latencyMs());
    }

    private void monitor(ActiveRun run) {
        try {
            boolean timeReached = elapsedMillis(run) >= run.config().durationSeconds() * 1000L;
            boolean allSent = run.counters().sent() >= run.counters().planned();
            liveHub.publish("RUN_PROGRESS", Map.of(
                    "runId", run.runId(),
                    "state", run.state(),
                    "counters", run.counters(),
                    "latency", run.latency(),
                    "achievedRatePerSecond", run.achievedRate()));
            if (run.stopRequested()) {
                run.stopCounting();
                if (run.inFlight() == 0) {
                    long unsent = Math.max(0, run.counters().planned() - run.counters().sent());
                    run.recordCancelled(unsent);
                    run.transitionTo(RunSnapshot.CANCELLED, "Run cancelled; " + unsent + " planned request(s) were never sent.");
                    cancelTasks(run);
                    history.add(run.snapshot());
                    liveHub.publish("RUN_STATE", Map.of("runId", run.runId(), "state", run.state(),
                            "counters", run.counters(), "latency", run.latency()));
                }
                return;
            }
            if ((timeReached || allSent) && run.inFlight() == 0) {
                run.stopCounting();
                long unsent = Math.max(0, run.counters().planned() - run.counters().sent());
                run.recordCancelled(unsent);
                run.transitionTo(RunSnapshot.COMPLETED, "Run completed.");
                cancelTasks(run);
                history.add(run.snapshot());
                liveHub.publish("RUN_STATE", Map.of("runId", run.runId(), "state", run.state(),
                        "counters", run.counters(), "latency", run.latency()));
            }
        } catch (RuntimeException ex) {
            log.warn("Run monitor failed for run {}: {}", run.runId(), ex.getMessage());
        }
    }

    private void cancelTick(ActiveRun run) {
        ScheduledFuture<?> tick = run.tickTask();
        if (tick != null) {
            tick.cancel(false);
        }
    }

    private void cancelTasks(ActiveRun run) {
        cancelTick(run);
        ScheduledFuture<?> monitor = run.monitorTask();
        if (monitor != null) {
            monitor.cancel(false);
        }
    }

    private long elapsedMillis(ActiveRun run) {
        return Duration.between(run.startedAt(), clock.instant()).toMillis();
    }

    private boolean isBusy(ActiveRun run) {
        if (run == null) {
            return false;
        }
        String state = run.state();
        return RunSnapshot.RUNNING.equals(state) || RunSnapshot.STOPPING.equals(state);
    }

    private void validate(RunConfig config) {
        if (config.ratePerSecond() > properties.getRateCap()) {
            throw new IllegalArgumentException("ratePerSecond exceeds the configured cap of " + properties.getRateCap());
        }
        if (config.durationSeconds() > properties.getDurationCap()) {
            throw new IllegalArgumentException("durationSeconds exceeds the configured cap of " + properties.getDurationCap());
        }
        if (config.requestLimit() > properties.getRequestCap()) {
            throw new IllegalArgumentException("requestLimit exceeds the configured cap of " + properties.getRequestCap());
        }
        if (config.concurrency() > properties.getConcurrencyCap()) {
            throw new IllegalArgumentException("concurrency exceeds the configured cap of " + properties.getConcurrencyCap());
        }
        if (config.ratePerSecond() < 1 || config.durationSeconds() < 1 || config.requestLimit() < 1
                || config.concurrency() < 1) {
            throw new IllegalArgumentException("rate, duration, request limit and concurrency must all be at least 1");
        }
    }

    public static class RunInProgressException extends RuntimeException {
        public RunInProgressException(String activeRunId) {
            super("A simulation run is already active: " + activeRunId);
        }
    }

    public static class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(String runId) {
            super("Unknown run: " + runId);
        }
    }
}
