package com.anvex.defender.security;

import java.time.Instant;
import java.util.List;

import com.anvex.common.AwarenessNotice;
import com.anvex.common.LoginUser;

/**
 * The result of one login pipeline execution.
 */
public record LoginResult(
        String status,
        String requestId,
        Instant serverTime,
        LoginUser user,
        List<AwarenessNotice> awareness,
        Integer retryAfterSeconds
) {
}
