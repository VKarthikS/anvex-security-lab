package com.anvex.simulator.api;

import com.anvex.simulator.client.DefenderClient;
import com.anvex.simulator.client.TargetHandshake;
import com.anvex.simulator.config.TargetSettings;
import com.anvex.simulator.run.RunManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/target")
public class TargetController {

    private final TargetSettings targetSettings;
    private final DefenderClient defenderClient;
    private final RunManager runManager;

    public TargetController(TargetSettings targetSettings, DefenderClient defenderClient, RunManager runManager) {
        this.targetSettings = targetSettings;
        this.defenderClient = defenderClient;
        this.runManager = runManager;
    }

    @GetMapping
    public Map<String, Object> target() {
        return Map.of("baseUrl", targetSettings.baseUrl());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateTarget(@RequestBody SimulatorApiRequests.TargetRequest request) {
        targetSettings.update(request.baseUrl());
        return ResponseEntity.ok(Map.of("baseUrl", targetSettings.baseUrl()));
    }

    /**
     * Same-origin, read-only bridge for Mission Control. A fresh identity handshake prevents
     * an incompatible target from being represented as an ANVEX defender. No analyst writes.
     */
    @GetMapping("/overview")
    public DefenderOverview overview(@RequestParam(required = false) String runId) {
        String baseUrl = targetSettings.baseUrl();
        // Avoid implying a match when the operator changes the configured target mid-run.
        boolean sameTarget = runId != null && runManager.find(runId)
                .map(run -> baseUrl.equals(run.targetBaseUrl())).orElse(false);
        TargetHandshake handshake = defenderClient.handshake(baseUrl);
        if (!handshake.reachable() || !handshake.compatible()) {
            return new DefenderOverview(baseUrl, handshake.reachable(), handshake.compatible(), false,
                    handshake.message(), null, null);
        }
        Map<String, Object> snapshot = defenderClient.fetchDashboardSnapshot(baseUrl);
        if (snapshot.isEmpty()) {
            return new DefenderOverview(baseUrl, true, true, false,
                    "The defender responded to the handshake, but its dashboard snapshot is unavailable.", null, null);
        }
        RunEvidence evidence = runId == null || runId.isBlank() ? null
                : sameTarget
                    ? RunEvidenceMatcher.match(runId, runManager.observedRequestIds(runId), snapshot)
                    : new RunEvidence(runId, false, 0, 0, java.util.List.of(),
                        "This run was not found or its target differs from the current defender.");
        return new DefenderOverview(baseUrl, true, true, true,
                "Verified ANVEX defender snapshot.", snapshot, evidence);
    }

    @PostMapping("/test")
    public TargetHandshake test() {
        return defenderClient.handshake(targetSettings.baseUrl());
    }
}
