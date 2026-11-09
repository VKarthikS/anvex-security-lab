package com.anvex.simulator.client;

public record TargetHandshake(
        String baseUrl,
        boolean reachable,
        boolean compatible,
        String product,
        String version,
        String apiVersion,
        String protocol,
        String message
) {
}
