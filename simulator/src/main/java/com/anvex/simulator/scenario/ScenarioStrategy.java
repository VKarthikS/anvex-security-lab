package com.anvex.simulator.scenario;

import com.anvex.common.DemoAccount;
import com.anvex.common.RunConfig;
import com.anvex.common.ScenarioDescriptor;

import java.util.List;

/**
 * Strategy interface for traffic generation. Each implementation knows how to turn a validated run
 * configuration into a bounded list of authentication attempts.
 */
public interface ScenarioStrategy {

    String id();

    ScenarioDescriptor descriptor();

    List<ScenarioRequest> plan(RunConfig config, List<DemoAccount> availableAccounts);
}
