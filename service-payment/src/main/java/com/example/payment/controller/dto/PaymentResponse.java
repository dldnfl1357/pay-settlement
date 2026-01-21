package com.example.payment.controller.dto;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long walletId;
    private Long merchantId;
    private String orderId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String pgTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime cancelledAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .walletId(payment.getWalletId())
            .merchantId(payment.getMerchantId())
            .orderId(payment.getOrderId())
            .amount(payment.getAmount())
            .status(payment.getStatus())
            .pgTransactionId(payment.getPgTransactionId())
            .createdAt(payment.getCreatedAt())
            .approvedAt(payment.getApprovedAt())
            .cancelledAt(payment.getCancelledAt())
            .build();
    }
}
