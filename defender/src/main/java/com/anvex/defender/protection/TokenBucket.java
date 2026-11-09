package com.anvex.defender.protection;

import java.time.Duration;
import java.time.Instant;

/**
 * A simple token bucket. Tokens refill continuously at {@code ratePerSecond} and are capped at
 * {@code burstCapacity}.
 */
public class TokenBucket {

    private final int ratePerSecond;
    private final int burstCapacity;
    private double tokens;
    private Instant lastRefill;

    public TokenBucket(int ratePerSecond, int burstCapacity, Instant now) {
        this.ratePerSecond = ratePerSecond;
        this.burstCapacity = burstCapacity;
        this.tokens = burstCapacity;
        this.lastRefill = now;
    }

    public synchronized boolean tryConsume(Instant now) {
        refill(now);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Milliseconds until one token is available again. */
    public synchronized long retryAfterMillis(Instant now) {
        refill(now);
        if (tokens >= 1.0) {
            return 0;
        }
        double missing = 1.0 - tokens;
        return (long) Math.ceil(missing / ratePerSecond * 1000.0);
    }

    public synchronized double availableTokens(Instant now) {
        refill(now);
        return tokens;
    }

    private void refill(Instant now) {
        if (now.isAfter(lastRefill)) {
            Duration elapsed = Duration.between(lastRefill, now);
            tokens = Math.min(burstCapacity, tokens + elapsed.toMillis() / 1000.0 * ratePerSecond);
            lastRefill = now;
        }
    }
}
