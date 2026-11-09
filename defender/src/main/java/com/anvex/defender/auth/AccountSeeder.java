package com.anvex.defender.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountSeeder(AccountRepository accountRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        int created = 0;
        for (SeedAccounts.Seed seed : SeedAccounts.ALL) {
            if (accountRepository.countByUsername(seed.username()) > 0) {
                continue;
            }
            accountRepository.save(new Account(
                    seed.username(),
                    seed.displayName(),
                    seed.role(),
                    passwordEncoder.encode(seed.demoPassword()),
                    now));
            created++;
        }
        if (created > 0) {
            log.info("Seeded {} dummy account(s); {} account(s) already existed", created, SeedAccounts.ALL.size() - created);
        }
    }
}
