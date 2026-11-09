package com.anvex.defender.detection;

import java.util.Optional;

/**
 * A detection rule. Rules are independent of one another: several may match the same decision and
 * each match is reported separately.
 */
public interface DetectionRule {

    String id();

    DetectionCategory category();

    Optional<RuleMatch> evaluate(RuleEvaluationContext context);
}
