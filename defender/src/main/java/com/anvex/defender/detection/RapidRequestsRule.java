package com.anvex.defender.detection;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Unusually rapid requests: at least N incoming login requests from one source inside the
 * configured window. High request rate is suspicious behaviour, not proof of malicious intent.
 */
@Component
public class RapidRequestsRule implements DetectionRule {

    @Override
    public String id() {
        return RuleIds.RAPID_REQUESTS;
    }

    @Override
    public DetectionCategory category() {
        return DetectionCategory.SUSPECTED_RAPID_REQUESTS;
    }

    @Override
    public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
        int threshold = context.policy().rapidRequestThreshold();
        if (context.incomingCount() < threshold) {
            return Optional.empty();
        }
        return Optional.of(new RuleMatch(
                id(),
                category(),
                threshold,
                context.incomingCount(),
                0,
                context.incomingWindowStart(),
                context.now(),
                threshold + " incoming login requests from one source within "
                        + (context.policy().rapidRequestWindowMillis() / 1000) + "s."));
    }
}
