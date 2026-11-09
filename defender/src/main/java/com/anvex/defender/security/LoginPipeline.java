package com.anvex.defender.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.anvex.common.AwarenessNotice;
import com.anvex.common.LoginRequest;
import com.anvex.common.LoginResponse;
import com.anvex.common.LoginUser;
import com.anvex.defender.alert.AlertService;
import com.anvex.defender.auth.AuthenticationOutcome;
import com.anvex.defender.auth.AuthenticationService;
import com.anvex.defender.detection.DetectionCategory;
import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.DetectionOutcome;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.PolicySnapshot;
import com.anvex.defender.detection.ProtectionMode;
import com.anvex.defender.detection.RuleMatch;
import com.anvex.defender.evidence.EvidenceService;
import com.anvex.defender.events.EventQueueService;
import com.anvex.defender.events.SecurityEvent;
import com.anvex.defender.events.ServiceOverloadedException;
import com.anvex.defender.evidence.EvidenceSnapshot;
import com.anvex.defender.metrics.DefenderMetrics;
import com.anvex.defender.protection.ProtectionDecision;
import com.anvex.defender.protection.ProtectionService;

/**
 * The synchronous request lifecycle of the dummy application.
 *
 * <ol>
 *   <li>assign request id and server timestamp, count the incoming request</li>
 *   <li>atomically update rolling counters and inspect the protection state</li>
 *   <li>(protection gate — implemented with the protection lifecycle)</li>
 *   <li>admitted requests reach password verification and are counted separately</li>
 *   <li>record the authentication result, update failure counters, evaluate failure rules</li>
 *   <li>activate restrictions and generate evidence when required</li>
 *   <li>publish immutable events for background persistence and UI delivery</li>
 * </ol>
 */
@Service
public class LoginPipeline {

    private final AuthenticationService authenticationService;
    private final DetectionEngine detectionEngine;
    private final PolicyService policyService;
    private final EvidenceService evidenceService;
    private final AlertService alertService;
    private final DefenderMetrics metrics;
    private final ProtectionService protectionService;
    private final EventQueueService eventQueueService;
    private final Clock clock;

    public LoginPipeline(AuthenticationService authenticationService,
                         DetectionEngine detectionEngine,
                         PolicyService policyService,
                         EvidenceService evidenceService,
                         AlertService alertService,
                         DefenderMetrics metrics,
                         ProtectionService protectionService,
                         EventQueueService eventQueueService,
                         Clock clock) {
        this.authenticationService = authenticationService;
        this.detectionEngine = detectionEngine;
        this.policyService = policyService;
        this.evidenceService = evidenceService;
        this.alertService = alertService;
        this.metrics = metrics;
        this.protectionService = protectionService;
        this.eventQueueService = eventQueueService;
        this.clock = clock;
    }

    public LoginResult process(LoginRequest request, String sourceIp, String requestId) {
        Instant now = clock.instant();
        PolicySnapshot policy = policyService.current();

        metrics.recordIncoming(request.runId());
        DetectionOutcome incomingOutcome = detectionEngine.recordIncoming(sourceIp, now, requestId);
        eventQueueService.publishTelemetry(SecurityEvent.of(SecurityEvent.LOGIN_REQUEST, now, sourceIp, requestId, false,
                Map.of("username", AuthenticationService.normalizeUsername(request.username()) == null
                        ? "" : AuthenticationService.normalizeUsername(request.username()))));

        ProtectionDecision protectionDecision = protectionService.gate(sourceIp, incomingOutcome, now, request.runId());
        if (!protectionDecision.allowed()) {
            String status = protectionDecision.blocked() ? LoginResponse.SOURCE_BLOCKED : LoginResponse.RATE_LIMITED;
            eventQueueService.publishTelemetry(SecurityEvent.of(SecurityEvent.LOGIN_REQUEST, now, sourceIp, requestId,
                    false, Map.of("outcome", status, "retryAfterSeconds", protectionDecision.retryAfterSeconds())));
            return new LoginResult(status, requestId, now, null, List.of(), protectionDecision.retryAfterSeconds());
        }

        metrics.recordAdmittedToAuthentication(request.runId());
        AuthenticationOutcome outcome = authenticationService.authenticate(request.username(), request.password());

        if (outcome.successful()) {
            metrics.recordAuthenticationSuccess(request.runId());
            detectionEngine.recordSuccess(sourceIp, outcome.normalizedUsername(), now, requestId);
            eventQueueService.publishTelemetry(SecurityEvent.of(SecurityEvent.AUTH_SUCCESS, now, sourceIp, requestId,
                    false, Map.of("username", outcome.normalizedUsername())));
            List<AwarenessNotice> awareness = awarenessFor(sourceIp, outcome.normalizedUsername(), now);
            return new LoginResult(LoginResponse.AUTHENTICATED, requestId, now, outcome.user(), awareness, null);
        }

        metrics.recordAuthenticationFailure(request.runId());
        String normalizedUsername = outcome.normalizedUsername();
        DetectionOutcome failureOutcome = detectionEngine.recordFailure(sourceIp, normalizedUsername, now, requestId);
        eventQueueService.publishTelemetry(SecurityEvent.of(SecurityEvent.AUTH_FAILURE, now, sourceIp, requestId, false,
                Map.of("username", normalizedUsername == null ? "" : normalizedUsername)));

        List<AwarenessNotice> awareness = new ArrayList<>();
        if (failureOutcome.hasMatches()) {
            EvidenceSnapshot evidence = buildEvidence(sourceIp, normalizedUsername, requestId, now, policy,
                    failureOutcome, incomingOutcome);
            try {
                eventQueueService.publishCritical(SecurityEvent.of(SecurityEvent.DETECTION_DECISION, now, sourceIp,
                        requestId, true, evidencePayload(evidence)));
            } catch (ServiceOverloadedException ex) {
                metrics.recordInvalidOrOverload(request.runId());
                return new LoginResult(LoginResponse.SERVICE_OVERLOADED, requestId, now, null, List.of(), null);
            }
            awareness.add(AwarenessNotice.warning(
                    "Suspicious activity observed",
                    "Repeated failed attempts for " + normalizedUsername + " were detected from your network address.",
                    List.of(evidence.decisionId())));
        }
        protectionService.applyFailureRules(sourceIp, failureOutcome, requestId, now);

        return new LoginResult(LoginResponse.INVALID_CREDENTIALS, requestId, now, null, awareness, null);
    }

    private List<AwarenessNotice> awarenessFor(String sourceIp, String username, Instant now) {
        DetectionOutcome reevaluated = detectionEngine.evaluateNow(sourceIp, now);
        if (!reevaluated.hasMatches()) {
            return List.of(AwarenessNotice.info("Signed in", "Authentication succeeded for " + username + "."));
        }
        List<String> decisionIds = evidenceService.recent(5).stream()
                .filter(evidence -> evidence.sourceIp().equals(sourceIp))
                .map(EvidenceSnapshot::decisionId)
                .toList();
        return List.of(AwarenessNotice.warning(
                "Signed in after suspicious activity",
                "This login followed suspicious activity from your network address. The correlation is not proof of "
                        + "compromise; review the activity and change the dummy password if you are unsure.",
                decisionIds));
    }

    private static Map<String, Object> evidencePayload(EvidenceSnapshot evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionId", evidence.decisionId());
        payload.put("category", evidence.category().name());
        payload.put("affectedUsernames", evidence.affectedUsernames());
        payload.put("matchedRules", evidence.matchedRules());
        payload.put("observed", evidence.observed());
        payload.put("windows", evidence.windows());
        payload.put("eventRefs", evidence.triggeringEventIds());
        payload.put("policyVersion", evidence.policyVersion());
        payload.put("policySnapshot", evidence.policySnapshot());
        payload.put("protectionMode", evidence.protectionMode().name());
        payload.put("action", evidence.action());
        payload.put("reason", evidence.reason());
        payload.put("blockExpiry", evidence.blockExpiry() == null ? "" : evidence.blockExpiry().toString());
        return payload;
    }

    private EvidenceSnapshot buildEvidence(String sourceIp, String username, String requestId, Instant now,
                                           PolicySnapshot policy, DetectionOutcome failureOutcome,
                                           DetectionOutcome incomingOutcome) {
        List<RuleMatch> matches = new ArrayList<>(failureOutcome.matches());
        matches.addAll(incomingOutcome.matches());
        DetectionCategory category = matches.stream()
                .map(RuleMatch::category)
                .min((left, right) -> Integer.compare(left.priority(), right.priority()))
                .orElse(DetectionCategory.NONE);

        Map<String, Object> observed = new LinkedHashMap<>();
        Map<String, Object> windows = new LinkedHashMap<>();
        for (RuleMatch match : matches) {
            observed.put(match.ruleId(), Map.of(
                    "observedCount", match.observedCount(),
                    "threshold", match.threshold(),
                    "observedDistinctAccounts", match.observedDistinctAccounts()));
            windows.put(match.ruleId(), Map.of(
                    "start", match.windowStart(),
                    "end", match.windowEnd()));
        }

        List<String> affected = new ArrayList<>();
        if (username != null) {
            affected.add(username);
        }
        Instant sprayingStart = now.minusMillis(policy.sprayingWindowMillis());
        for (String account : detectionEngine.distinctAccountsSince(sourceIp, sprayingStart)) {
            if (!affected.contains(account)) {
                affected.add(account);
            }
        }

        boolean enforced = policy.protectionMode() == ProtectionMode.ON;
        String action = enforced ? EvidenceSnapshot.ACTION_ENFORCED : EvidenceSnapshot.ACTION_OBSERVATION_ONLY;
        String reason = category == DetectionCategory.NONE
                ? "Rule threshold reached."
                : category.displayName() + ": " + matches.get(0).description();

        EvidenceSnapshot evidence = evidenceService.record(
                sourceIp,
                affected,
                category,
                matches,
                observed,
                windows,
                List.of(requestId),
                policy.policyVersion(),
                policy,
                policy.protectionMode(),
                action,
                reason,
                null,
                now);

        for (RuleMatch match : matches) {
            alertService.upsert(sourceIp, match, evidence.decisionId(), now);
            metrics.recordDetectionAlert();
        }
        return evidence;
    }
}
