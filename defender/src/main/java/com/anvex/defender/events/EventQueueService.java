package com.anvex.defender.events;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.anvex.defender.config.DefenderProperties;

/**
 * The bounded producer side of the event pipeline.
 *
 * <p>Two queues are used. Routine telemetry goes into a large bounded queue; when that queue is
 * saturated, repetitive telemetry is aggregated with explicit coverage markers and an aggregation
 * counter is incremented. Decisions and analyst actions use a separate bounded critical queue; if
 * that queue is exhausted as well the caller receives a bounded overload response instead of
 * silent evidence loss.</p>
 */
@Component
public class EventQueueService {

    private final BlockingQueue<SecurityEvent> telemetryQueue;
    private final BlockingQueue<SecurityEvent> criticalQueue;
    private final Map<String, Aggregation> aggregations = new ConcurrentHashMap<>();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong aggregated = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong criticalDropped = new AtomicLong();
    private final AtomicLong overloadResponses = new AtomicLong();

    public EventQueueService(DefenderProperties properties) {
        this.telemetryQueue = new ArrayBlockingQueue<>(properties.getQueue().getCapacity());
        this.criticalQueue = new ArrayBlockingQueue<>(properties.getQueue().getCriticalCapacity());
    }

    /** Publishes routine telemetry. Never throws: saturation is aggregated or counted. */
    public void publishTelemetry(SecurityEvent event) {
        if (telemetryQueue.offer(event)) {
            published.incrementAndGet();
            return;
        }
        Aggregation aggregation = aggregations.computeIfAbsent(event.type(), key -> new Aggregation(key));
        aggregation.add(event);
        aggregated.incrementAndGet();
    }

    /**
     * Publishes a critical event (decision, block transition, analyst action).
     *
     * @throws ServiceOverloadedException when the critical queue is saturated
     */
    public void publishCritical(SecurityEvent event) {
        if (criticalQueue.offer(event)) {
            published.incrementAndGet();
            return;
        }
        criticalDropped.incrementAndGet();
        overloadResponses.incrementAndGet();
        throw new ServiceOverloadedException("The critical event queue is saturated; the request was rejected.");
    }

    public SecurityEvent pollTelemetry(long timeoutMillis) throws InterruptedException {
        return telemetryQueue.poll(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public SecurityEvent pollCritical(long timeoutMillis) throws InterruptedException {
        return criticalQueue.poll(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Drains aggregated telemetry back into the queue when space is available again. */
    public void flushAggregations() {
        aggregations.forEach((type, aggregation) -> {
            SecurityEvent snapshot = aggregation.drain();
            if (snapshot != null && !telemetryQueue.offer(snapshot)) {
                aggregation.restore(snapshot);
            }
        });
    }

    public int telemetryDepth() {
        return telemetryQueue.size();
    }

    public int criticalDepth() {
        return criticalQueue.size();
    }

    public boolean criticalDepthIsZero() {
        return criticalQueue.isEmpty();
    }

    public int telemetryCapacity() {
        return telemetryQueue.size() + telemetryQueue.remainingCapacity();
    }

    public int criticalCapacity() {
        return criticalQueue.size() + criticalQueue.remainingCapacity();
    }

    public long publishedCount() {
        return published.get();
    }

    public long aggregatedCount() {
        return aggregated.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long criticalDroppedCount() {
        return criticalDropped.get();
    }

    public long overloadResponseCount() {
        return overloadResponses.get();
    }

    public int pendingAggregationTypes() {
        return aggregations.size();
    }

    private static final class Aggregation {
        private final String type;
        private long count;
        private Instant firstSeen;
        private Instant lastSeen;
        private String sourceIp;

        private Aggregation(String type) {
            this.type = type;
        }

        private synchronized void add(SecurityEvent event) {
            count++;
            firstSeen = firstSeen == null ? event.occurredAt() : firstSeen;
            lastSeen = event.occurredAt();
            sourceIp = event.sourceIp();
        }

        private synchronized SecurityEvent drain() {
            if (count == 0) {
                return null;
            }
            SecurityEvent event = SecurityEvent.of(SecurityEvent.AGGREGATED_TELEMETRY, lastSeen, sourceIp, null, false,
                    Map.of("aggregatedType", type,
                            "count", count,
                            "firstSeen", firstSeen.toString(),
                            "lastSeen", lastSeen.toString(),
                            "coverage", "aggregate-with-gaps"));
            count = 0;
            firstSeen = null;
            lastSeen = null;
            return event;
        }

        private synchronized void restore(SecurityEvent event) {
            count = Math.max(count, 1);
        }
    }
}
