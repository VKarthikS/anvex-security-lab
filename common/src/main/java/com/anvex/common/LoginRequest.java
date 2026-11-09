package com.anvex.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 64, message = "username must be at most 64 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 256, message = "password must be at most 256 characters")
        String password,

        @Size(max = 64, message = "runId must be at most 64 characters")
        String runId
) {
}
