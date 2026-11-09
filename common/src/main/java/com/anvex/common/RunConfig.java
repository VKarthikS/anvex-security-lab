package com.anvex.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RunConfig(
        @NotBlank @Size(max = 40) String scenarioId,
        @Min(1) @Max(20) int ratePerSecond,
        @Min(1) @Max(120) int durationSeconds,
        @Size(max = 32) List<String> targetUsernames,
        @Min(1) @Max(1000) int requestLimit,
        @Min(1) @Max(8) int concurrency
) {
}
