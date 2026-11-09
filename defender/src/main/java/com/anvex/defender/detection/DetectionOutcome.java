package com.anvex.defender.detection;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of one detection evaluation.
 */
public record DetectionOutcome(
        List<RuleMatch> matches,
        DetectionCategory primaryCategory,
        Instant evaluatedAt
) {
    public static DetectionOutcome none(Instant evaluatedAt) {
        return new DetectionOutcome(List.of(), DetectionCategory.NONE, evaluatedAt);
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }
}
