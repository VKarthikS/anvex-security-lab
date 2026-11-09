package com.anvex.defender.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helpers for deriving the client address from the actual remote socket. Forwarded headers are
 * deliberately ignored so a simulator cannot spoof its identity.
 */
public final class RequestSupport {

    private RequestSupport() {
    }

    public static String remoteAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    public static boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        String address = remoteAddress.trim().toLowerCase();
        if (address.equals("::1") || address.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (address.startsWith("::ffff:")) {
            address = address.substring("::ffff:".length());
        }
        return address.startsWith("127.");
    }
}
