package com.anvex.common;

import java.time.Instant;

public record Identity(
        String product,
        String version,
        String apiVersion,
        String protocol,
        Instant buildTime
) {
}
