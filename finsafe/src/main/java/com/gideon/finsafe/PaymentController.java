package com.gideon.finsafe;

import com.gideon.finsafe.service.IdempotencyService;
import com.gideon.finsafe.service.IdempotencyService.IdempotencyResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String CACHE_HIT_HEADER = "X-Cache-Hit";
    private final IdempotencyService idempotencyService;


    @PostMapping("/process-payment")
    public ResponseEntity<PaymentDto.PaymentResponse> processPayment(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody @Valid PaymentDto.PaymentRequest request
    ) {
        log.info("Received payment request: idempotencyKey='{}' amount={} currency={}",
                idempotencyKey, request.getAmount(), request.getCurrency());

        IdempotencyResult result = idempotencyService.processPayment(idempotencyKey, request);

        HttpHeaders responseHeaders = buildResponseHeaders(result.cacheHit());
        HttpStatus  responseStatus  = HttpStatus.valueOf(result.httpStatusCode());

        log.info("Responding to idempotencyKey='{}': status={} cacheHit={}",
                idempotencyKey, responseStatus, result.cacheHit());

        return ResponseEntity.status(responseStatus)
                .headers(responseHeaders)
                .body(result.response());
    }


    private HttpHeaders buildResponseHeaders(boolean isCacheHit) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CACHE_HIT_HEADER, String.valueOf(isCacheHit));
        return headers;
    }
}
