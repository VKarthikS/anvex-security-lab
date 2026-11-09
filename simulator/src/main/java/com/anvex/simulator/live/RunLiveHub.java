package com.anvex.simulator.live;

import java.io.IOException;
import java.time.Instant;
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
 * Live run progress for the simulator interface. Subscribers use a bounded buffer and are told to
 * re-snapshot when they fall behind.
 */
@Component
public class RunLiveHub {

    private static final Logger log = LoggerFactory.getLogger(RunLiveHub.class);
    private static final int SUBSCRIBER_BUFFER = 32;

    private final AtomicLong sequence = new AtomicLong();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public EventEnvelope publish(String type, Map<String, Object> payload) {
        EventEnvelope envelope = new EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                type,
                Instant.now(),
                sequence.incrementAndGet(),
                payload == null ? Map.of() : payload);
        for (Subscription subscription : subscriptions) {
            subscription.offer(envelope);
        }
        return envelope;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        try {
            emitter.send(SseEmitter.event().name("hello").data(Map.of("sequence", sequence.get())));
        } catch (IOException ex) {
            subscriptions.remove(subscription);
        }
        subscription.start();
        return emitter;
    }

    public int subscriberCount() {
        return subscriptions.size();
    }

    private final class Subscription {
        private final SseEmitter emitter;
        private final ArrayBlockingQueue<EventEnvelope> buffer = new ArrayBlockingQueue<>(SUBSCRIBER_BUFFER);
        private volatile boolean lagging;
        private volatile boolean closed;

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private void offer(EventEnvelope envelope) {
            if (!buffer.offer(envelope)) {
                lagging = true;
            }
        }

        private void start() {
            Thread pump = new Thread(() -> {
                try {
                    while (!closed) {
                        EventEnvelope envelope = buffer.poll(200, TimeUnit.MILLISECONDS);
                        if (envelope == null) {
                            if (lagging) {
                                lagging = false;
                                emitter.send(SseEmitter.event().name("lag")
                                        .data(Map.of("message", "Run progress buffer overflowed: re-snapshot required.")));
                            }
                            continue;
                        }
                        emitter.send(SseEmitter.event().id(Long.toString(envelope.sequence()))
                                .name(envelope.type())
                                .data(envelope));
                    }
                } catch (IOException | IllegalStateException ex) {
                    log.debug("Simulator SSE subscriber closed: {}", ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    closed = true;
                    subscriptions.remove(this);
                }
            }, "anvex-sim-sse-pump");
            pump.setDaemon(true);
            pump.start();
        }
    }
}
