package com.anvex.defender.events;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.anvex.common.EventEnvelope;

/**
 * Live update delivery to the browser. Every subscriber has a bounded buffer; a subscriber that
 * cannot keep up is told to re-snapshot instead of silently missing events.
 */
@Component
public class LiveEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LiveEventPublisher.class);
    private static final int SUBSCRIBER_BUFFER = 64;
    private static final int REPLAY_BUFFER = 500;

    private final AtomicLong sequence = new AtomicLong();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Deque<EventEnvelope> replay = new ArrayDeque<>();

    public EventEnvelope publish(String type, Map<String, Object> payload, Instant occurredAt) {
        EventEnvelope envelope = new EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                type,
                occurredAt,
                sequence.incrementAndGet(),
                payload == null ? Map.of() : payload);
        synchronized (replay) {
            replay.addLast(envelope);
            while (replay.size() > REPLAY_BUFFER) {
                replay.removeFirst();
            }
        }
        for (Subscription subscription : subscriptions) {
            subscription.offer(envelope);
        }
        return envelope;
    }

    public long currentSequence() {
        return sequence.get();
    }

    public SseEmitter subscribe(String lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        try {
            emitter.send(SseEmitter.event().name("hello")
                    .data(Map.of("sequence", currentSequence(), "replayAvailable", replaySize())));
        } catch (IOException ex) {
            subscriptions.remove(subscription);
        }
        subscription.start();
        return emitter;
    }

    public int subscriberCount() {
        return subscriptions.size();
    }

    public int replaySize() {
        synchronized (replay) {
            return replay.size();
        }
    }

    private final class Subscription {
        private final SseEmitter emitter;
        private final ArrayBlockingQueue<EventEnvelope> buffer = new ArrayBlockingQueue<>(SUBSCRIBER_BUFFER);
        private volatile boolean lagging;
        private volatile boolean closed;
        private Thread pump;

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private void offer(EventEnvelope envelope) {
            if (buffer.offer(envelope)) {
                return;
            }
            lagging = true;
        }

        private void start() {
            pump = new Thread(() -> {
                try {
                    while (!closed) {
                        EventEnvelope envelope = buffer.poll(200, TimeUnit.MILLISECONDS);
                        if (envelope == null) {
                            if (lagging) {
                                lagging = false;
                                emitter.send(SseEmitter.event().name("lag")
                                        .data(Map.of("sequence", currentSequence(),
                                                "message", "Subscriber buffer overflowed: re-snapshot required.")));
                            }
                            continue;
                        }
                        emitter.send(SseEmitter.event().id(Long.toString(envelope.sequence()))
                                .name(envelope.type())
                                .data(envelope));
                    }
                } catch (IOException | IllegalStateException ex) {
                    log.debug("SSE subscriber closed: {}", ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    closed = true;
                    subscriptions.remove(this);
                }
            }, "anvex-sse-pump");
            pump.setDaemon(true);
            pump.start();
        }
    }
}
