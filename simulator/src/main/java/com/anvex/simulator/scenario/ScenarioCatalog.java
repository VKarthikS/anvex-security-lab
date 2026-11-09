package com.anvex.simulator.scenario;

import com.anvex.common.ScenarioDescriptor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScenarioCatalog {

    private final Map<String, ScenarioStrategy> strategies = new LinkedHashMap<>();

    public ScenarioCatalog(List<ScenarioStrategy> discovered) {
        for (ScenarioStrategy strategy : discovered) {
            strategies.put(strategy.id(), strategy);
        }
    }

    public ScenarioStrategy require(String id) {
        ScenarioStrategy strategy = strategies.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown scenario: " + id);
        }
        return strategy;
    }

    public List<ScenarioDescriptor> descriptors() {
        return strategies.values().stream().map(ScenarioStrategy::descriptor).toList();
    }
}
