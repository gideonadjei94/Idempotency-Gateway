package com.gideon.finsafe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gideon.finsafe.PaymentDto;
import com.gideon.finsafe.config.IdempotencyPropertiesConfig;
import com.gideon.finsafe.exceptions.CircuitBreakerOpenException;
import com.gideon.finsafe.exceptions.IdempotencyConflictException;
import com.gideon.finsafe.exceptions.RequestInFlightException;
import com.gideon.finsafe.utils.HashUtils;
import com.gideon.finsafe.utils.RedisKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String LOCK_VALUE = "PROCESSING";
    private static final long   POLL_INTERVAL_MS  = 300L;
    private static final int    MAX_POLL_ATTEMPTS = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final PaymentService   paymentService;
    private final ObjectMapper  objectMapper = buildResponseMapper();
    private final CircuitBreakerService circuitBreaker;
    private final IdempotencyPropertiesConfig idempotencyPropertiesConfig;



    public IdempotencyResult processPayment(String idempotencyKey, PaymentDto.PaymentRequest request) {
        String requestHash = HashUtils.hashOf(request);
        log.debug("Processing payment idempotencyKey='{}' requestHash='{}'", idempotencyKey, requestHash);

        PaymentDto.IdempotencyRecord existingRecord = fetchRecord(idempotencyKey);
        if (existingRecord != null) {
            return resolveExistingRecord(idempotencyKey, requestHash, existingRecord);
        }

        boolean lockAcquired = acquireLock(idempotencyKey);

        if (!lockAcquired) {
            return waitForInFlightRequestAndReplay(idempotencyKey, requestHash);
        }

        existingRecord = fetchRecord(idempotencyKey);
        if (existingRecord != null) {
            releaseLock(idempotencyKey);
            return resolveExistingRecord(idempotencyKey, requestHash, existingRecord);
        }


        return executeAndPersist(idempotencyKey, requestHash, request);
    }

    private IdempotencyResult resolveExistingRecord(
            String idempotencyKey,
            String incomingHash,
            PaymentDto.IdempotencyRecord record
    ) {
        if (!HashUtils.constantTimeEquals(incomingHash, record.getRequestHash())) {
            log.warn("Idempotency conflict: key='{}' incomingHash='{}' storedHash='{}'",
                    idempotencyKey, incomingHash, record.getRequestHash());
            throw new IdempotencyConflictException(idempotencyKey);
        }

        log.info("Cache hit for idempotencyKey='{}' — replaying stored response", idempotencyKey);
        PaymentDto.PaymentResponse cachedResponse = deserializeResponse(record.getResponseBody());
        return new IdempotencyResult(cachedResponse, record.getHttpStatusCode(), true);
    }


    private IdempotencyResult waitForInFlightRequestAndReplay(
            String idempotencyKey,
            String requestHash
    ) {
        log.info("Request in-flight for key='{}' — entering polling loop", idempotencyKey);

        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            log.debug("Polling attempt {}/{} for idempotencyKey='{}'",
                    attempt, MAX_POLL_ATTEMPTS, idempotencyKey);

            try {
                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RequestInFlightException(idempotencyKey);
            }


            PaymentDto.IdempotencyRecord record = fetchRecord(idempotencyKey);
            if (record != null) {
                log.info("In-flight request completed for key='{}' on poll attempt {}",
                        idempotencyKey, attempt);
                return resolveExistingRecord(idempotencyKey, requestHash, record);
            }

            boolean lockStillHeld = Boolean.TRUE.equals(
                    redisTemplate.hasKey(RedisKeyUtils.lockKey(idempotencyKey))
            );
            if (!lockStillHeld) {
                log.warn("Lock expired without data for key='{}' on attempt {} — re-entering flow",
                        idempotencyKey, attempt);
                break;
            }
        }

        throw new RequestInFlightException(idempotencyKey);
    }



    private IdempotencyResult executeAndPersist(
            String idempotencyKey,
            String requestHash,
            PaymentDto.PaymentRequest request
    ) {
        try {
            PaymentDto.PaymentResponse response = paymentService.process(request);
            int statusCode = HttpStatus.CREATED.value();

            persistRecord(idempotencyKey, requestHash, statusCode, response);
            log.info("Payment persisted for idempotencyKey='{}'",
                    idempotencyKey);

            return new IdempotencyResult(response, statusCode, false);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Payment thread interrupted for idempotencyKey='{}'", idempotencyKey);
            throw new RuntimeException("Payment processing was interrupted", ex);

        } finally {
            releaseLock(idempotencyKey);
        }
    }



    private boolean acquireLock(String idempotencyKey) {
        try {
            return circuitBreaker.execute(() -> {
                String lockKey = RedisKeyUtils.lockKey(idempotencyKey);
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, LOCK_VALUE, Duration.ofSeconds(idempotencyPropertiesConfig.getLockTtl()));
                boolean result = Boolean.TRUE.equals(acquired);
                log.debug("Lock acquisition for key='{}': {}", idempotencyKey, result ? "SUCCESS" : "FAILED");
                return result;
            });
        } catch (CircuitBreakerOpenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to acquire lock for key='{}'", idempotencyKey, ex);
            return false;
        }
    }



    private void releaseLock(String idempotencyKey) {
        redisTemplate.delete(RedisKeyUtils.lockKey(idempotencyKey));
        log.debug("Lock released for idempotencyKey='{}'", idempotencyKey);
    }



    private PaymentDto.IdempotencyRecord fetchRecord(String idempotencyKey) {
        try {
            return circuitBreaker.execute(() -> {
                Object raw = redisTemplate.opsForValue().get(RedisKeyUtils.dataKey(idempotencyKey));
                if (raw == null) return null;

                if (raw instanceof PaymentDto.IdempotencyRecord record) return record;
                return objectMapper.convertValue(raw, PaymentDto.IdempotencyRecord.class);
            });
        } catch (CircuitBreakerOpenException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch idempotency record for key='{}'", idempotencyKey, ex);
            throw new RuntimeException("Failed to check idempotency", ex);
        }
    }


    private void persistRecord(String idempotencyKey, String requestHash,
                               int httpStatusCode, PaymentDto.PaymentResponse response) {
        try {
            circuitBreaker.execute(() -> {
                PaymentDto.IdempotencyRecord record = PaymentDto.IdempotencyRecord.builder()
                        .requestHash(requestHash)
                        .httpStatusCode(httpStatusCode)
                        .responseBody(serializeResponse(response))
                        .createdAt(Instant.now())
                        .build();

                redisTemplate.opsForValue().set(
                        RedisKeyUtils.dataKey(idempotencyKey),
                        record,
                        Duration.ofSeconds(idempotencyPropertiesConfig.getKeyTtl())
                );
                return null;
            });
        } catch (Exception ex) {
            log.error("Failed to persist idempotency record for key='{}'", idempotencyKey, ex);
        }
    }



    private String serializeResponse(PaymentDto.PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize PaymentResponse for Redis storage", ex);
        }
    }



    private PaymentDto.PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentDto.PaymentResponse.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize cached PaymentResponse from Redis", ex);
        }
    }



    private ObjectMapper buildResponseMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }



    public record IdempotencyResult(
            PaymentDto.PaymentResponse response,
            int httpStatusCode,
            boolean cacheHit
    ) {}
}