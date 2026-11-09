package com.anvex.simulator.client;

import com.anvex.common.DemoAccount;
import com.anvex.common.Identity;
import com.anvex.common.LoginRequest;
import com.anvex.common.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.anvex.simulator.config.SimulatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Java HTTP client for the defender. Redirects are disabled so a simulation can never be moved to
 * another host, and only the configured ANVEX base URL is ever contacted.
 */
@Component
public class DefenderClient {

    private static final Logger log = LoggerFactory.getLogger(DefenderClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public DefenderClient(HttpClient defenderHttpClient, ObjectMapper objectMapper, SimulatorProperties properties) {
        this.httpClient = defenderHttpClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = properties.getRequestTimeout();
    }

    public TargetHandshake handshake(String baseUrl) {
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/v1/identity"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            double latencyMs = elapsedMs(started);
            if (response.statusCode() != 200) {
                return new TargetHandshake(baseUrl, true, false, null, null, null, null,
                        "Defender answered the identity handshake with HTTP " + response.statusCode() + ".");
            }
            Identity identity = objectMapper.readValue(response.body(), Identity.class);
            boolean compatible = "ANVEX".equals(identity.product())
                    && "anvex/1".equals(identity.protocol())
                    && "v1".equals(identity.apiVersion());
            return new TargetHandshake(baseUrl, true, compatible, identity.product(), identity.version(),
                    identity.apiVersion(), identity.protocol(),
                    compatible
                            ? "ANVEX " + identity.version() + " handshake accepted."
                            : "Target answered but is not a compatible ANVEX defender.");
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Identity handshake failed for {}: {}", baseUrl, ex.getMessage());
            return new TargetHandshake(baseUrl, false, false, null, null, null, null,
                    "Could not reach the defender: " + ex.getClass().getSimpleName() + ".");
        }
    }

    public List<DemoAccount> fetchDemoAccounts(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/v1/demo/accounts"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            return List.of(objectMapper.readValue(response.body(), DemoAccount[].class));
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    public DefenderLoginResult login(String baseUrl, String username, String password, String runId) {
        long started = System.nanoTime();
        try {
            String payload = objectMapper.writeValueAsString(new LoginRequest(username, password, runId));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/v1/auth/login"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            double latencyMs = elapsedMs(started);
            String requestId = response.headers().firstValue("X-ANVEX-Request-Id").orElse(null);
            String sourceIp = response.headers().firstValue("X-ANVEX-Source-Ip").orElse(null);
            if (response.statusCode() == 429) {
                String status = "rate_limited_or_blocked";
                try {
                    LoginResponse body = objectMapper.readValue(response.body(), LoginResponse.class);
                    if (body.status() != null && !body.status().isBlank()) {
                        status = body.status();
                    }
                } catch (IOException ignored) {
                    status = "rate_limited_or_blocked";
                }
                return new DefenderLoginResult(status, 429, requestId, sourceIp, latencyMs, false, null);
            }
            if (response.statusCode() >= 500) {
                return new DefenderLoginResult("service_overloaded", response.statusCode(), requestId, sourceIp, latencyMs, false, null);
            }
            LoginResponse body = objectMapper.readValue(response.body(), LoginResponse.class);
            return new DefenderLoginResult(body.status(), response.statusCode(), requestId, sourceIp, latencyMs, false, null);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DefenderLoginResult.transportError(ex.getClass().getSimpleName() + ": " + ex.getMessage(), elapsedMs(started));
        }
    }

    /** Fetches the authoritative defender counters, optionally for one simulator run. */
    public Map<String, Object> fetchMetrics(String baseUrl, String runId) {
        try {
            String url = trimTrailingSlash(baseUrl) + "/api/v1/metrics/snapshot"
                    + (runId == null || runId.isBlank() ? "" : "?runId=" + java.net.URLEncoder.encode(runId,
                    java.nio.charset.StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Map.of();
            }
            return objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        }
    }

    /** Fetches the public, read-only policy snapshot of the defender. */
    public Map<String, Object> fetchPolicy(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/v1/policy"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Map.of();
            }
            return objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Map.of();
        }
    }

    /**
     * Fetches only the read-only defender dashboard endpoint. There is no generic proxy:
     * callers cannot choose the path, method, or request body.
     */
    public Map<String, Object> fetchDashboardSnapshot(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/v1/dashboard/snapshot"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Map.of();
            }
            Map<String, Object> snapshot = objectMapper.readValue(response.body(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            // A successful HTTP status alone is not sufficient. Ensure this is the expected API shape.
            if (!snapshot.containsKey("counters") || !snapshot.containsKey("protection")
                    || !snapshot.containsKey("recentEvidence") || !snapshot.containsKey("activeBlocks")) {
                return Map.of();
            }
            return snapshot;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Defender dashboard snapshot unavailable: {}", ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static double elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0;
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
