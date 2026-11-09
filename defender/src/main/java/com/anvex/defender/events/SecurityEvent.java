package com.anvex.defender.events;

import java.time.Instant;
import java.util.Map;

/**
 * An immutable event produced by the security path. Events are handed to the bounded queue and are
 * never mutated afterwards.
 */
public record SecurityEvent(
        String eventId,
        String type,
        Instant occurredAt,
        String sourceIp,
        String requestId,
        boolean critical,
        boolean aggregated,
        Map<String, Object> payload
) {
    public static final String LOGIN_REQUEST = "LOGIN_REQUEST";
    public static final String AUTH_SUCCESS = "AUTH_SUCCESS";
    public static final String AUTH_FAILURE = "AUTH_FAILURE";
    public static final String DETECTION_DECISION = "DETECTION_DECISION";
    public static final String ALERT_UPSERT = "ALERT_UPSERT";
    public static final String BLOCK_CREATED = "BLOCK_CREATED";
    public static final String BLOCK_RELEASED = "BLOCK_RELEASED";
    public static final String PROTECTION_CHANGED = "PROTECTION_CHANGED";
    public static final String ANALYST_ACTION = "ANALYST_ACTION";
    public static final String SETTINGS_CHANGED = "SETTINGS_CHANGED";
    public static final String AGGREGATED_TELEMETRY = "AGGREGATED_TELEMETRY";

    public static SecurityEvent of(String type, Instant occurredAt, String sourceIp, String requestId,
                                   boolean critical, Map<String, Object> payload) {
        return new SecurityEvent(java.util.UUID.randomUUID().toString(), type, occurredAt, sourceIp, requestId,
                critical, false, payload == null ? Map.of() : Map.copyOf(payload));
    }
}
