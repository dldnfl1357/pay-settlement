package com.example.payment.interfaces.rest;

import com.example.payment.application.PaymentApplicationService;
import com.example.payment.application.dto.CreatePaymentCommand;
import com.example.payment.application.dto.PaymentResult;
import com.example.payment.interfaces.rest.dto.PaymentRequest;
import com.example.payment.interfaces.rest.dto.PaymentResponse;
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

    private final PaymentApplicationService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody PaymentRequest request) {

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }

        log.info("Payment request: orderId={}, amount={}, idempotencyKey={}",
            request.getOrderId(), request.getAmount(), request.getIdempotencyKey());

        CreatePaymentCommand command = CreatePaymentCommand.builder()
            .walletId(request.getWalletId())
            .merchantId(request.getMerchantId())
            .orderId(request.getOrderId())
            .amount(request.getAmount())
            .cardToken(request.getCardToken())
            .productName(request.getProductName())
            .idempotencyKey(request.getIdempotencyKey())
            .build();

        PaymentResult result = paymentService.createPayment(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(result));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentResponse> approvePayment(@PathVariable Long id) {
        log.info("Payment approval request: id={}", id);
        PaymentResult result = paymentService.approvePayment(id);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable Long id) {
        log.info("Payment cancellation request: id={}", id);
        PaymentResult result = paymentService.cancelPayment(id);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        PaymentResult result = paymentService.getPayment(id);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }
}
