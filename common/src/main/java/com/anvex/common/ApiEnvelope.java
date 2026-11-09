package com.anvex.common;

import java.time.Instant;

public record ApiEnvelope<T>(String apiVersion, Instant serverTime, T data) {
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>("v1", Instant.now(), data);
    }
}
