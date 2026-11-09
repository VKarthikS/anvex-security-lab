package com.anvex.defender.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.anvex.defender.detection.DetectionCategory;
import com.anvex.defender.detection.RuleMatch;
import com.anvex.defender.events.EventQueueService;
import com.anvex.defender.events.SecurityEvent;

/**
 * Alert registry. Repeated matches of the same rule for the same source update the existing alert
 * instead of creating duplicates, while the occurrence count and the last-seen time are kept.
 */
@Service
public class AlertService {

    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";

    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private final EventQueueService eventQueueService;

    public AlertService(EventQueueService eventQueueService) {
        this.eventQueueService = eventQueueService;
    }

    public Alert upsert(String sourceIp, RuleMatch match, String decisionId, Instant now) {
        String key = sourceIp + '|' + match.ruleId();
        Alert updated = alerts.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new Alert(
                        UUID.randomUUID().toString(),
                        sourceIp,
                        match.ruleId(),
                        match.category(),
                        severityFor(match.category()),
                        now,
                        now,
                        1,
                        "OPEN",
                        List.of(decisionId));
            }
            List<String> decisions = new ArrayList<>(existing.decisionIds());
            if (!decisions.contains(decisionId)) {
                decisions.add(decisionId);
                while (decisions.size() > 25) {
                    decisions.remove(0);
                }
            }
            return new Alert(
                    existing.alertId(),
                    existing.sourceIp(),
                    existing.ruleId(),
                    existing.category(),
                    existing.severity(),
                    existing.firstSeen(),
                    now,
                    existing.occurrenceCount() + 1,
                    existing.status(),
                    List.copyOf(decisions));
        });
        eventQueueService.publishCritical(SecurityEvent.of(SecurityEvent.ALERT_UPSERT, now, sourceIp,
                updated.alertId(), true, Map.of(
                        "alertId", updated.alertId(),
                        "ruleId", updated.ruleId(),
                        "category", updated.category().name(),
                        "severity", updated.severity(),
                        "firstSeen", updated.firstSeen().toString(),
                        "lastSeen", updated.lastSeen().toString(),
                        "occurrenceCount", updated.occurrenceCount(),
                        "status", updated.status(),
                        "decisionIds", updated.decisionIds())));
        return updated;
    }

    public List<Alert> alerts(int limit) {
        List<Alert> all = new ArrayList<>(alerts.values());
        all.sort((left, right) -> right.lastSeen().compareTo(left.lastSeen()));
        return all.stream().limit(Math.max(1, limit)).toList();
    }

    public List<Alert> alertsForSource(String sourceIp) {
        return alerts.values().stream()
                .filter(alert -> alert.sourceIp().equals(sourceIp))
                .sorted((left, right) -> right.lastSeen().compareTo(left.lastSeen()))
                .toList();
    }

    /** Clears the alert registry (demo history reset). */
    public void clear() {
        alerts.clear();
    }

    public static String severityFor(DetectionCategory category) {        return switch (category) {
            case SUSPECTED_PASSWORD_SPRAYING -> SEVERITY_HIGH;
            case SUSPECTED_REPEATED_GUESSING -> SEVERITY_MEDIUM;
            case SUSPECTED_RAPID_REQUESTS -> SEVERITY_LOW;
            case NONE -> SEVERITY_LOW;
        };
    }

    public record Alert(
            String alertId,
            String sourceIp,
            String ruleId,
            DetectionCategory category,
            String severity,
            Instant firstSeen,
            Instant lastSeen,
            long occurrenceCount,
            String status,
            List<String> decisionIds
    ) {
    }
}
