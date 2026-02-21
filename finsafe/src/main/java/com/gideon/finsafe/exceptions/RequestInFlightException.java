package com.gideon.finsafe.exceptions;

import lombok.Getter;

@Getter
public class RequestInFlightException extends RuntimeException{

    private final String idempotencyKey;

    public RequestInFlightException(String idempotencyKey) {
        super(String.format(
                "A request with idempotency key '%s' is currently being processed. " +
                        "Please retry after a short delay.", idempotencyKey
        ));
        this.idempotencyKey = idempotencyKey;
    }


}
