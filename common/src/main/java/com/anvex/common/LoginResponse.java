package com.anvex.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String status,
        String requestId,
        Instant serverTime,
        LoginUser user,
        List<AwarenessNotice> awareness,
        Integer retryAfterSeconds
) {
    public static final String AUTHENTICATED = "authenticated";
    public static final String INVALID_CREDENTIALS = "invalid_credentials";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String SOURCE_BLOCKED = "source_blocked";
    public static final String SERVICE_OVERLOADED = "service_overloaded";

    public static LoginResponse authenticated(String requestId, Instant serverTime, LoginUser user, List<AwarenessNotice> awareness) {
        return new LoginResponse(AUTHENTICATED, requestId, serverTime, user, awareness, null);
    }

    public static LoginResponse invalidCredentials(String requestId, Instant serverTime, List<AwarenessNotice> awareness) {
        return new LoginResponse(INVALID_CREDENTIALS, requestId, serverTime, null, awareness, null);
    }

    public static LoginResponse rejected(String status, String requestId, Instant serverTime, int retryAfterSeconds, List<AwarenessNotice> awareness) {
        return new LoginResponse(status, requestId, serverTime, null, awareness, retryAfterSeconds);
    }
}
