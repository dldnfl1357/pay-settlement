package com.example.payment.interfaces.rest;

import com.example.payment.application.exception.PaymentNotFoundException;
import com.example.payment.domain.payment.exception.DuplicatePaymentException;
import com.example.payment.domain.payment.exception.InvalidPaymentStateException;
import com.example.payment.domain.payment.exception.PaymentExpiredException;
import com.example.payment.domain.wallet.exception.InsufficientBalanceException;
import com.example.payment.infrastructure.metrics.PaymentMetrics;
import com.example.payment.infrastructure.pg.PgApprovalException;
import com.example.payment.infrastructure.redis.LockAcquisitionException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final PaymentMetrics paymentMetrics;

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentNotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException e) {
        paymentMetrics.incrementPaymentFailed();
        return buildResponse(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", e.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePayment(DuplicatePaymentException e) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
    }

    @ExceptionHandler(PaymentExpiredException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentExpired(PaymentExpiredException e) {
        paymentMetrics.incrementPaymentFailed();
        return buildResponse(HttpStatus.BAD_REQUEST, "PAYMENT_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidPaymentStateException e) {
        paymentMetrics.incrementPaymentFailed();
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(PgApprovalException.class)
    public ResponseEntity<Map<String, Object>> handlePgApprovalError(PgApprovalException e) {
        paymentMetrics.incrementPaymentFailed();
        return buildResponse(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> handleLockAcquisition(LockAcquisitionException e) {
        paymentMetrics.incrementPaymentFailed();
        paymentMetrics.incrementLockAcquisitionFailure();
        return buildResponse(HttpStatus.CONFLICT, "LOCK_ACQUISITION_FAILED", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + ", " + b)
            .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
