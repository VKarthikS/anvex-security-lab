package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual login: exactly one attempt with operator supplied credentials. Used for the legitimate
 * baseline and for deliberately failed comparisons.
 */
@Component
public class ManualLoginScenario implements ScenarioStrategy {

    public static final String ID = "manual-login";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ScenarioDescriptor descriptor() {
        return new ScenarioDescriptor(ID, "Manual login",
                "A single login attempt with operator supplied credentials.",
                1, 1, 1, false, List.of());
    }

    @Override
    public List<ScenarioRequest> plan(RunConfig config, List<DemoAccount> availableAccounts) {
        return List.of();
    }
}
