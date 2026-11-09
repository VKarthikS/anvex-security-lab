package com.anvex.simulator.config;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The explicitly configured defender target. A browser can never discover or change the other
 * laptop automatically: the operator must point the simulator at a concrete base URL.
 */
@Component
public class TargetSettings {

    private final AtomicReference<String> baseUrl;

    public TargetSettings(SimulatorProperties properties) {
        this.baseUrl = new AtomicReference<>(normalise(properties.getTargetBaseUrl()));
    }

    public String baseUrl() {
        return baseUrl.get();
    }

    public void update(String candidate) {
        baseUrl.set(normalise(validate(candidate)));
    }

    public static String validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        String trimmed = candidate.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("baseUrl must be a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("baseUrl must contain a host");
        }
        return trimmed;
    }

    private static String normalise(String candidate) {
        String trimmed = candidate.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
