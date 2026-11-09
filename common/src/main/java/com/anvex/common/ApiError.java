package com.anvex.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant serverTime,
        Integer retryAfterSeconds
) {
    public static ApiError of(String code, String message, String requestId, Instant serverTime) {
        return new ApiError(code, message, requestId, serverTime, null);
    }
}
