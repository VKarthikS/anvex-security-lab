package com.anvex.defender.protection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.anvex.defender.alert.AlertService;
import com.anvex.defender.detection.DetectionCategory;
import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.DetectionOutcome;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.detection.RuleIds;
import com.anvex.defender.detection.RuleMatch;
import com.anvex.defender.evidence.EvidenceService;
import com.anvex.defender.events.EventQueueService;
import com.anvex.defender.persistence.BlockPersistenceService;
import com.anvex.defender.persistence.SettingsPersistenceService;
import com.anvex.defender.events.SecurityEvent;
import com.anvex.defender.evidence.EvidenceSnapshot;
import com.anvex.defender.metrics.DefenderMetrics;

/**
 * The protection lifecycle: ON/OFF mode, per-source token bucket, temporary source blocks, the
 * rearm policy and manual recovery.
 */
@Service
public class ProtectionService {

    private static final Logger log = LoggerFactory.getLogger(ProtectionService.class);

    private final PolicyService policyService;
    private final DetectionEngine detectionEngine;
    private final BlockRegistry blockRegistry;
    private final EvidenceService evidenceService;
    private final AlertService alertService;
    private final DefenderMetrics metrics;
    private final Clock clock;
    private final EventQueueService eventQueueService;
    private final BlockPersistenceService blockPersistenceService;
    private final SettingsPersistenceService settingsPersistenceService;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final List<String> protectionEvents = new ArrayList<>();

    public ProtectionService(PolicyService policyService,
                             DetectionEngine detectionEngine,
                             BlockRegistry blockRegistry,
                             EvidenceService evidenceService,
                             AlertService alertService,
                             DefenderMetrics metrics,
                             Clock clock,
                             EventQueueService eventQueueService,
                             BlockPersistenceService blockPersistenceService,
                             SettingsPersistenceService settingsPersistenceService) {
        this.policyService = policyService;
        this.detectionEngine = detectionEngine;
        this.blockRegistry = blockRegistry;
        this.evidenceService = evidenceService;
        this.alertService = alertService;
        this.metrics = metrics;
        this.clock = clock;
        this.eventQueueService = eventQueueService;
        this.blockPersistenceService = blockPersistenceService;
        this.settingsPersistenceService = settingsPersistenceService;
    }

    public boolean isProtectionOn() {
        return policyService.protectionMode().isOn();
    }

    public ProtectionMode mode() {
        return policyService.protectionMode();
    }

    /**
     * The protection gate. It is only consulted when protection is ON: with protection OFF the
     * dummy application keeps verifying credentials without additional restrictions.
     */
    public ProtectionDecision gate(String sourceIp, DetectionOutcome incomingOutcome, Instant now, String runId) {
        if (!isProtectionOn()) {
            return ProtectionDecision.allowed("Protection is OFF: observation only.");
        }
        Optional<SourceBlock> active = blockRegistry.activeBlock(sourceIp, now);
        if (active.isPresent()) {
            SourceBlock block = active.get();
            metrics.recordSourceBlocked(runId);
            int retryAfter = (int) Math.max(1, Duration.between(now, block.expiry()).toSeconds());
            return ProtectionDecision.blocked(block, retryAfter);
        }

        boolean rapidMatch = incomingOutcome.matches().stream()
                .anyMatch(match -> match.ruleId().equals(RuleIds.RAPID_REQUESTS));
        if (rapidMatch) {
            SourceBlock block = createBlockIfAllowed(sourceIp, RuleIds.RAPID_REQUESTS,
                    "Unusually rapid login requests detected.", now, incomingOutcome);
            if (block != null) {
                metrics.recordSourceBlocked(runId);
                int retryAfter = (int) Math.max(1, Duration.between(now, block.expiry()).toSeconds());
                return ProtectionDecision.blocked(block, retryAfter);
            }
        }

        TokenBucket bucket = buckets.computeIfAbsent(sourceIp,
                ip -> new TokenBucket(policyService.current().tokenBucketRequestsPerSecond(),
                        policyService.current().tokenBucketBurstCapacity(), now));
        if (!bucket.tryConsume(now)) {
            metrics.recordRateLimited(runId);
            long retryMillis = bucket.retryAfterMillis(now);
            int retryAfter = (int) Math.max(1, Math.ceil(retryMillis / 1000.0));
            return ProtectionDecision.rateLimited(retryAfter);
        }
        return ProtectionDecision.allowed("Protection is ON and the request was admitted.");
    }

    /**
     * Applies the failure based rules after a credential check. The threshold-crossing request has
     * already reached authentication; the block applies to subsequent requests.
     */
    public Optional<SourceBlock> applyFailureRules(String sourceIp, DetectionOutcome outcome, String requestId, Instant now) {
        if (!isProtectionOn() || !outcome.hasMatches()) {
            return Optional.empty();
        }
        for (RuleMatch match : outcome.matches()) {
            if (match.ruleId().equals(RuleIds.RAPID_REQUESTS)) {
                continue;
            }
            SourceBlock block = createBlockIfAllowed(sourceIp, match.ruleId(), match.description(), now, outcome);
            if (block != null) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    private SourceBlock createBlockIfAllowed(String sourceIp, String ruleId, String reason, Instant now,
                                             DetectionOutcome outcome) {
        if (!blockRegistry.mayCreateBlock(sourceIp, ruleId, now)) {
            Instant rearmAt = blockRegistry.rearmInstant(sourceIp, ruleId).orElse(null);
            if (rearmAt != null && now.isBefore(rearmAt)) {
                return null;
            }
            int freshFailures = detectionEngine.failureCountSince(sourceIp, rearmAt);
            int freshRequests = detectionEngine.incomingCountSince(sourceIp, rearmAt);
            if (freshFailures == 0 && freshRequests == 0) {
                return null;
            }
        }
        PolicySnapshot policy = policyService.current();
        Duration duration = Duration.ofMillis(policy.blockDurationMillis());
        SourceBlock block = blockRegistry.createBlock(sourceIp, ruleId, reason, now, duration);
        blockPersistenceService.save(block);
        recordEvidence(sourceIp, List.of(ruleId), reason, now, policy, block.expiry(), "enforced", block.blockId());
        publishBlockEvent(block, now);
        for (RuleMatch match : outcome.matches()) {
            if (match.ruleId().equals(ruleId)) {
                alertService.upsert(sourceIp, match, block.blockId(), now);
            }
        }
        return block;
    }

    /**
     * Switches protection on or off. Switching on immediately re-evaluates still-valid rolling
     * activity and enforces the applicable restrictions.
     */
    public ProtectionMode setMode(ProtectionMode requested, String actor) {
        Instant now = clock.instant();
        PolicySnapshot previous = policyService.current();
        policyService.withProtectionMode(requested, now);
        synchronized (protectionEvents) {
            protectionEvents.add(now + " protection " + previous.protectionMode() + " -> " + requested + " by " + actor);
            while (protectionEvents.size() > 100) {
                protectionEvents.remove(0);
            }
        }
        if (requested.isOn()) {
            enforceOnEnable(now);
        }
        settingsPersistenceService.save(policyService.current());
        publishProtectionChanged(requested, policyService.current(), now, actor);
        return requested;
    }

    private void enforceOnEnable(Instant now) {
        PolicySnapshot policy = policyService.current();
        for (String sourceIp : detectionEngine.summaries().keySet()) {
            DetectionOutcome outcome = detectionEngine.reevaluate(sourceIp, now);
            if (!outcome.hasMatches()) {
                continue;
            }
            for (RuleMatch match : outcome.matches()) {
                Instant rearmAt = blockRegistry.rearmInstant(sourceIp, match.ruleId()).orElse(null);
                if (rearmAt != null && now.isBefore(rearmAt)) {
                    continue;
                }
                SourceBlock block = blockRegistry.createBlock(sourceIp, match.ruleId(),
                        "Enforced when protection was switched on: " + match.description(), now,
                        Duration.ofMillis(policy.blockDurationMillis()));
                recordEvidence(sourceIp, List.of(match.ruleId()),
                        "PROTECTION_ENABLED_ENFORCEMENT - " + match.description(), now, policy, block.expiry(),
                        "enforced", block.blockId());
                publishBlockEvent(block, now);
                alertService.upsert(sourceIp, match, block.blockId(), now);
            }
        }
    }

    private void recordEvidence(String sourceIp, List<String> ruleIds, String reason, Instant now,
                                PolicySnapshot policy, Instant blockExpiry, String action, String eventId) {
        Map<String, Object> observed = new LinkedHashMap<>();
        Map<String, Object> windows = new LinkedHashMap<>();
        for (String ruleId : ruleIds) {
            observed.put(ruleId, Map.of("threshold", policy.repeatedGuessingFailureThreshold()));
            windows.put(ruleId, Map.of("start", now, "end", now));
        }
        evidenceService.record(
                sourceIp,
                List.of(),
                DetectionCategory.NONE,
                List.of(),
                observed,
                windows,
                List.of(eventId),
                policy.policyVersion(),
                policy,
                policy.protectionMode(),
                action,
                reason,
                blockExpiry,
                now);
    }

    public Optional<SourceBlock> unblock(String sourceIp, String actor) {
        Instant now = clock.instant();
        PolicySnapshot policy = policyService.current();
        Optional<SourceBlock> released = blockRegistry.release(sourceIp, now, actor,
                Duration.ofMillis(policy.blockDurationMillis()));
        if (released.isPresent()) {
            synchronized (protectionEvents) {
                protectionEvents.add(now + " manual unblock of " + sourceIp + " by " + actor);
            }
            blockPersistenceService.save(released.get());
            publishBlockEvent(released.get(), now);
        }
        return released;
    }

    private void publishBlockEvent(SourceBlock block, Instant now) {
        try {
            eventQueueService.publishCritical(SecurityEvent.of(
                    block.releasedAt() != null ? SecurityEvent.BLOCK_RELEASED : SecurityEvent.BLOCK_CREATED,
                    now, block.sourceIp(), block.blockId(), true, Map.of(
                            "blockId", block.blockId(),
                            "ruleId", block.ruleId(),
                            "reason", block.reason() == null ? "" : block.reason(),
                            "createdAt", block.createdAt().toString(),
                            "expiry", block.expiry().toString(),
                            "releasedAt", block.releasedAt() == null ? "" : block.releasedAt().toString(),
                            "releasedBy", block.releasedBy() == null ? "" : block.releasedBy(),
                            "state", block.state())));
        } catch (RuntimeException ex) {
            log.warn("Could not publish block event for {}: {}", block.blockId(), ex.getMessage());
        }
    }

    private void publishProtectionChanged(ProtectionMode mode, PolicySnapshot policy, Instant now, String actor) {
        try {
            eventQueueService.publishCritical(SecurityEvent.of(SecurityEvent.PROTECTION_CHANGED, now, null, null, true,
                    Map.of(
                            "mode", mode.name(),
                            "policyVersion", policy.policyVersion(),
                            "actor", actor,
                            "snapshot", policy)));
        } catch (RuntimeException ex) {
            log.warn("Could not publish protection change event: {}", ex.getMessage());
        }
    }

    public List<SourceBlock> activeBlocks() {
        return blockRegistry.activeBlocks(clock.instant());
    }

    public List<SourceBlock> blockHistory() {
        return blockRegistry.history();
    }

    public List<String> protectionEvents() {
        synchronized (protectionEvents) {
            return List.copyOf(protectionEvents);
        }
    }

    public double availableTokens(String sourceIp) {
        TokenBucket bucket = buckets.get(sourceIp);
        return bucket == null ? 0 : bucket.availableTokens(clock.instant());
    }

    /** Clears live enforcement state (used by the analyst reset). */
    public void resetState() {
        buckets.clear();
        blockRegistry.clear();
        synchronized (protectionEvents) {
            protectionEvents.clear();
        }
        log.info("Protection state cleared by analyst reset");
    }
}
