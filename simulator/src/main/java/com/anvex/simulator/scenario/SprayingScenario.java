package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Password spraying: a small synthetic guess set cycled across many seeded accounts. The same
 * incorrect guess is replayed against different usernames, which is the observable pattern that
 * the defender labels as <em>suspected</em> spraying.
 */
@Component
public class SprayingScenario implements ScenarioStrategy {

    public static final String ID = "password-spraying";
    private static final String SPRAY_GUESS = "Spray!9999";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScenarioDescriptor descriptor() {
        return new ScenarioDescriptor(ID, "Password spraying",
                "A synthetic guess set cycled across many seeded accounts.",
                2, 12, 12, true, List.of());
    }

    @Override
    public List<ScenarioRequest> plan(RunConfig config, List<DemoAccount> availableAccounts) {
        List<String> usernames = ScenarioSupport.targetUsernames(config, availableAccounts, 12);
        int total = ScenarioSupport.plannedRequestCount(config);
        List<ScenarioRequest> requests = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            requests.add(new ScenarioRequest(usernames.get(i % usernames.size()), SPRAY_GUESS));
        }
        return requests;
    }
}
