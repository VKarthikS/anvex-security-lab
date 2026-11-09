package com.anvex.defender.auth;

import com.anvex.common.LoginUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Credential verification for the dummy application. Passwords are compared against salted BCrypt
 * hashes and are never logged, stored or derived into fingerprints.
 */
@Service
public class AuthenticationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticationOutcome authenticate(String rawUsername, String rawPassword) {
        String username = normalizeUsername(rawUsername);
        if (username == null || rawPassword == null || rawPassword.isEmpty()) {
            return AuthenticationOutcome.failure(username);
        }
        Optional<Account> account = accountRepository.findByUsername(username);
        if (account.isEmpty()) {
            return AuthenticationOutcome.failure(username);
        }
        boolean matches = passwordEncoder.matches(rawPassword, account.get().getPasswordHash());
        if (!matches) {
            return AuthenticationOutcome.failure(username);
        }
        Account stored = account.get();
        return AuthenticationOutcome.success(
                username,
                new LoginUser(stored.getUsername(), stored.getDisplayName(), stored.getRole()));
    }

    public static String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            return null;
        }
        String trimmed = rawUsername.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
