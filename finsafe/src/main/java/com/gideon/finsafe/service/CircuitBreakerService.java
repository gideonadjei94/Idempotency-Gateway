package com.gideon.finsafe.service;


import com.gideon.finsafe.config.CircuitBreakerPropertiesConfig;
import com.gideon.finsafe.exceptions.CircuitBreakerOpenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@Component
public class CircuitBreakerService {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final CircuitBreakerPropertiesConfig properties;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private volatile Instant openedAt;

    public CircuitBreakerService(CircuitBreakerPropertiesConfig properties) {
        this.properties = properties;
    }


    public <T> T execute(CircuitBreakerOperation<T> operation) throws Exception {
        if (!properties.isEnabled()) {
            return operation.execute();
        }

        State currentState = state.get();

        if (currentState == State.OPEN && shouldAttemptReset()) {
            log.info("Circuit breaker transitioning from OPEN to HALF_OPEN");
            state.set(State.HALF_OPEN);
            halfOpenCalls.set(0);
            currentState = State.HALF_OPEN;
        }

        if (currentState == State.OPEN) {
            log.warn("Circuit breaker is OPEN, rejecting request");
            throw new CircuitBreakerOpenException("redis");
        }


        if (currentState == State.HALF_OPEN) {
            int calls = halfOpenCalls.incrementAndGet();
            if (calls > properties.getPermittedNumberOfCallsInHalfOpenState()) {
                log.warn("Half-open call limit exceeded, rejecting request");
                throw new CircuitBreakerOpenException("redis");
            }
        }

        try {
            T result = operation.execute();
            onSuccess();
            return result;

        } catch (Exception ex) {
            onFailure();
            throw ex;
        }
    }



    private void onSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            log.debug("Circuit breaker HALF_OPEN: {} successes", successes);

            if (successes >= properties.getPermittedNumberOfCallsInHalfOpenState()) {
                log.info("Circuit breaker transitioning from HALF_OPEN to CLOSED");
                reset();
            }
        } else if (currentState == State.CLOSED) {
            totalCalls.incrementAndGet();
        }
    }


    private void onFailure() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            log.warn("Circuit breaker HALF_OPEN test call failed, reopening circuit");
            open();
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            int total = totalCalls.incrementAndGet();

            if (total >= properties.getMinimumNumberOfCalls()) {
                double failureRate = (double) failures / total * 100;
                log.debug("Circuit breaker failure rate: {:.2f}% ({}/{})", failureRate, failures, total);

                if (failureRate >= properties.getFailureRateThreshold()) {
                    log.error("Circuit breaker failure rate {:.2f}% exceeds threshold {}%, opening circuit",
                            failureRate, properties.getFailureRateThreshold());
                    open();
                }
            }
        }
    }


    private void open() {
        state.set(State.OPEN);
        openedAt = Instant.now();
        log.warn("Circuit breaker OPENED at {}", openedAt);
    }


    private void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        totalCalls.set(0);
        halfOpenCalls.set(0);
        openedAt = null;
        log.info("Circuit breaker CLOSED (reset)");
    }


    private boolean shouldAttemptReset() {
        if (openedAt == null) {
            return false;
        }

        long elapsedSeconds = Instant.now().getEpochSecond() - openedAt.getEpochSecond();
        return elapsedSeconds >= properties.getWaitDurationInOpenStateSeconds();
    }


    public String getState() {
        return state.get().name();
    }


    @FunctionalInterface
    public interface CircuitBreakerOperation<T> {
        T execute() throws Exception;
    }
}