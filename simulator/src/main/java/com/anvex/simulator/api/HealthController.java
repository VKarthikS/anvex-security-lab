package com.anvex.simulator.api;

import com.anvex.common.HealthStatus;
import com.anvex.common.Identity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final Clock clock;
    private final String version;
    private final String buildTime;

    public HealthController(Clock clock,
                            @Value("${anvex.build-version:1.0.0}") String version,
                            @Value("${anvex.build-time:unknown}") String buildTime) {
        this.clock = clock;
        this.version = version;
        this.buildTime = buildTime;
    }

    @GetMapping("/health")
    public HealthStatus health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("scheduler", "UP");
        components.put("workerPool", "UP");
        return new HealthStatus("UP", "anvex-simulator", version, "v1",
                ManagementFactory.getRuntimeMXBean().getUptime(), System.getProperty("java.version"), components);
    }

    @GetMapping("/identity")
    public Identity identity() {
        Instant parsed;
        try {
            parsed = Instant.parse(buildTime);
        } catch (RuntimeException ex) {
            parsed = clock.instant();
        }
        return new Identity("ANVEX", version, "v1", "anvex/1", parsed);
    }
}
