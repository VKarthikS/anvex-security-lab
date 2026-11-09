package com.anvex.simulator.api;

import com.anvex.simulator.client.DefenderClient;
import com.anvex.simulator.client.TargetHandshake;
import com.anvex.simulator.config.TargetSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/target")
public class TargetController {

    private final TargetSettings targetSettings;
    private final DefenderClient defenderClient;

    public TargetController(TargetSettings targetSettings, DefenderClient defenderClient) {
        this.targetSettings = targetSettings;
        this.defenderClient = defenderClient;
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
    public DefenderOverview overview() {
        String baseUrl = targetSettings.baseUrl();
        TargetHandshake handshake = defenderClient.handshake(baseUrl);
        if (!handshake.reachable() || !handshake.compatible()) {
            return new DefenderOverview(baseUrl, handshake.reachable(), handshake.compatible(), false,
                    handshake.message(), null);
        }
        Map<String, Object> snapshot = defenderClient.fetchDashboardSnapshot(baseUrl);
        if (snapshot.isEmpty()) {
            return new DefenderOverview(baseUrl, true, true, false,
                    "The defender responded to the handshake, but its dashboard snapshot is unavailable.", null);
        }
        return new DefenderOverview(baseUrl, true, true, true,
                "Verified ANVEX defender snapshot.", snapshot);
    }

    @PostMapping("/test")
    public TargetHandshake test() {
        return defenderClient.handshake(targetSettings.baseUrl());
    }
}
