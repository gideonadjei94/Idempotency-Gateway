package com.gideon.finsafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PaymentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentRequest {

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than 0")
        @Digits(integer = 7, fraction = 2, message = "Amount must have at most 2 decimal places")
        private BigDecimal amount;


        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must be a valid 3-letter ISO-4217 code (e.g. GHS, USD)"
        )
        private String currency;
    }


    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentResponse {
        private String status;
        private BigDecimal amount;
        private String currency;
        private String transactionId;
        private Instant processedAt;
    }



    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private String path;
        private Instant timestamp;
        private List<String> fieldErrors;
    }



    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdempotencyRecord {
        private String requestHash;
        private int httpStatusCode;
        private String responseBody;
        private Instant createdAt;
    }

}
