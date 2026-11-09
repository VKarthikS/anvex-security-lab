package com.anvex.defender.detection;

/**
 * Stable rule identifiers. The id, the exact threshold and the window bounds are stored with every
 * decision so evidence can be re-read independently of later configuration changes.
 */
public final class RuleIds {

    public static final String REPEATED_GUESSING = "repeated-guessing";
    public static final String PASSWORD_SPRAYING = "password-spraying";
    public static final String RAPID_REQUESTS = "rapid-requests";

    private RuleIds() {
    }
}
