package com.gideon.finsafe.exceptions;

import com.gideon.finsafe.PaymentDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<PaymentDto.ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest request
    ) {
        log.warn("Idempotency conflict for key='{}': {}", ex.getIdempotencyKey(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                ));
    }



    @ExceptionHandler(RequestInFlightException.class)
    public ResponseEntity<PaymentDto.ErrorResponse> handleRequestInFlight(
            RequestInFlightException ex,
            HttpServletRequest request
    ) {
        log.warn("Request in-flight timeout for key='{}': {}", ex.getIdempotencyKey(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(buildError(
                        HttpStatus.GATEWAY_TIMEOUT,
                        ex.getMessage(),
                        request.getRequestURI(),
                        null
                ));
    }



    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentDto.ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.warn("Validation failed for path='{}': {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "Request validation failed",
                        request.getRequestURI(),
                        fieldErrors
                ));
    }



    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<PaymentDto.ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        String message = String.format("Required request header '%s' is missing", ex.getHeaderName());
        log.warn("Missing header '{}' on path='{}'", ex.getHeaderName(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        message,
                        request.getRequestURI(),
                        null
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentDto.ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception on path='{}'", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.",
                        request.getRequestURI(),
                        null
                ));
    }



    private PaymentDto.ErrorResponse buildError(
            HttpStatus status,
            String message,
            String path,
            List<String> fieldErrors
    ) {
        return PaymentDto.ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
    }
}

