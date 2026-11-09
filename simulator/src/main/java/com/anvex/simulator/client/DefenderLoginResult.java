package com.anvex.simulator.client;

/**
 * Outcome of one login attempt issued by the simulator. Transport failures are kept separate from
 * defender rejections so the UI never conflates "the network failed" with "the defender refused".
 */
public record DefenderLoginResult(
        String status,
        int httpStatus,
        String requestId,
        String sourceIp,
        double latencyMs,
        boolean transportFailure,
        String transportError
) {
    public static DefenderLoginResult transportError(String message, double latencyMs) {
        return new DefenderLoginResult("transport_error", 0, null, null, latencyMs, true, message);
    }
}
