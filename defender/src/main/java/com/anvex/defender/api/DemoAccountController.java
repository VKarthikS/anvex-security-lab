package com.anvex.defender.api;

import com.anvex.common.DemoAccount;
import com.anvex.defender.auth.SeedAccounts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoAccountController {

    private final Clock clock;

    public DemoAccountController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/accounts")
    public List<DemoAccount> accounts() {
        Instant seededAt = clock.instant();
        return SeedAccounts.ALL.stream()
                .map(seed -> new DemoAccount(seed.username(), seed.displayName(), seed.role(), seed.demoPassword(), seededAt))
                .toList();
    }
}
