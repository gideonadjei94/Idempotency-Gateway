package com.gideon.finsafe.exceptions;

import lombok.Getter;

@Getter
public class CircuitBreakerOpenException extends RuntimeException {

    private final String circuitName;

    public CircuitBreakerOpenException(String circuitName) {
        super(String.format("Circuit breaker '%s' is OPEN. Service temporarily unavailable.", circuitName));
        this.circuitName = circuitName;
    }

}
