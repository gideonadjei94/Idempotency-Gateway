package com.gideon.finsafe.service;

import com.gideon.finsafe.PaymentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    /**
     * Configurable delay in milliseconds that simulates the time a real payment
     * gateway call would take. Sourced from {@code idempotency.processing-delay}
     * in {@code application.yml} (default: 2000 ms).
     */
    @Value("${idempotency.processing-delay:2000}")
    private long processingDelayMs;

    public PaymentDto.PaymentResponse process(PaymentDto.PaymentRequest request) throws InterruptedException {
        log.info("Processing payment: amount={} currency={}",
                request.getAmount(), request.getCurrency());

        simulateProcessingDelay();

        String transactionId = "TXN-" + UUID.randomUUID();
        String statusMessage  = String.format("Charged %s %s",
                request.getAmount().toPlainString(), request.getCurrency());

        PaymentDto.PaymentResponse response = PaymentDto.PaymentResponse.builder()
                .status(statusMessage)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionId(transactionId)
                .processedAt(Instant.now())
                .build();

        log.info("Payment processed successfully: transactionId={}", transactionId);
        return response;
    }


    private void simulateProcessingDelay() throws InterruptedException {
        log.debug("Simulating payment gateway delay of {}ms", processingDelayMs);
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
}