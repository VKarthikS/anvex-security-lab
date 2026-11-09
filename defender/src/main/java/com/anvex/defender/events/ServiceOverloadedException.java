package com.anvex.defender.events;

/**
 * Raised when the critical event queue is saturated. The request is answered with a bounded
 * overload response while enforcement stays active.
 */
public class ServiceOverloadedException extends RuntimeException {

    public ServiceOverloadedException(String message) {
        super(message);
    }
}
