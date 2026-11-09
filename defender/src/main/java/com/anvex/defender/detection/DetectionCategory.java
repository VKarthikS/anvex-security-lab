package com.anvex.defender.detection;

/**
 * Categories are inferred from observed behaviour only. A simulator run label or scenario name is
 * never consulted when classifying activity.
 */
public enum DetectionCategory {
    NONE,
    SUSPECTED_REPEATED_GUESSING,
    SUSPECTED_PASSWORD_SPRAYING,
    SUSPECTED_RAPID_REQUESTS;

    /**
     * Deterministic primary-category priority when several rules match the same decision.
     */
    public int priority() {
        return switch (this) {
            case SUSPECTED_REPEATED_GUESSING -> 0;
            case SUSPECTED_PASSWORD_SPRAYING -> 1;
            case SUSPECTED_RAPID_REQUESTS -> 2;
            case NONE -> 3;
        };
    }

    public String displayName() {
        return switch (this) {
            case NONE -> "No pattern";
            case SUSPECTED_REPEATED_GUESSING -> "Suspected repeated guessing";
            case SUSPECTED_PASSWORD_SPRAYING -> "Suspected password spraying";
            case SUSPECTED_RAPID_REQUESTS -> "Suspected rapid requests";
        };
    }
}
