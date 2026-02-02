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
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException e) {
        log.error("Optimistic locking conflict: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .header("Retry-After", "1")
            .body(buildErrorResponse("CONFLICT", "일시적인 충돌이 발생했습니다."));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException e) {
        log.error("Entity not found: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentNotFoundException e) {
        log.error("Payment not found: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException e) {
        log.error("Insufficient balance: {}", e.getMessage());
        paymentMetrics.incrementPaymentFailed();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", e.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePayment(DuplicatePaymentException e) {
        log.error("Duplicate payment: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
    }

    @ExceptionHandler(PaymentExpiredException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentExpired(PaymentExpiredException e) {
        log.error("Payment expired: {}", e.getMessage());
        paymentMetrics.incrementPaymentFailed();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "PAYMENT_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidPaymentStateException e) {
        log.error("Invalid payment state: {}", e.getMessage());
        paymentMetrics.incrementPaymentFailed();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(PgApprovalException.class)
    public ResponseEntity<Map<String, Object>> handlePgApprovalError(PgApprovalException e) {
        log.error("PG approval error: {}", e.getMessage());
        paymentMetrics.incrementPaymentFailed();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> handleLockAcquisition(LockAcquisitionException e) {
        log.error("Lock acquisition failed: {}", e.getMessage());
        paymentMetrics.incrementPaymentFailed();
        paymentMetrics.incrementLockAcquisitionFailure();
        return buildErrorResponse(HttpStatus.CONFLICT, "LOCK_ACQUISITION_FAILED", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.error("Illegal state: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + ", " + b)
            .orElse("Validation failed");
        log.error("Validation error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e) {
        log.error("Client aborted: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(buildErrorBody(code, message));
    }

    private Map<String, Object> buildErrorResponse(String code, String message) {
        return buildErrorBody(code, message);
    }

    private Map<String, Object> buildErrorBody(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", true);
        body.put("timestamp", LocalDateTime.now());
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
