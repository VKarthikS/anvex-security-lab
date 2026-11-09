package com.anvex.defender.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anvex.defender.alert.AlertService;
import com.anvex.defender.detection.DetectionEngine;
import com.anvex.defender.detection.PolicyService;
import com.anvex.defender.detection.SourceSummary;
import com.anvex.defender.evidence.EvidenceService;
import com.anvex.defender.evidence.EvidenceSnapshot;
import com.anvex.defender.metrics.DefenderMetrics;
import com.anvex.defender.protection.ProtectionService;
import com.anvex.defender.events.EventPipeline;

/**
 * Read-only monitoring endpoints. These are safe to expose on the LAN: they contain no analyst
 * controls and no credentials.
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DefenderMetrics metrics;
    private final PolicyService policyService;
    private final DetectionEngine detectionEngine;
    private final AlertService alertService;
    private final EvidenceService evidenceService;
    private final ProtectionService protectionService;
    private final EventPipeline eventPipeline;

    public DashboardController(DefenderMetrics metrics,
                               PolicyService policyService,
                               DetectionEngine detectionEngine,
                               AlertService alertService,
                               EvidenceService evidenceService,
                               ProtectionService protectionService,
                               EventPipeline eventPipeline) {
        this.metrics = metrics;
        this.policyService = policyService;
        this.detectionEngine = detectionEngine;
        this.alertService = alertService;
        this.evidenceService = evidenceService;
        this.protectionService = protectionService;
        this.eventPipeline = eventPipeline;
    }

    @GetMapping("/metrics/snapshot")
    public Map<String, Object> metricsSnapshot(@RequestParam(required = false) String runId) {
        Map<String, Object> body = metricsSnapshotBody();
        if (runId != null && !runId.isBlank()) {
            body.put("runId", runId);
            body.put("runCounters", metrics.snapshotForRun(runId));
        }
        return body;
    }

    @GetMapping("/dashboard/snapshot")
    public Map<String, Object> dashboardSnapshot() {
        Map<String, Object> body = metricsSnapshotBody();
        body.put("recentAlerts", alertService.alerts(20));
        body.put("recentEvidence", evidenceService.recent(20));
        body.put("activeBlocks", protectionService.activeBlocks());
        body.put("sources", detectionEngine.summaries().values().stream()
                .sorted((left, right) -> Long.compare(right.totalIncoming(), left.totalIncoming()))
                .limit(50)
                .toList());
        body.put("eventPipeline", eventPipeline.diagnostics());
        return body;
    }

    @GetMapping("/alerts")
    public List<AlertService.Alert> alerts(@RequestParam(defaultValue = "50") int limit) {
        return alertService.alerts(limit);
    }

    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<?> alert(@PathVariable String alertId) {
        return alertService.alerts(500).stream()
                .filter(alert -> alert.alertId().equals(alertId))
                .findFirst()
                .<ResponseEntity<?>>map(alert -> ResponseEntity.ok(Map.of("alert", alert)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/alerts/{alertId}/evidence")
    public ResponseEntity<?> alertEvidence(@PathVariable String alertId) {
        return alertService.alerts(500).stream()
                .filter(alert -> alert.alertId().equals(alertId))
                .findFirst()
                .<ResponseEntity<?>>map(alert -> {
                    List<EvidenceSnapshot> evidence = alert.decisionIds().stream()
                            .map(evidenceService::byDecisionId)
                            .flatMap(java.util.Optional::stream)
                            .toList();
                    return ResponseEntity.ok(Map.of("alert", alert, "evidence", evidence));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/evidence/{decisionId}")
    public ResponseEntity<EvidenceSnapshot> evidence(@PathVariable String decisionId) {
        return evidenceService.byDecisionId(decisionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sources")
    public List<SourceSummary> sources() {
        return detectionEngine.summaries().values().stream()
                .sorted((left, right) -> Long.compare(right.totalIncoming(), left.totalIncoming()))
                .toList();
    }

    @GetMapping("/sources/{sourceIp}")
    public ResponseEntity<Map<String, Object>> source(@PathVariable String sourceIp) {
        SourceSummary summary = detectionEngine.summaries().get(sourceIp);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", summary);
        body.put("alerts", alertService.alertsForSource(sourceIp));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> metricsSnapshotBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counters", metrics.snapshot());
        body.put("protection", Map.of(
                "mode", policyService.protectionMode().name(),
                "policyVersion", policyService.current().policyVersion(),
                "observationOnly", !policyService.protectionMode().isOn()));
        return body;
    }
}
