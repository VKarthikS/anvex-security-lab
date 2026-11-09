package com.anvex.defender.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "anvex")
public class DefenderProperties {

    /** Absolute or relative directory that holds the file-backed H2 database. */
    @NotBlank
    private String dataDir = "./data";

    /** Version tag of the shipped detection policy; bumped whenever defaults change. */
    @NotBlank
    private String policyVersion = "anvex-policy-1.0.0";

    /** Initial protection mode for a fresh installation. Persisted once the analyst changes it. */
    @NotNull
    private ProtectionMode initialProtectionMode = ProtectionMode.OFF;

    @Valid
    private final Detection detection = new Detection();

    @Valid
    private final Protection protection = new Protection();

    @Valid
    private final Queue queue = new Queue();

    public enum ProtectionMode {
        ON,
        OFF
    }

    public static class Detection {
        @Min(1)
        private int repeatedGuessingFailureThreshold = 8;
        @NotNull
        private Duration repeatedGuessingWindow = Duration.ofSeconds(30);

        @Min(1)
        private int sprayingFailureThreshold = 10;
        @Min(1)
        private int sprayingDistinctAccountThreshold = 8;
        @NotNull
        private Duration sprayingWindow = Duration.ofSeconds(60);

        @Min(1)
        private int rapidRequestThreshold = 30;
        @NotNull
        private Duration rapidRequestWindow = Duration.ofSeconds(5);

        /** Maximum number of tracked sources and (source, account) pairs before stale pruning. */
        @Min(16)
        private int maxTrackedSources = 5000;
        @Min(16)
        private int maxTrackedAccountPairs = 20000;
        @NotNull
        private Duration staleEntryRetention = Duration.ofMinutes(15);

        public int getRepeatedGuessingFailureThreshold() {
            return repeatedGuessingFailureThreshold;
        }

        public void setRepeatedGuessingFailureThreshold(int value) {
            this.repeatedGuessingFailureThreshold = value;
        }

        public Duration getRepeatedGuessingWindow() {
            return repeatedGuessingWindow;
        }

        public void setRepeatedGuessingWindow(Duration value) {
            this.repeatedGuessingWindow = value;
        }

        public int getSprayingFailureThreshold() {
            return sprayingFailureThreshold;
        }

        public void setSprayingFailureThreshold(int value) {
            this.sprayingFailureThreshold = value;
        }

        public int getSprayingDistinctAccountThreshold() {
            return sprayingDistinctAccountThreshold;
        }

        public void setSprayingDistinctAccountThreshold(int value) {
            this.sprayingDistinctAccountThreshold = value;
        }

        public Duration getSprayingWindow() {
            return sprayingWindow;
        }

        public void setSprayingWindow(Duration value) {
            this.sprayingWindow = value;
        }

        public int getRapidRequestThreshold() {
            return rapidRequestThreshold;
        }

        public void setRapidRequestThreshold(int value) {
            this.rapidRequestThreshold = value;
        }

        public Duration getRapidRequestWindow() {
            return rapidRequestWindow;
        }

        public void setRapidRequestWindow(Duration value) {
            this.rapidRequestWindow = value;
        }

        public int getMaxTrackedSources() {
            return maxTrackedSources;
        }

        public void setMaxTrackedSources(int value) {
            this.maxTrackedSources = value;
        }

        public int getMaxTrackedAccountPairs() {
            return maxTrackedAccountPairs;
        }

        public void setMaxTrackedAccountPairs(int value) {
            this.maxTrackedAccountPairs = value;
        }

        public Duration getStaleEntryRetention() {
            return staleEntryRetention;
        }

        public void setStaleEntryRetention(Duration value) {
            this.staleEntryRetention = value;
        }
    }

    public static class Protection {
        @Min(1)
        private int tokenBucketRequestsPerSecond = 5;
        @Min(1)
        private int tokenBucketBurstCapacity = 10;
        @NotNull
        private Duration blockDuration = Duration.ofSeconds(30);

        public int getTokenBucketRequestsPerSecond() {
            return tokenBucketRequestsPerSecond;
        }

        public void setTokenBucketRequestsPerSecond(int value) {
            this.tokenBucketRequestsPerSecond = value;
        }

        public int getTokenBucketBurstCapacity() {
            return tokenBucketBurstCapacity;
        }

        public void setTokenBucketBurstCapacity(int value) {
            this.tokenBucketBurstCapacity = value;
        }

        public Duration getBlockDuration() {
            return blockDuration;
        }

        public void setBlockDuration(Duration value) {
            this.blockDuration = value;
        }
    }

    public static class Queue {
        @Min(10)
        private int capacity = 10000;
        @Min(2)
        private int criticalCapacity = 2000;
        @Min(1)
        private int workerThreads = 2;
        @Min(1)
        private int batchSize = 50;
        @NotNull
        private Duration shutdownDrainTimeout = Duration.ofSeconds(10);

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int value) {
            this.capacity = value;
        }

        public int getCriticalCapacity() {
            return criticalCapacity;
        }

        public void setCriticalCapacity(int value) {
            this.criticalCapacity = value;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int value) {
            this.workerThreads = value;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int value) {
            this.batchSize = value;
        }

        public Duration getShutdownDrainTimeout() {
            return shutdownDrainTimeout;
        }

        public void setShutdownDrainTimeout(Duration value) {
            this.shutdownDrainTimeout = value;
        }
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public ProtectionMode getInitialProtectionMode() {
        return initialProtectionMode;
    }

    public void setInitialProtectionMode(ProtectionMode initialProtectionMode) {
        this.initialProtectionMode = initialProtectionMode;
    }

    public Detection getDetection() {
        return detection;
    }

    public Protection getProtection() {
        return protection;
    }

    public Queue getQueue() {
        return queue;
    }
}
