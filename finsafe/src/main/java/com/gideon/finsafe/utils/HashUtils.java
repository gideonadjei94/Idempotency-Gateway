package com.gideon.finsafe.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;


@Slf4j
@UtilityClass
public class HashUtils {


    private static final ObjectMapper CANONICAL_MAPPER = buildCanonicalMapper();


    public static String hashOf(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null for hashing");
        }

        try {
            String canonicalJson = CANONICAL_MAPPER.writeValueAsString(payload);
            log.debug("Canonical JSON for hashing: {}", canonicalJson);

            byte[] jsonBytes = canonicalJson.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(jsonBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception ex) {
            log.error("Failed to compute SHA-256 hash for payload type={}", payload.getClass().getSimpleName(), ex);
            throw new HashComputationException("Failed to compute request hash — cannot guarantee idempotency", ex);
        }
    }


    public static boolean constantTimeEquals(String hashA, String hashB) {
        if (hashA == null || hashB == null) return false;

        byte[] a = hashA.getBytes(StandardCharsets.UTF_8);
        byte[] b = hashB.getBytes(StandardCharsets.UTF_8);

        if (a.length != b.length) return false;

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= (a[i] ^ b[i]);
        }
        return result == 0;
    }


    private static ObjectMapper buildCanonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(
                com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,
                true
        );
        return mapper;
    }

    public static class HashComputationException extends RuntimeException {
        public HashComputationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}