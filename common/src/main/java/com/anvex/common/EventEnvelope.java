package com.anvex.common;

import java.time.Instant;
import java.util.Map;

public record EventEnvelope(
        String eventId,
        String type,
        Instant occurredAt,
        long sequence,
        Map<String, Object> payload
) {
}
