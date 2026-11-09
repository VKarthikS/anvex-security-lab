package com.anvex.simulator.api;

import java.util.Map;

/**
 * Read-only snapshot of the configured ANVEX defender for the local simulator UI.
 * A missing snapshot is explicit; the simulator must not invent defender evidence.
 */
public record DefenderOverview(
        String targetBaseUrl,
        boolean reachable,
        boolean compatible,
        boolean available,
        String message,
        Map<String, Object> snapshot
) {
}
