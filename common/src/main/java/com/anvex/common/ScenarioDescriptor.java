package com.anvex.common;

import java.util.List;

public record ScenarioDescriptor(
        String id,
        String displayName,
        String description,
        int defaultRatePerSecond,
        int defaultDurationSeconds,
        int defaultAccountCount,
        boolean requiresAccountCount,
        List<String> defaultTargetUsernames
) {
}
