package com.anvex.defender.persistence;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;

/**
 * Stores the settings versions. The latest version is restored on restart so the protection mode
 * and thresholds survive a restart.
 */
@Service
public class SettingsPersistenceService {

    private final SettingsVersionRepository repository;
    private final ObjectMapper objectMapper;

    public SettingsPersistenceService(SettingsVersionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(PolicySnapshot snapshot) {
        repository.save(new Entities.SettingsVersionEntity(
                snapshot.policyVersion(),
                snapshot.protectionMode().name(),
                toJson(snapshot),
                snapshot.createdAt()));
    }

    @Transactional(readOnly = true)
    public Optional<StoredSettings> latest() {
        return repository.findTopByOrderByCreatedAtDesc()
                .map(entity -> new StoredSettings(entity.getPolicyVersion(), entity.getProtectionMode(),
                        entity.getSnapshotJson(), entity.getCreatedAt()));
    }

    private String toJson(PolicySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public record StoredSettings(String policyVersion, String protectionMode, String snapshotJson, Instant createdAt) {
        public ProtectionMode mode() {
            try {
                return ProtectionMode.valueOf(protectionMode);
            } catch (RuntimeException ex) {
                return ProtectionMode.OFF;
            }
        }
    }
}
