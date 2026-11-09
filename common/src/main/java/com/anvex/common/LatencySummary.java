package com.anvex.common;

public record LatencySummary(long count, double avgMs, double p50Ms, double p95Ms, double maxMs) {
    public static LatencySummary empty() {
        return new LatencySummary(0, 0, 0, 0, 0);
    }
}
