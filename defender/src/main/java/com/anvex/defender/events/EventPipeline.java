package com.anvex.defender.events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.anvex.defender.config.DefenderProperties;
import com.anvex.defender.persistence.EventPersistenceService;

/**
 * The bounded producer/consumer event pipeline.
 *
 * <p>Named worker threads consume the bounded queues: {@code anvex-event-persist-N} writes batches
 * to H2 and {@code anvex-event-publish-1} fans events out to the live-update hub. The security path
 * never waits for these workers, so protection keeps running when persistence or UI delivery is
 * slow. Shutdown drains the queues with a bounded timeout.</p>
 */
@Component
public class EventPipeline implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventPipeline.class);

    private final EventQueueService queueService;
    private final EventPersistenceService persistenceService;
    private final LiveEventPublisher livePublisher;
    private final DefenderProperties properties;
    private final BlockingQueue<SecurityEvent> publishQueue = new ArrayBlockingQueue<>(2000);
    private final List<Thread> workers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong persistedCount = new AtomicLong();
    private final AtomicLong publicationDrops = new AtomicLong();
    private final AtomicLong persistenceErrors = new AtomicLong();
    private final AtomicReference<Instant> lastPersistedAt = new AtomicReference<>();

    public EventPipeline(EventQueueService queueService,
                         EventPersistenceService persistenceService,
                         LiveEventPublisher livePublisher,
                         DefenderProperties properties) {
        this.queueService = queueService;
        this.persistenceService = persistenceService;
        this.livePublisher = livePublisher;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int persistWorkers = Math.max(1, properties.getQueue().getWorkerThreads());
        for (int index = 1; index <= persistWorkers; index++) {
            Thread worker = new Thread(() -> persistLoop(), "anvex-event-persist-" + index);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
        Thread publisher = new Thread(() -> publishLoop(), "anvex-event-publish-1");
        publisher.setDaemon(true);
        workers.add(publisher);
        publisher.start();
        log.info("Event pipeline started with {} persistence worker(s) and 1 publication worker", persistWorkers);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Duration timeout = properties.getQueue().getShutdownDrainTimeout();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline
                && (queueService.telemetryDepth() > 0 || queueService.criticalDepth() > 0 || !publishQueue.isEmpty())) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        workers.forEach(Thread::interrupt);
        log.info("Event pipeline stopped; persisted {} event(s), {} publication(s) dropped", persistedCount.get(),
                publicationDrops.get());
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void persistLoop() {
        List<SecurityEvent> batch = new ArrayList<>();
        while (running.get() || !queueService.criticalDepthIsZero() || queueService.telemetryDepth() > 0) {
            try {
                SecurityEvent critical = queueService.pollCritical(50);
                if (critical != null) {
                    persistBatch(List.of(critical));
                    continue;
                }
                SecurityEvent telemetry = queueService.pollTelemetry(50);
                if (telemetry != null) {
                    batch.add(telemetry);
                    int batchSize = Math.max(1, properties.getQueue().getBatchSize());
                    while (batch.size() < batchSize) {
                        SecurityEvent next = queueService.pollTelemetry(0);
                        if (next == null) {
                            break;
                        }
                        batch.add(next);
                    }
                    persistBatch(List.copyOf(batch));
                    batch.clear();
                    queueService.flushAggregations();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                persistenceErrors.incrementAndGet();
                log.error("Event persistence worker error: {}", ex.getMessage());
            }
        }
    }

    private void persistBatch(List<SecurityEvent> events) {
        try {
            persistenceService.persistBatch(events);
            persistedCount.addAndGet(events.size());
            lastPersistedAt.set(Instant.now());
            for (SecurityEvent event : events) {
                offerForPublication(event);
            }
        } catch (RuntimeException ex) {
            persistenceErrors.incrementAndGet();
            log.error("Failed to persist a batch of {} event(s): {}", events.size(), ex.getMessage());
        }
    }

    private void offerForPublication(SecurityEvent event) {
        if (!publishQueue.offer(event)) {
            publicationDrops.incrementAndGet();
        }
    }

    private void publishLoop() {
        while (running.get() || !publishQueue.isEmpty()) {
            try {
                SecurityEvent event = publishQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                livePublisher.publish(event.type(), event.payload(), event.occurredAt());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                log.error("Live publication worker error: {}", ex.getMessage());
            }
        }
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("running", running.get());
        body.put("telemetryQueueDepth", queueService.telemetryDepth());
        body.put("telemetryQueueCapacity", queueService.telemetryCapacity());
        body.put("criticalQueueDepth", queueService.criticalDepth());
        body.put("criticalQueueCapacity", queueService.criticalCapacity());
        body.put("publishQueueDepth", publishQueue.size());
        body.put("publishedEvents", queueService.publishedCount());
        body.put("aggregatedEvents", queueService.aggregatedCount());
        body.put("droppedEvents", queueService.droppedCount());
        body.put("criticalDroppedEvents", queueService.criticalDroppedCount());
        body.put("overloadResponses", queueService.overloadResponseCount());
        body.put("persistedEvents", persistedCount.get());
        body.put("publicationDrops", publicationDrops.get());
        body.put("persistenceErrors", persistenceErrors.get());
        body.put("lastPersistedAt", lastPersistedAt.get());
        body.put("subscriberCount", livePublisher.subscriberCount());
        body.put("workerStates", workers.stream()
                .map(thread -> Map.of("name", thread.getName(), "state", thread.getState().name()))
                .toList());
        return body;
    }
}
