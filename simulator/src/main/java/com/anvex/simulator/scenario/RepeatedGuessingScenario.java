package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Repeated guessing: failed guesses against one selected dummy account. The generated password is
 * intentionally wrong so the scenario produces genuine authentication failures.
 */
@Component
public class RepeatedGuessingScenario implements ScenarioStrategy {

    public static final String ID = "repeated-guessing";
    private static final String WRONG_PASSWORD = "Wrong!9999";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScenarioDescriptor descriptor() {
        return new ScenarioDescriptor(ID, "Repeated guessing",
                "Repeated failed guesses against one selected dummy account.",
                2, 12, 1, false, List.of());
    }

    @Override
    public List<ScenarioRequest> plan(RunConfig config, List<DemoAccount> availableAccounts) {
        String username = ScenarioSupport.firstTargetUsername(config, availableAccounts);
        int total = ScenarioSupport.plannedRequestCount(config);
        List<ScenarioRequest> requests = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            requests.add(new ScenarioRequest(username, WRONG_PASSWORD));
        }
        return requests;
    }
}
