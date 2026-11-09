package com.anvex.defender.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.anvex.defender.config.DefenderProperties;

class EventQueueServiceTest {

    private DefenderProperties smallQueue() {
        DefenderProperties properties = new DefenderProperties();
        properties.getQueue().setCapacity(10);
        properties.getQueue().setCriticalCapacity(2);
        return properties;
    }

    @Test
    void telemetrySaturationAggregatesInsteadOfFailing() {
        EventQueueService service = new EventQueueService(smallQueue());
        for (int i = 0; i < 25; i++) {
            service.publishTelemetry(SecurityEvent.of(SecurityEvent.LOGIN_REQUEST, Instant.now(), "10.0.0.1",
                    "evt-" + i, false, Map.of("username", "asha.raman")));
        }
        assertThat(service.aggregatedCount()).isPositive();
        assertThat(service.telemetryDepth()).isLessThanOrEqualTo(10);
        assertThat(service.criticalDroppedCount()).isZero();
    }

    @Test
    void criticalSaturationProducesABoundedOverloadResponse() {
        EventQueueService service = new EventQueueService(smallQueue());
        service.publishCritical(SecurityEvent.of(SecurityEvent.DETECTION_DECISION, Instant.now(), "10.0.0.1",
                "evt-1", true, Map.of()));
        service.publishCritical(SecurityEvent.of(SecurityEvent.DETECTION_DECISION, Instant.now(), "10.0.0.1",
                "evt-2", true, Map.of()));

        assertThatThrownBy(() -> service.publishCritical(SecurityEvent.of(SecurityEvent.DETECTION_DECISION,
                Instant.now(), "10.0.0.1", "evt-3", true, Map.of())))
                .isInstanceOf(ServiceOverloadedException.class);

        assertThat(service.criticalDroppedCount()).isEqualTo(1);
        assertThat(service.overloadResponseCount()).isEqualTo(1);
    }
}
