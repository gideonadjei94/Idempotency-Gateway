package com.gideon.finsafe.exceptions;

public class RateLimitExceededException extends RuntimeException {

    private final String clientId;
    private final int retryAfterSeconds;

    public RateLimitExceededException(String clientId, int retryAfterSeconds) {
        super(String.format("Rate limit exceeded for client '%s'. Retry after %d seconds.",
                clientId, retryAfterSeconds));
        this.clientId = clientId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getClientId() {
        return clientId;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
