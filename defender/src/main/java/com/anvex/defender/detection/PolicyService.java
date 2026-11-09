package com.anvex.defender.detection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.anvex.defender.config.DefenderProperties;

/**
 * Holds the current policy snapshot. Reads are lock-free (single volatile reference swap), so a
 * decision always observes a coherent set of thresholds, windows and protection mode.
 */
@Service
public class PolicyService {

    private final AtomicReference<PolicySnapshot> current = new AtomicReference<>();
    private final DefenderProperties properties;

    public PolicyService(DefenderProperties properties) {
        this.properties = properties;
        this.current.set(snapshotFrom(properties, initialMode(), Instant.now()));
    }

    public PolicySnapshot current() {
        return current.get();
    }

    public ProtectionMode protectionMode() {
        return current.get().protectionMode();
    }

    public ProtectionMode initialMode() {
        return properties.getInitialProtectionMode() == DefenderProperties.ProtectionMode.ON
                ? ProtectionMode.ON
                : ProtectionMode.OFF;
    }

    public PolicySnapshot withProtectionMode(ProtectionMode mode, Instant now) {
        PolicySnapshot snapshot = current.get();
        PolicySnapshot updated = new PolicySnapshot(
                snapshot.policyVersion(),
                snapshot.repeatedGuessingFailureThreshold(),
                snapshot.repeatedGuessingWindowMillis(),
                snapshot.sprayingFailureThreshold(),
                snapshot.sprayingDistinctAccountThreshold(),
                snapshot.sprayingWindowMillis(),
                snapshot.rapidRequestThreshold(),
                snapshot.rapidRequestWindowMillis(),
                snapshot.tokenBucketRequestsPerSecond(),
                snapshot.tokenBucketBurstCapacity(),
                snapshot.blockDurationMillis(),
                mode,
                now);
        current.set(updated);
        return updated;
    }

    public PolicySnapshot withThresholds(PolicySnapshot updated, Instant now) {
        current.set(updated);
        return updated;
    }

    public static PolicySnapshot snapshotFrom(DefenderProperties properties, ProtectionMode mode, Instant now) {
        DefenderProperties.Detection detection = properties.getDetection();
        DefenderProperties.Protection protection = properties.getProtection();
        return new PolicySnapshot(
                properties.getPolicyVersion(),
                detection.getRepeatedGuessingFailureThreshold(),
                detection.getRepeatedGuessingWindow().toMillis(),
                detection.getSprayingFailureThreshold(),
                detection.getSprayingDistinctAccountThreshold(),
                detection.getSprayingWindow().toMillis(),
                detection.getRapidRequestThreshold(),
                detection.getRapidRequestWindow().toMillis(),
                protection.getTokenBucketRequestsPerSecond(),
                protection.getTokenBucketBurstCapacity(),
                protection.getBlockDuration().toMillis(),
                mode,
                now);
    }

    public static PolicySnapshot from(PolicySnapshot template, int repeatedGuessingFailureThreshold,
                                      Duration repeatedGuessingWindow, int sprayingFailureThreshold,
                                      int sprayingDistinctAccountThreshold, Duration sprayingWindow,
                                      int rapidRequestThreshold, Duration rapidRequestWindow,
                                      int tokenBucketRequestsPerSecond, int tokenBucketBurstCapacity,
                                      Duration blockDuration, String policyVersion, Instant now) {
        return new PolicySnapshot(
                policyVersion,
                repeatedGuessingFailureThreshold,
                repeatedGuessingWindow.toMillis(),
                sprayingFailureThreshold,
                sprayingDistinctAccountThreshold,
                sprayingWindow.toMillis(),
                rapidRequestThreshold,
                rapidRequestWindow.toMillis(),
                tokenBucketRequestsPerSecond,
                tokenBucketBurstCapacity,
                blockDuration.toMillis(),
                template.protectionMode(),
                now);
    }
}
