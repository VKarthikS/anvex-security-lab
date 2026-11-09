package com.anvex.defender.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anvex.defender.alert.AlertService;
import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.evidence.EvidenceService;
import com.anvex.defender.metrics.DefenderMetrics;
import com.anvex.defender.protection.BlockRegistry;
import com.anvex.defender.protection.ProtectionService;
import com.anvex.defender.protection.SourceBlock;
import com.anvex.defender.events.EventPipeline;
import com.anvex.defender.persistence.SecurityEventRepository;
import com.anvex.defender.persistence.EvidenceRepository;
import com.anvex.defender.persistence.AlertRepository;
import com.anvex.defender.persistence.DetectionObservationRepository;

/**
 * Analyst controls. Every endpoint in this controller requires a loopback caller.
 */
@RestController
@RequestMapping("/api/v1")
public class AnalystController {

    private final PolicyService policyService;
    private final ProtectionService protectionService;
    private final DetectionEngine detectionEngine;
    private final EvidenceService evidenceService;
    private final AlertService alertService;
    private final BlockRegistry blockRegistry;
    private final DefenderMetrics metrics;
    private final Clock clock;
    private final EventPipeline eventPipeline;
    private final SecurityEventRepository securityEventRepository;
    private final EvidenceRepository evidenceRepository;
    private final AlertRepository alertRepository;
    private final DetectionObservationRepository observationRepository;

    public AnalystController(PolicyService policyService,
                             ProtectionService protectionService,
                             DetectionEngine detectionEngine,
                             EvidenceService evidenceService,
                             AlertService alertService,
                             BlockRegistry blockRegistry,
                             DefenderMetrics metrics,
                             Clock clock,
                             EventPipeline eventPipeline,
                             SecurityEventRepository securityEventRepository,
                             EvidenceRepository evidenceRepository,
                             AlertRepository alertRepository,
                             DetectionObservationRepository observationRepository) {
        this.policyService = policyService;
        this.protectionService = protectionService;
        this.detectionEngine = detectionEngine;
        this.evidenceService = evidenceService;
        this.alertService = alertService;
        this.blockRegistry = blockRegistry;
        this.metrics = metrics;
        this.clock = clock;
        this.eventPipeline = eventPipeline;
        this.securityEventRepository = securityEventRepository;
        this.evidenceRepository = evidenceRepository;
        this.alertRepository = alertRepository;
        this.observationRepository = observationRepository;
    }

    @GetMapping("/policy")
    public Map<String, Object> policy() {
        return describe(policyService.current());
    }

    @GetMapping("/settings")
    public Map<String, Object> settings(HttpServletRequest request) {
        AnalystGuard.requireLoopback(request, clock);
        return describe(policyService.current());
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(HttpServletRequest request,
                                              @Valid @RequestBody SettingsUpdateRequest update) {
        AnalystGuard.requireLoopback(request, clock);
        PolicySnapshot current = policyService.current();
        int repeatedGuessingThreshold = orDefault(update.repeatedGuessingFailureThreshold(),
                current.repeatedGuessingFailureThreshold());
        Duration repeatedGuessingWindow = Duration.ofSeconds(orDefault(update.repeatedGuessingWindowSeconds(),
                (int) (current.repeatedGuessingWindowMillis() / 1000)));
        int sprayingThreshold = orDefault(update.sprayingFailureThreshold(), current.sprayingFailureThreshold());
        int sprayingDistinct = orDefault(update.sprayingDistinctAccountThreshold(),
                current.sprayingDistinctAccountThreshold());
        Duration sprayingWindow = Duration.ofSeconds(orDefault(update.sprayingWindowSeconds(),
                (int) (current.sprayingWindowMillis() / 1000)));
        int rapidThreshold = orDefault(update.rapidRequestThreshold(), current.rapidRequestThreshold());
        Duration rapidWindow = Duration.ofSeconds(orDefault(update.rapidRequestWindowSeconds(),
                (int) (current.rapidRequestWindowMillis() / 1000)));
        int requestsPerSecond = orDefault(update.tokenBucketRequestsPerSecond(),
                current.tokenBucketRequestsPerSecond());
        int burstCapacity = orDefault(update.tokenBucketBurstCapacity(), current.tokenBucketBurstCapacity());
        Duration blockDuration = Duration.ofSeconds(orDefault(update.blockDurationSeconds(),
                (int) (current.blockDurationMillis() / 1000)));

        if (sprayingDistinct > sprayingThreshold) {
            throw new IllegalArgumentException(
                    "sprayingDistinctAccountThreshold must not exceed sprayingFailureThreshold.");
        }
        if (burstCapacity < requestsPerSecond) {
            throw new IllegalArgumentException("tokenBucketBurstCapacity must be at least tokenBucketRequestsPerSecond.");
        }

        String version = update.policyVersion() == null || update.policyVersion().isBlank()
                ? current.policyVersion()
                : update.policyVersion().trim();

        PolicySnapshot updated = PolicyService.from(current,
                repeatedGuessingThreshold,
                repeatedGuessingWindow,
                sprayingThreshold,
                sprayingDistinct,
                sprayingWindow,
                rapidThreshold,
                rapidWindow,
                requestsPerSecond,
                burstCapacity,
                blockDuration,
                version,
                clock.instant());
        policyService.withThresholds(updated, clock.instant());
        return describe(updated);
    }

    @PostMapping("/protection")
    public Map<String, Object> setProtection(HttpServletRequest request, @RequestBody Map<String, String> body) {
        AnalystGuard.requireLoopback(request, clock);
        String requested = body.get("mode");
        if (requested == null) {
            throw new IllegalArgumentException("mode is required (ON or OFF)");
        }
        ProtectionMode mode;
        try {
            mode = ProtectionMode.valueOf(requested.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("mode must be ON or OFF");
        }
        ProtectionMode applied = protectionService.setMode(mode, "analyst@loopback");
        return Map.of("mode", applied.name(), "policyVersion", policyService.current().policyVersion(),
                "activeBlocks", protectionService.activeBlocks().size());
    }

    @PostMapping("/analyst/unblock")
    public ResponseEntity<?> unblock(HttpServletRequest request,
                                    @RequestBody Map<String, String> body) {
        AnalystGuard.requireLoopback(request, clock);
        String sourceIp = body.get("sourceIp");
        if (sourceIp == null || sourceIp.isBlank()) {
            throw new IllegalArgumentException("sourceIp is required");
        }
        return protectionService.unblock(sourceIp.trim(), "analyst@loopback")
                .map(block -> ResponseEntity.ok(Map.of(
                        "released", true,
                        "sourceIp", block.sourceIp(),
                        "blockId", block.blockId(),
                        "releasedAt", String.valueOf(block.releasedAt()))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("released", false)));
    }

    @PostMapping("/analyst/reset")
    public Map<String, Object> reset(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        AnalystGuard.requireLoopback(request, clock);
        String scope = body == null || body.get("scope") == null ? "counters" : body.get("scope").trim();
        detectionEngine.resetCounters();
        protectionService.resetState();
        if (scope.equalsIgnoreCase("demo")) {
            securityEventRepository.deleteAllInBatch();
            evidenceRepository.deleteAllInBatch();
            alertRepository.deleteAllInBatch();
            observationRepository.deleteAllInBatch();
            evidenceService.clear();
            alertService.clear();
            blockRegistry.clear();
        }
        return Map.of("scope", scope, "resetAt", clock.instant().toString(),
                "counters", metrics.snapshot());
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics(HttpServletRequest request) {
        AnalystGuard.requireLoopback(request, clock);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("javaVersion", System.getProperty("java.version"));
        body.put("javaVendor", System.getProperty("java.vendor"));
        body.put("policy", describe(policyService.current()));
        body.put("counters", metrics.snapshot());
        body.put("activeBlocks", protectionService.activeBlocks());
        body.put("blockHistory", blockRegistry.history());
        body.put("protectionEvents", protectionService.protectionEvents());
        body.put("sources", detectionEngine.summaries().values());
        body.put("alerts", alertService.alerts(25));
        body.put("evidenceCount", evidenceService.recent(1000).size());
        body.put("eventPipeline", eventPipeline.diagnostics());
        body.put("historyGaps", Map.of("note", "Rolling history is reconstructed from persisted observations; aggregated or dropped telemetry leaves documented gaps.", "retainedWindowSeconds", 900));
        return body;
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static Map<String, Object> describe(PolicySnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policyVersion", snapshot.policyVersion());
        body.put("protectionMode", snapshot.protectionMode().name());
        body.put("repeatedGuessingFailureThreshold", snapshot.repeatedGuessingFailureThreshold());
        body.put("repeatedGuessingWindowSeconds", snapshot.repeatedGuessingWindowMillis() / 1000);
        body.put("sprayingFailureThreshold", snapshot.sprayingFailureThreshold());
        body.put("sprayingDistinctAccountThreshold", snapshot.sprayingDistinctAccountThreshold());
        body.put("sprayingWindowSeconds", snapshot.sprayingWindowMillis() / 1000);
        body.put("rapidRequestThreshold", snapshot.rapidRequestThreshold());
        body.put("rapidRequestWindowSeconds", snapshot.rapidRequestWindowMillis() / 1000);
        body.put("tokenBucketRequestsPerSecond", snapshot.tokenBucketRequestsPerSecond());
        body.put("tokenBucketBurstCapacity", snapshot.tokenBucketBurstCapacity());
        body.put("blockDurationSeconds", snapshot.blockDurationMillis() / 1000);
        body.put("createdAt", snapshot.createdAt());
        return body;
    }
}
