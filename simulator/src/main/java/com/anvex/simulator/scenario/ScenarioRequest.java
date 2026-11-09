package com.anvex.simulator.scenario;

/**
 * One planned authentication attempt. Passwords are held only in memory for the duration of the
 * request; they are never logged by the simulator.
 */
public record ScenarioRequest(String username, String password) {
}
