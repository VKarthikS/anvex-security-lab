package com.anvex.defender.auth;

import com.anvex.common.LoginUser;

public record AuthenticationOutcome(boolean successful, String normalizedUsername, LoginUser user) {

    public static AuthenticationOutcome success(String normalizedUsername, LoginUser user) {
        return new AuthenticationOutcome(true, normalizedUsername, user);
    }

    public static AuthenticationOutcome failure(String normalizedUsername) {
        return new AuthenticationOutcome(false, normalizedUsername, null);
    }
}
