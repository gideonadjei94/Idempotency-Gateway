package com.gideon.finsafe.exceptions;

public class IdempotencyConflictException extends RuntimeException{

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already used for a different request body.");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
