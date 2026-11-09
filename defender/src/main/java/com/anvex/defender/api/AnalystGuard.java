package com.anvex.defender.api;

import jakarta.servlet.http.HttpServletRequest;

import com.anvex.common.ApiError;

import java.time.Clock;
import java.util.UUID;

/**
 * Analyst controls are restricted to a validated loopback caller. Forwarded headers are ignored, so
 * a remote simulator can never switch protection off or reset defender state.
 */
public final class AnalystGuard {

    private AnalystGuard() {
    }

    public static void requireLoopback(HttpServletRequest request, Clock clock) {
        String remote = RequestSupport.remoteAddress(request);
        if (!RequestSupport.isLoopback(remote)) {
            throw new AnalystLoopbackRequiredException(remote, clock.instant());
        }
    }

    public static class AnalystLoopbackRequiredException extends RuntimeException {
        private final String remoteAddress;

        public AnalystLoopbackRequiredException(String remoteAddress, java.time.Instant now) {
            super("Analyst controls are only available to a loopback caller (request came from " + remoteAddress + ").");
            this.remoteAddress = remoteAddress;
        }

        public ApiError toApiError(Clock clock) {
            return ApiError.of("analyst_loopback_required", getMessage(), UUID.randomUUID().toString(), clock.instant());
        }
    }
}
