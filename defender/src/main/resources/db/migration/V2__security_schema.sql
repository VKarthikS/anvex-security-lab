CREATE TABLE security_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id    VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    source_ip   VARCHAR(64),
    request_id  VARCHAR(64),
    critical    BOOLEAN      NOT NULL,
    aggregated  BOOLEAN      NOT NULL,
    payload_json CLOB
);

CREATE INDEX idx_events_occurred_at ON security_events (occurred_at);
CREATE INDEX idx_events_source_ip ON security_events (source_ip);
CREATE INDEX idx_events_type ON security_events (event_type);

CREATE TABLE evidence (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    decision_id           VARCHAR(64)  NOT NULL,
    source_ip             VARCHAR(64)  NOT NULL,
    category              VARCHAR(64)  NOT NULL,
    affected_usernames_json CLOB,
    matched_rules_json    CLOB,
    observed_json         CLOB,
    windows_json          CLOB,
    event_refs_json       CLOB,
    policy_version        VARCHAR(80),
    policy_snapshot_json  CLOB,
    protection_mode       VARCHAR(8)   NOT NULL,
    action                VARCHAR(32)  NOT NULL,
    reason                VARCHAR(1000),
    block_expiry          TIMESTAMP(6),
    created_at            TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX idx_evidence_decision_id ON evidence (decision_id);
CREATE INDEX idx_evidence_source_ip ON evidence (source_ip);
CREATE INDEX idx_evidence_created_at ON evidence (created_at);

CREATE TABLE alerts (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_id         VARCHAR(64)  NOT NULL,
    source_ip        VARCHAR(64)  NOT NULL,
    rule_id          VARCHAR(64)  NOT NULL,
    category         VARCHAR(64)  NOT NULL,
    severity         VARCHAR(16)  NOT NULL,
    first_seen       TIMESTAMP(6) NOT NULL,
    last_seen        TIMESTAMP(6) NOT NULL,
    occurrence_count BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    decision_ids_json CLOB
);

CREATE UNIQUE INDEX idx_alerts_alert_id ON alerts (alert_id);
CREATE INDEX idx_alerts_source_ip ON alerts (source_ip);
CREATE INDEX idx_alerts_last_seen ON alerts (last_seen);

CREATE TABLE settings_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_version  VARCHAR(80) NOT NULL,
    protection_mode VARCHAR(8)  NOT NULL,
    snapshot_json   CLOB,
    created_at      TIMESTAMP(6) NOT NULL
);

CREATE TABLE analyst_actions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    action     VARCHAR(64)  NOT NULL,
    target     VARCHAR(128),
    actor      VARCHAR(64)  NOT NULL,
    note       VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE source_blocks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_id    VARCHAR(64)  NOT NULL,
    source_ip   VARCHAR(64)  NOT NULL,
    rule_id     VARCHAR(64)  NOT NULL,
    reason      VARCHAR(1000),
    created_at  TIMESTAMP(6) NOT NULL,
    expiry      TIMESTAMP(6) NOT NULL,
    released_at TIMESTAMP(6),
    released_by VARCHAR(64),
    state       VARCHAR(16)  NOT NULL
);

CREATE UNIQUE INDEX idx_blocks_block_id ON source_blocks (block_id);
CREATE INDEX idx_blocks_source_ip ON source_blocks (source_ip);
CREATE INDEX idx_blocks_expiry ON source_blocks (expiry);

CREATE TABLE detection_observations (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ip        VARCHAR(64) NOT NULL,
    username         VARCHAR(64),
    observation_type VARCHAR(16) NOT NULL,
    occurred_at      TIMESTAMP(6) NOT NULL,
    request_id       VARCHAR(64)
);

CREATE INDEX idx_observations_occurred_at ON detection_observations (occurred_at);
CREATE INDEX idx_observations_source_ip ON detection_observations (source_ip);
