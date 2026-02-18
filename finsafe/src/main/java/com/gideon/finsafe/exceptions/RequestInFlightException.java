package com.gideon.finsafe.exceptions;

public class RequestInFlightException extends RuntimeException{

    private final String idempotencyKey;

    public RequestInFlightException(String idempotencyKey) {
        super(String.format(
                "A request with idempotency key '%s' is currently being processed. " +
                        "Please retry after a short delay.", idempotencyKey
        ));
        this.idempotencyKey = idempotencyKey;
    }


    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
