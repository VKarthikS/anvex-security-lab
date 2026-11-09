package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rapid requests: a bounded burst of login requests aimed at one account. Valid credentials are
 * used by default so the observable behaviour is request rate rather than credential failures.
 */
@Component
public class RapidRequestsScenario implements ScenarioStrategy {

    public static final String ID = "rapid-requests";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScenarioDescriptor descriptor() {
        return new ScenarioDescriptor(ID, "Rapid requests",
                "A bounded burst of login requests aimed at one account.",
                12, 10, 1, false, List.of());
    }

    @Override
    public List<ScenarioRequest> plan(RunConfig config, List<DemoAccount> availableAccounts) {
        String username = ScenarioSupport.firstTargetUsername(config, availableAccounts);
        String password = ScenarioSupport.demoPasswordFor(username, availableAccounts);
        int total = ScenarioSupport.plannedRequestCount(config);
        List<ScenarioRequest> requests = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            requests.add(new ScenarioRequest(username, password));
        }
        return requests;
    }
}
