package com.anvex.defender.detection;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Suspected password spraying: enough failures from one source, spread over enough distinct
 * usernames, inside the configured window. The label is deliberately "suspected": without recording
 * password material, spread failures cannot prove that one password was reused.
 */
@Component
public class PasswordSprayingRule implements DetectionRule {

    @Override
    public String id() {
        return RuleIds.PASSWORD_SPRAYING;
    }

    @Override
    public DetectionCategory category() {
        return DetectionCategory.SUSPECTED_PASSWORD_SPRAYING;
    }

    @Override
    public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
        int failureThreshold = context.policy().sprayingFailureThreshold();
        int accountThreshold = context.policy().sprayingDistinctAccountThreshold();
        if (context.sourceFailureCount() < failureThreshold || context.distinctAccountCount() < accountThreshold) {
            return Optional.empty();
        }
        return Optional.of(new RuleMatch(
                id(),
                category(),
                failureThreshold,
                context.sourceFailureCount(),
                context.distinctAccountCount(),
                context.distinctWindowStart(),
                context.now(),
                failureThreshold + " failures spanning " + context.distinctAccountCount() + " distinct usernames ("
                        + accountThreshold + " required) from one source within "
                        + (context.policy().sprayingWindowMillis() / 1000) + "s. Password reuse is not proven."));
    }
}
