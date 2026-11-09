package com.anvex.common;

public record RunCounters(
        long planned,
        long sent,
        long completed,
        long cancelled,
        long transportErrors,
        long authenticationSuccess,
        long authenticationFailure,
        long rateLimited,
        long sourceBlocked,
        long invalidOrOverload
) {
    public static RunCounters empty() {
        return new RunCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public RunCounters plus(RunCounters other) {
        return new RunCounters(
                planned + other.planned,
                sent + other.sent,
                completed + other.completed,
                cancelled + other.cancelled,
                transportErrors + other.transportErrors,
                authenticationSuccess + other.authenticationSuccess,
                authenticationFailure + other.authenticationFailure,
                rateLimited + other.rateLimited,
                sourceBlocked + other.sourceBlocked,
                invalidOrOverload + other.invalidOrOverload);
    }
}
