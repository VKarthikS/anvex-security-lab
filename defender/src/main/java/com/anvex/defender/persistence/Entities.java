package com.anvex.defender.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The durable ANVEX schema. Column names match the Flyway migrations exactly.
 */
public final class Entities {

    private Entities() {
    }

    @Entity
    @Table(name = "security_events", indexes = {
            @Index(name = "idx_events_occurred_at", columnList = "occurred_at"),
            @Index(name = "idx_events_source_ip", columnList = "source_ip"),
            @Index(name = "idx_events_type", columnList = "event_type")
    })
    public static class SecurityEventEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "event_id", nullable = false, length = 64)
        private String eventId;

        @Column(name = "event_type", nullable = false, length = 64)
        private String eventType;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Column(name = "source_ip", length = 64)
        private String sourceIp;

        @Column(name = "request_id", length = 64)
        private String requestId;

        @Column(name = "critical", nullable = false)
        private boolean critical;

        @Column(name = "aggregated", nullable = false)
        private boolean aggregated;

        @Lob
        @Column(name = "payload_json")
        private String payloadJson;

        protected SecurityEventEntity() {
        }

        public SecurityEventEntity(String eventId, String eventType, Instant occurredAt, String sourceIp,
                                   String requestId, boolean critical, boolean aggregated, String payloadJson) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.occurredAt = occurredAt;
            this.sourceIp = sourceIp;
            this.requestId = requestId;
            this.critical = critical;
            this.aggregated = aggregated;
            this.payloadJson = payloadJson;
        }

        public Long getId() {
            return id;
        }

        public String getEventId() {
            return eventId;
        }

        public String getEventType() {
            return eventType;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getRequestId() {
            return requestId;
        }

        public boolean isCritical() {
            return critical;
        }

        public boolean isAggregated() {
            return aggregated;
        }

        public String getPayloadJson() {
            return payloadJson;
        }
    }

    @Entity
    @Table(name = "evidence", indexes = {
            @Index(name = "idx_evidence_source_ip", columnList = "source_ip"),
            @Index(name = "idx_evidence_created_at", columnList = "created_at")
    })
    public static class EvidenceEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "decision_id", nullable = false, length = 64)
        private String decisionId;

        @Column(name = "source_ip", nullable = false, length = 64)
        private String sourceIp;

        @Column(name = "category", nullable = false, length = 64)
        private String category;

        @Lob
        @Column(name = "affected_usernames_json")
        private String affectedUsernamesJson;

        @Lob
        @Column(name = "matched_rules_json")
        private String matchedRulesJson;

        @Lob
        @Column(name = "observed_json")
        private String observedJson;

        @Lob
        @Column(name = "windows_json")
        private String windowsJson;

        @Lob
        @Column(name = "event_refs_json")
        private String eventRefsJson;

        @Column(name = "policy_version", length = 80)
        private String policyVersion;

        @Lob
        @Column(name = "policy_snapshot_json")
        private String policySnapshotJson;

        @Column(name = "protection_mode", nullable = false, length = 8)
        private String protectionMode;

        @Column(name = "action", nullable = false, length = 32)
        private String action;

        @Column(name = "reason", length = 1000)
        private String reason;

        @Column(name = "block_expiry")
        private Instant blockExpiry;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected EvidenceEntity() {
        }

        public EvidenceEntity(String decisionId, String sourceIp, String category, String affectedUsernamesJson,
                              String matchedRulesJson, String observedJson, String windowsJson, String eventRefsJson,
                              String policyVersion, String policySnapshotJson, String protectionMode, String action,
                              String reason, Instant blockExpiry, Instant createdAt) {
            this.decisionId = decisionId;
            this.sourceIp = sourceIp;
            this.category = category;
            this.affectedUsernamesJson = affectedUsernamesJson;
            this.matchedRulesJson = matchedRulesJson;
            this.observedJson = observedJson;
            this.windowsJson = windowsJson;
            this.eventRefsJson = eventRefsJson;
            this.policyVersion = policyVersion;
            this.policySnapshotJson = policySnapshotJson;
            this.protectionMode = protectionMode;
            this.action = action;
            this.reason = reason;
            this.blockExpiry = blockExpiry;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getDecisionId() {
            return decisionId;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getCategory() {
            return category;
        }

        public String getAffectedUsernamesJson() {
            return affectedUsernamesJson;
        }

        public String getMatchedRulesJson() {
            return matchedRulesJson;
        }

        public String getObservedJson() {
            return observedJson;
        }

        public String getWindowsJson() {
            return windowsJson;
        }

        public String getEventRefsJson() {
            return eventRefsJson;
        }

        public String getPolicyVersion() {
            return policyVersion;
        }

        public String getPolicySnapshotJson() {
            return policySnapshotJson;
        }

        public String getProtectionMode() {
            return protectionMode;
        }

        public String getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public Instant getBlockExpiry() {
            return blockExpiry;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    @Entity
    @Table(name = "alerts", indexes = {
            @Index(name = "idx_alerts_source_ip", columnList = "source_ip"),
            @Index(name = "idx_alerts_last_seen", columnList = "last_seen")
    })
    public static class AlertEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "alert_id", nullable = false, length = 64)
        private String alertId;

        @Column(name = "source_ip", nullable = false, length = 64)
        private String sourceIp;

        @Column(name = "rule_id", nullable = false, length = 64)
        private String ruleId;

        @Column(name = "category", nullable = false, length = 64)
        private String category;

        @Column(name = "severity", nullable = false, length = 16)
        private String severity;

        @Column(name = "first_seen", nullable = false)
        private Instant firstSeen;

        @Column(name = "last_seen", nullable = false)
        private Instant lastSeen;

        @Column(name = "occurrence_count", nullable = false)
        private long occurrenceCount;

        @Column(name = "status", nullable = false, length = 16)
        private String status;

        @Lob
        @Column(name = "decision_ids_json")
        private String decisionIdsJson;

        protected AlertEntity() {
        }

        public AlertEntity(String alertId, String sourceIp, String ruleId, String category, String severity,
                           Instant firstSeen, Instant lastSeen, long occurrenceCount, String status,
                           String decisionIdsJson) {
            this.alertId = alertId;
            this.sourceIp = sourceIp;
            this.ruleId = ruleId;
            this.category = category;
            this.severity = severity;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.occurrenceCount = occurrenceCount;
            this.status = status;
            this.decisionIdsJson = decisionIdsJson;
        }

        public Long getId() {
            return id;
        }

        public String getAlertId() {
            return alertId;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getRuleId() {
            return ruleId;
        }

        public String getCategory() {
            return category;
        }

        public String getSeverity() {
            return severity;
        }

        public Instant getFirstSeen() {
            return firstSeen;
        }

        public Instant getLastSeen() {
            return lastSeen;
        }

        public long getOccurrenceCount() {
            return occurrenceCount;
        }

        public String getStatus() {
            return status;
        }

        public String getDecisionIdsJson() {
            return decisionIdsJson;
        }

        /** Applies a later occurrence of the same alert to an existing row. */
        public void applyOccurrence(String severity, Instant lastSeen, long occurrenceCount, String status,
                                    String decisionIdsJson) {
            this.severity = severity;
            this.lastSeen = lastSeen;
            this.occurrenceCount = occurrenceCount;
            this.status = status;
            this.decisionIdsJson = decisionIdsJson;
        }
    }

    @Entity
    @Table(name = "settings_versions")
    public static class SettingsVersionEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "policy_version", nullable = false, length = 80)
        private String policyVersion;

        @Column(name = "protection_mode", nullable = false, length = 8)
        private String protectionMode;

        @Lob
        @Column(name = "snapshot_json")
        private String snapshotJson;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected SettingsVersionEntity() {
        }

        public SettingsVersionEntity(String policyVersion, String protectionMode, String snapshotJson,
                                     Instant createdAt) {
            this.policyVersion = policyVersion;
            this.protectionMode = protectionMode;
            this.snapshotJson = snapshotJson;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getPolicyVersion() {
            return policyVersion;
        }

        public String getProtectionMode() {
            return protectionMode;
        }

        public String getSnapshotJson() {
            return snapshotJson;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    @Entity
    @Table(name = "analyst_actions")
    public static class AnalystActionEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "action", nullable = false, length = 64)
        private String action;

        @Column(name = "target", length = 128)
        private String target;

        @Column(name = "actor", nullable = false, length = 64)
        private String actor;

        @Column(name = "note", length = 500)
        private String note;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected AnalystActionEntity() {
        }

        public AnalystActionEntity(String action, String target, String actor, String note, Instant createdAt) {
            this.action = action;
            this.target = target;
            this.actor = actor;
            this.note = note;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getAction() {
            return action;
        }

        public String getTarget() {
            return target;
        }

        public String getActor() {
            return actor;
        }

        public String getNote() {
            return note;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    @Entity
    @Table(name = "source_blocks", indexes = {
            @Index(name = "idx_blocks_source_ip", columnList = "source_ip"),
            @Index(name = "idx_blocks_expiry", columnList = "expiry")
    })
    public static class SourceBlockEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "block_id", nullable = false, length = 64)
        private String blockId;

        @Column(name = "source_ip", nullable = false, length = 64)
        private String sourceIp;

        @Column(name = "rule_id", nullable = false, length = 64)
        private String ruleId;

        @Column(name = "reason", length = 1000)
        private String reason;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        @Column(name = "expiry", nullable = false)
        private Instant expiry;

        @Column(name = "released_at")
        private Instant releasedAt;

        @Column(name = "released_by", length = 64)
        private String releasedBy;

        @Column(name = "state", nullable = false, length = 16)
        private String state;

        protected SourceBlockEntity() {
        }

        public SourceBlockEntity(String blockId, String sourceIp, String ruleId, String reason, Instant createdAt,
                                 Instant expiry, Instant releasedAt, String releasedBy, String state) {
            this.blockId = blockId;
            this.sourceIp = sourceIp;
            this.ruleId = ruleId;
            this.reason = reason;
            this.createdAt = createdAt;
            this.expiry = expiry;
            this.releasedAt = releasedAt;
            this.releasedBy = releasedBy;
            this.state = state;
        }

        public Long getId() {
            return id;
        }

        public String getBlockId() {
            return blockId;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getRuleId() {
            return ruleId;
        }

        public String getReason() {
            return reason;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getExpiry() {
            return expiry;
        }

        public Instant getReleasedAt() {
            return releasedAt;
        }

        public String getReleasedBy() {
            return releasedBy;
        }

        public String getState() {
            return state;
        }

        /** Applies a later transition (for example a manual release) to an existing row. */
        public void applyTransition(String reason, Instant expiry, Instant releasedAt, String releasedBy,
                                    String newState) {
            this.reason = reason;
            this.expiry = expiry;
            this.releasedAt = releasedAt;
            this.releasedBy = releasedBy;
            this.state = newState;
        }
    }

    @Entity
    @Table(name = "detection_observations", indexes = {
            @Index(name = "idx_observations_occurred_at", columnList = "occurred_at"),
            @Index(name = "idx_observations_source_ip", columnList = "source_ip")
    })
    public static class DetectionObservationEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "source_ip", nullable = false, length = 64)
        private String sourceIp;

        @Column(name = "username", length = 64)
        private String username;

        @Column(name = "observation_type", nullable = false, length = 16)
        private String observationType;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Column(name = "request_id", length = 64)
        private String requestId;

        protected DetectionObservationEntity() {
        }

        public DetectionObservationEntity(String sourceIp, String username, String observationType,
                                          Instant occurredAt, String requestId) {
            this.sourceIp = sourceIp;
            this.username = username;
            this.observationType = observationType;
            this.occurredAt = occurredAt;
            this.requestId = requestId;
        }

        public Long getId() {
            return id;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public String getUsername() {
            return username;
        }

        public String getObservationType() {
            return observationType;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public String getRequestId() {
            return requestId;
        }
    }
}
