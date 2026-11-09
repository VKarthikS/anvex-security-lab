package com.anvex.common;

import java.time.Instant;
import java.util.List;

public record DemoAccount(String username, String displayName, String role, String demoPassword, Instant seededAt) {
    public static List<DemoAccount> listOf(String username, String displayName, String role, String demoPassword, Instant seededAt) {
        return List.of(new DemoAccount(username, displayName, role, demoPassword, seededAt));
    }
}
