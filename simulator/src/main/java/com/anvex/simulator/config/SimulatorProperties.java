package com.anvex.simulator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "anvex.simulator")
public class SimulatorProperties {

    /** Explicitly configured ANVEX defender base URL. */
    @NotBlank
    private String targetBaseUrl = "http://127.0.0.1:8080";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(3);

    @Min(1)
    private int defaultConcurrency = 4;

    @Min(1)
    @Max(8)
    private int concurrencyCap = 8;

    @Min(1)
    @Max(20)
    private int rateCap = 20;

    @Min(1)
    @Max(120)
    private int durationCap = 120;

    @Min(1)
    @Max(1000)
    private int requestCap = 1000;

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getDefaultConcurrency() {
        return defaultConcurrency;
    }

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public int getConcurrencyCap() {
        return concurrencyCap;
    }

    public void setConcurrencyCap(int concurrencyCap) {
        this.concurrencyCap = concurrencyCap;
    }

    public int getRateCap() {
        return rateCap;
    }

    public void setRateCap(int rateCap) {
        this.rateCap = rateCap;
    }

    public int getDurationCap() {
        return durationCap;
    }

    public void setDurationCap(int durationCap) {
        this.durationCap = durationCap;
    }

    public int getRequestCap() {
        return requestCap;
    }

    public void setRequestCap(int requestCap) {
        this.requestCap = requestCap;
    }
}
