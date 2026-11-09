package com.anvex.simulator.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.anvex.common.RunConfig;
import com.anvex.common.RunSnapshot;

@SpringBootTest
class RunManagerTest {

    private static HttpServer server;
    private static final AtomicInteger loginRequests = new AtomicInteger();
    private static final AtomicBoolean dropConnections = new AtomicBoolean(false);

    @Autowired
    private RunManager runManager;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/identity", exchange -> respond(exchange,
                "{\"product\":\"ANVEX\",\"version\":\"1.0.0\",\"apiVersion\":\"v1\",\"protocol\":\"anvex/1\","
                        + "\"buildTime\":\"2026-01-01T00:00:00Z\"}"));
        server.createContext("/api/v1/demo/accounts", exchange -> respond(exchange,
                "[{\"username\":\"asha.raman\",\"displayName\":\"Asha Raman\",\"role\":\"STUDENT\","
                        + "\"demoPassword\":\"Asha!2345\",\"seededAt\":\"2026-01-01T00:00:00Z\"},"
                        + "{\"username\":\"rohan.mehta\",\"displayName\":\"Rohan Mehta\",\"role\":\"STUDENT\","
                        + "\"demoPassword\":\"Rohan!2345\",\"seededAt\":\"2026-01-01T00:00:00Z\"}]"));
        server.createContext("/api/v1/auth/login", exchange -> {
            loginRequests.incrementAndGet();
            if (dropConnections.get()) {
                exchange.close();
                return;
            }
            respond(exchange, "{\"status\":\"invalid_credentials\",\"requestId\":\"srv-1\","
                    + "\"serverTime\":\"2026-01-01T00:00:00Z\",\"awareness\":[]}");
        });
        server.start();
        registry.add("anvex.simulator.target-base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (exchange.getRequestURI().getPath().endsWith("/auth/login")) {
            exchange.getResponseHeaders().add("X-ANVEX-Request-Id", java.util.UUID.randomUUID().toString());
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @BeforeEach
    void reset() {
        loginRequests.set(0);
        dropConnections.set(false);
        runManager.current().ifPresent(run -> {
            try {
                runManager.stop(run.runId());
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    String state = runManager.current().map(RunSnapshot::state).orElse(RunSnapshot.IDLE);
                    if (!RunSnapshot.RUNNING.equals(state) && !RunSnapshot.STOPPING.equals(state)) {
                        return;
                    }
                    Thread.sleep(50);
                }
            } catch (RuntimeException | InterruptedException ignored) {
                // no active run to stop
            }
        });
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RunConfig config(String scenario, int rate, int duration, int limit) {
        return new RunConfig(scenario, rate, duration, List.of("asha.raman"), limit, 2);
    }

    private RunSnapshot waitForCompletion(RunSnapshot start) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        RunSnapshot current = start;
        while (System.currentTimeMillis() < deadline) {
            current = runManager.find(start.runId()).orElse(start);
            if (RunSnapshot.COMPLETED.equals(current.state()) || RunSnapshot.CANCELLED.equals(current.state())
                    || RunSnapshot.FAILED.equals(current.state())) {
                return current;
            }
            Thread.sleep(100);
        }
        return current;
    }

    @Test
    void aScenarioRunsOverRealHttpAndCountsOutcomes() throws Exception {
        RunSnapshot started = runManager.start(config("repeated-guessing", 5, 2, 10));
        RunSnapshot finished = waitForCompletion(started);

        assertThat(finished.state()).isEqualTo(RunSnapshot.COMPLETED);
        assertThat(finished.counters().sent()).isPositive();
        assertThat(finished.counters().authenticationFailure()).isEqualTo(finished.counters().sent());
        assertThat(finished.counters().transportErrors()).isZero();
        assertThat(loginRequests.get()).isEqualTo(finished.counters().sent());
        assertThat(runManager.observedRequestIds(finished.runId())).isPresent()
                .get().satisfies(ids -> assertThat(ids).hasSize((int) finished.counters().completed()));
    }

    @Test
    void overlappingRunsAreRejected() throws Exception {
        RunSnapshot started = runManager.start(config("repeated-guessing", 5, 3, 30));
        try {
            assertThatThrownBy(() -> runManager.start(config("rapid-requests", 5, 3, 30)))
                    .isInstanceOf(RunManager.RunInProgressException.class);
        } finally {
            runManager.stop(started.runId());
            waitForCompletion(started);
        }
    }

    @Test
    void stopCancelsPendingRequestsAndSettlesTheRun() throws Exception {
        RunSnapshot started = runManager.start(config("rapid-requests", 10, 20, 400));
        Thread.sleep(300);
        runManager.stop(started.runId());
        RunSnapshot finished = waitForCompletion(started);

        assertThat(finished.state()).isEqualTo(RunSnapshot.CANCELLED);
        assertThat(finished.counters().cancelled()).isPositive();
        assertThat(finished.counters().sent() + finished.counters().cancelled())
                .isLessThanOrEqualTo(finished.counters().planned());
    }

    @Test
    void capsAreEnforced() {
        assertThatThrownBy(() -> runManager.start(config("repeated-guessing", 99, 2, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runManager.start(config("repeated-guessing", 2, 999, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runManager.start(config("repeated-guessing", 2, 2, 100000)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runManager.start(
                new RunConfig("repeated-guessing", 2, 2, List.of("asha.raman"), 10, 99)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transportErrorsAreSeparatedFromDefenderRejections() throws Exception {
        dropConnections.set(true);
        RunSnapshot started = runManager.start(config("repeated-guessing", 4, 2, 8));
        RunSnapshot finished = waitForCompletion(started);

        assertThat(finished.state()).isEqualTo(RunSnapshot.COMPLETED);
        assertThat(finished.counters().transportErrors()).isPositive();
        assertThat(finished.counters().authenticationFailure()).isZero();
    }
}
