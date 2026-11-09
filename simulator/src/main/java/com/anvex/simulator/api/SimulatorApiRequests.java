package com.anvex.simulator.api;

import com.anvex.common.DemoAccount;
import com.anvex.common.LoginRequest;
import com.anvex.common.RunConfig;
import jakarta.validation.Valid;

public final class SimulatorApiRequests {

    private SimulatorApiRequests() {
    }

    public record TargetRequest(@Valid @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String baseUrl) {
    }

    public record ManualLoginRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String username,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 256) String password) {
    }
}
