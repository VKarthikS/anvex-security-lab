package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ScenarioSupport {

    private ScenarioSupport() {
    }

    static int plannedRequestCount(RunConfig config) {
        long byRate = (long) config.ratePerSecond() * config.durationSeconds();
        return (int) Math.min(config.requestLimit(), Math.min(byRate, 1000));
    }

    static String firstTargetUsername(RunConfig config, List<DemoAccount> availableAccounts) {
        if (config.targetUsernames() != null && !config.targetUsernames().isEmpty()) {
            return config.targetUsernames().get(0);
        }
        if (!availableAccounts.isEmpty()) {
            return availableAccounts.get(0).username();
        }
        return "asha.raman";
    }

    static List<String> targetUsernames(RunConfig config, List<DemoAccount> availableAccounts, int minimum) {
        Set<String> selected = new LinkedHashSet<>();
        if (config.targetUsernames() != null) {
            config.targetUsernames().stream()
                    .filter(username -> username != null && !username.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .forEach(selected::add);
        }
        for (DemoAccount account : availableAccounts) {
            if (selected.size() >= minimum) {
                break;
            }
            selected.add(account.username());
        }
        if (selected.isEmpty()) {
            selected.add("asha.raman");
        }
        return List.copyOf(selected);
    }

    static String demoPasswordFor(String username, List<DemoAccount> availableAccounts) {
        for (DemoAccount account : availableAccounts) {
            if (account.username().equalsIgnoreCase(username)) {
                return account.demoPassword();
            }
        }
        return "Unknown!0000";
    }
}
