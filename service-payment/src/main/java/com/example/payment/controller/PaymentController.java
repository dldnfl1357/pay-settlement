package com.example.payment.controller;

import com.example.payment.controller.dto.PaymentRequest;
import com.example.payment.controller.dto.PaymentResponse;
import com.example.payment.service.PaymentSagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentSagaService paymentSagaService;

    /**
     * 결제 요청 생성
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody PaymentRequest request) {

        // 헤더에서 멱등성 키를 받으면 요청 본문의 키를 덮어씀
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }

        log.info("Payment request: orderId={}, amount={}, idempotencyKey={}",
            request.getOrderId(), request.getAmount(), request.getIdempotencyKey());

        PaymentResponse response = paymentSagaService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 결제 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentResponse> approvePayment(@PathVariable Long id) {
        log.info("Payment approval request: id={}", id);
        PaymentResponse response = paymentSagaService.approvePayment(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 취소
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable Long id) {
        log.info("Payment cancellation request: id={}", id);
        PaymentResponse response = paymentSagaService.cancelPayment(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        PaymentResponse response = paymentSagaService.getPayment(id);
        return ResponseEntity.ok(response);
    }
}
