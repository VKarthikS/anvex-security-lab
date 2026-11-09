package com.anvex.defender.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvex.defender.events.SecurityEvent;

/**
 * Writes events into H2. Routine telemetry is written in batches by the worker threads; the schema
 * is owned by Flyway.
 */
@Service
public class EventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EventPersistenceService.class);

    private final SecurityEventRepository securityEventRepository;
    private final EvidenceRepository evidenceRepository;
    private final AlertRepository alertRepository;
    private final SettingsVersionRepository settingsVersionRepository;
    private final AnalystActionRepository analystActionRepository;
    private final SourceBlockRepository sourceBlockRepository;
    private final DetectionObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public EventPersistenceService(SecurityEventRepository securityEventRepository,
                                   EvidenceRepository evidenceRepository,
                                   AlertRepository alertRepository,
                                   SettingsVersionRepository settingsVersionRepository,
                                   AnalystActionRepository analystActionRepository,
                                   SourceBlockRepository sourceBlockRepository,
                                   DetectionObservationRepository observationRepository,
                                   ObjectMapper objectMapper) {
        this.securityEventRepository = securityEventRepository;
        this.evidenceRepository = evidenceRepository;
        this.alertRepository = alertRepository;
        this.settingsVersionRepository = settingsVersionRepository;
        this.analystActionRepository = analystActionRepository;
        this.sourceBlockRepository = sourceBlockRepository;
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persistBatch(List<SecurityEvent> events) {
        for (SecurityEvent event : events) {
            try {
                persist(event);
            } catch (RuntimeException ex) {
                log.error("Failed to persist event {} ({}): {}", event.eventId(), event.type(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void persist(SecurityEvent event) {
        securityEventRepository.save(new Entities.SecurityEventEntity(
                event.eventId(),
                event.type(),
                event.occurredAt(),
                event.sourceIp(),
                event.requestId(),
                event.critical(),
                event.aggregated(),
                toJson(event.payload())));

        switch (event.type()) {
            case SecurityEvent.DETECTION_DECISION -> persistEvidence(event);
            case SecurityEvent.ALERT_UPSERT -> persistAlert(event);
            case SecurityEvent.BLOCK_CREATED, SecurityEvent.BLOCK_RELEASED -> persistBlock(event);
            case SecurityEvent.PROTECTION_CHANGED, SecurityEvent.SETTINGS_CHANGED -> persistSettings(event);
            case SecurityEvent.ANALYST_ACTION -> persistAnalystAction(event);
            case SecurityEvent.LOGIN_REQUEST -> persistObservation(event, "INCOMING");
            case SecurityEvent.AUTH_FAILURE -> persistObservation(event, "FAILURE");
            case SecurityEvent.AUTH_SUCCESS -> persistObservation(event, "SUCCESS");
            default -> {
            }
        }
    }

    private void persistEvidence(SecurityEvent event) {
        Map<String, Object> payload = event.payload();
        String decisionId = string(payload, "decisionId");
        if (evidenceRepository.findByDecisionId(decisionId).isPresent()) {
            return;
        }
        evidenceRepository.save(new Entities.EvidenceEntity(
                decisionId,
                event.sourceIp(),
                string(payload, "category"),
                toJson(payload.get("affectedUsernames")),
                toJson(payload.get("matchedRules")),
                toJson(payload.get("observed")),
                toJson(payload.get("windows")),
                toJson(payload.get("eventRefs")),
                string(payload, "policyVersion"),
                toJson(payload.get("policySnapshot")),
                string(payload, "protectionMode"),
                string(payload, "action"),
                string(payload, "reason"),
                instant(payload, "blockExpiry"),
                event.occurredAt()));
    }

    private void persistAlert(SecurityEvent event) {
        Map<String, Object> payload = event.payload();
        String severity = string(payload, "severity");
        Instant lastSeen = instant(payload, "lastSeen");
        long occurrenceCount = longValue(payload, "occurrenceCount");
        String status = string(payload, "status");
        String decisionIds = toJson(payload.get("decisionIds"));
        String alertId = string(payload, "alertId");
        alertRepository.findByAlertId(alertId).ifPresentOrElse(existing -> {
            existing.applyOccurrence(severity, lastSeen, occurrenceCount, status, decisionIds);
            alertRepository.save(existing);
        }, () -> alertRepository.save(new Entities.AlertEntity(
                alertId,
                event.sourceIp(),
                string(payload, "ruleId"),
                string(payload, "category"),
                severity,
                instant(payload, "firstSeen"),
                lastSeen,
                occurrenceCount,
                status,
                decisionIds)));
    }

    private void persistBlock(SecurityEvent event) {
        Map<String, Object> payload = event.payload();
        String blockId = string(payload, "blockId");
        sourceBlockRepository.findByBlockId(blockId).ifPresentOrElse(existing -> {
            existing.applyTransition(string(payload, "reason"), instant(payload, "expiry"),
                    instant(payload, "releasedAt"), blankToNull(string(payload, "releasedBy")),
                    string(payload, "state"));
            sourceBlockRepository.save(existing);
        }, () -> sourceBlockRepository.save(new Entities.SourceBlockEntity(
                blockId,
                event.sourceIp(),
                string(payload, "ruleId"),
                string(payload, "reason"),
                instant(payload, "createdAt"),
                instant(payload, "expiry"),
                instant(payload, "releasedAt"),
                blankToNull(string(payload, "releasedBy")),
                string(payload, "state"))));
    }

    private void persistSettings(SecurityEvent event) {
        Map<String, Object> payload = event.payload();
        settingsVersionRepository.save(new Entities.SettingsVersionEntity(
                string(payload, "policyVersion"),
                string(payload, "mode"),
                toJson(payload.get("snapshot")),
                event.occurredAt()));
    }

    private void persistAnalystAction(SecurityEvent event) {
        Map<String, Object> payload = event.payload();
        analystActionRepository.save(new Entities.AnalystActionEntity(
                string(payload, "action"),
                string(payload, "target"),
                string(payload, "actor"),
                string(payload, "note"),
                event.occurredAt()));
    }

    private void persistObservation(SecurityEvent event, String observationType) {
        if (event.aggregated()) {
            return;
        }
        observationRepository.save(new Entities.DetectionObservationEntity(
                event.sourceIp(),
                string(event.payload(), "username"),
                observationType,
                event.occurredAt(),
                event.requestId()));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise payload: {}", ex.getMessage());
            return null;
        }
    }

    private static String string(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant instant(Map<String, Object> payload, String key) {
        String value = string(payload, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static long longValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
