package com.gideon.finsafe.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedisKeyUtils {

    private static final String NAMESPACE  = "idempotency";
    private static final String DATA_INFIX = "data";
    private static final String LOCK_INFIX = "lock";
    private static final String SEPARATOR  = ":";

    public static String dataKey(String idempotencyKey) {
        validateKey(idempotencyKey);
        return NAMESPACE + SEPARATOR + DATA_INFIX + SEPARATOR + idempotencyKey;
    }

    public static String lockKey(String idempotencyKey) {
        validateKey(idempotencyKey);
        return NAMESPACE + SEPARATOR + LOCK_INFIX + SEPARATOR + idempotencyKey;
    }


    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be null or blank");
        }
    }
}
