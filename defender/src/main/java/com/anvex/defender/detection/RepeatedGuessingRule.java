package com.anvex.defender.detection;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Suspected repeated guessing: at least N failed authentications for one source/account pair
 * inside the configured window.
 */
@Component
public class RepeatedGuessingRule implements DetectionRule {

    @Override
    public String id() {
        return RuleIds.REPEATED_GUESSING;
    }

    @Override
    public DetectionCategory category() {
        return DetectionCategory.SUSPECTED_REPEATED_GUESSING;
    }

    @Override
    public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
        int threshold = context.policy().repeatedGuessingFailureThreshold();
        if (context.accountFailureCount() < threshold) {
            return Optional.empty();
        }
        return Optional.of(new RuleMatch(
                id(),
                category(),
                threshold,
                context.accountFailureCount(),
                1,
                context.accountFailureWindowStart(),
                context.now(),
                threshold + " failed authentications for " + context.username() + " from one source within "
                        + (context.policy().repeatedGuessingWindowMillis() / 1000) + "s."));
    }
}
