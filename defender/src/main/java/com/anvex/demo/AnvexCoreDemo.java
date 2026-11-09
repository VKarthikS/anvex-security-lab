package com.anvex.demo;

import java.time.Instant;

import org.springframework.boot.SpringApplication;

import com.anvex.defender.DefenderApplication;
import com.anvex.defender.detection.ActivityState;
import com.anvex.defender.events.ServiceOverloadedException;
import com.anvex.defender.protection.TokenBucket;

/**
 * Entry point of the ANVEX defender. It first runs a short core-Java demo of detection, rate
 * limiting, threads and exceptions, then starts the defender server.
 */
public class AnvexCoreDemo {

    static final int THRESHOLD = 8;   // same as RepeatedGuessingRule: 8 failures in 30 s

    public static void main(String[] args) throws InterruptedException {
        Instant now = Instant.now();

        // 1. Detection: 9 failed logins for one account
        ActivityState state = new ActivityState();
        for (int i = 1; i <= 9; i++) {
            state.recordFailure("asha", now);
            int failures = state.countAccountFailuresSince("asha", now.minusSeconds(30));
            if (failures >= THRESHOLD) {
                System.out.println("Attempt " + i + ": ALERT - repeated guessing");
            } else {
                System.out.println("Attempt " + i + ": failed login");
            }
        }

        // 2. Token bucket: 15 requests at once, burst capacity 10
        TokenBucket bucket = new TokenBucket(5, 10, now);
        int allowed = 0;
        for (int i = 0; i < 15; i++) {
            if (bucket.tryConsume(now)) {
                allowed++;
            }
        }
        System.out.println("Allowed: " + allowed + ", Rate limited: " + (15 - allowed));

        // 3. Two threads share one bucket; synchronized keeps the total at 10
        TokenBucket shared = new TokenBucket(5, 10, now);
        Runnable client = () -> {
            int ok = 0;
            for (int i = 0; i < 10; i++) {
                if (shared.tryConsume(now)) {
                    ok++;
                }
            }
            System.out.println(Thread.currentThread().getName() + " admitted " + ok);
        };
        Thread t1 = new Thread(client, "client-1");
        Thread t2 = new Thread(client, "client-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // 4. Exception: the bucket is empty, so the next request is rejected
        try {
            if (!bucket.tryConsume(now)) {
                throw new ServiceOverloadedException("Too many requests from this IP");
            }
        } catch (ServiceOverloadedException e) {
            System.out.println("Rejected: " + e.getMessage());
        } finally {
            System.out.println("Demo finished.");
        }

        // 5. Start the real ANVEX defender server on port 8080
        SpringApplication.run(DefenderApplication.class, args);
    }
}
