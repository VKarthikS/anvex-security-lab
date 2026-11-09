package com.anvex.common;

import java.util.Map;

public record HealthStatus(
        String status,
        String service,
        String version,
        String apiVersion,
        long uptimeMs,
        String javaVersion,
        Map<String, String> components
) {
}
