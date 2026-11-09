package com.anvex.defender.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.protection.BlockRegistry;
import com.anvex.defender.protection.SourceBlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Restores the durable state after a restart: the latest settings version (including the
 * protection mode), unexpired source blocks and the recent detection history.
 *
 * <p>The detection history is reconstructed from the persisted observations. Where observations
 * are missing (for example because telemetry was aggregated or dropped under queue pressure), the
 * history gap is reported instead of being invented.</p>
 */
@Component
public class StartupRestore implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRestore.class);

    private final SettingsPersistenceService settingsPersistenceService;
    private final BlockPersistenceService blockPersistenceService;
    private final DetectionObservationRepository observationRepository;
    private final PolicyService policyService;
    private final BlockRegistry blockRegistry;
    private final DetectionEngine detectionEngine;

    public StartupRestore(SettingsPersistenceService settingsPersistenceService,
                          BlockPersistenceService blockPersistenceService,
                          DetectionObservationRepository observationRepository,
                          PolicyService policyService,
                          BlockRegistry blockRegistry,
                          DetectionEngine detectionEngine) {
        this.settingsPersistenceService = settingsPersistenceService;
        this.blockPersistenceService = blockPersistenceService;
        this.observationRepository = observationRepository;
        this.policyService = policyService;
        this.blockRegistry = blockRegistry;
        this.detectionEngine = detectionEngine;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();

        settingsPersistenceService.latest().ifPresent(stored -> {
            policyService.withProtectionMode(stored.mode(), now);
            log.info("Restored settings version {} with protection mode {}", stored.policyVersion(), stored.mode());
        });

        List<SourceBlock> active = blockPersistenceService.activeAt(now);
        active.forEach(blockRegistry::restore);
        if (!active.isEmpty()) {
            log.info("Restored {} unexpired source block(s)", active.size());
        }

        List<Entities.DetectionObservationEntity> observations = observationRepository.findByOccurredAtAfter(
                now.minusSeconds(900));
        int incoming = 0;
        int failures = 0;
        for (Entities.DetectionObservationEntity observation : observations) {
            if ("INCOMING".equals(observation.getObservationType())) {
                detectionEngine.seedIncoming(observation.getSourceIp(), observation.getOccurredAt());
                incoming++;
            } else if ("FAILURE".equals(observation.getObservationType())) {
                detectionEngine.seedFailure(observation.getSourceIp(), observation.getUsername(),
                        observation.getOccurredAt());
                failures++;
            }
        }
        if (!observations.isEmpty()) {
            log.info("Reconstructed recent detection history: {} incoming request(s), {} failure(s)", incoming, failures);
        }
    }
}
