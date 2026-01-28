package com.example.payment.application.dto;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class PaymentResult {

    private final Long id;
    private final Long walletId;
    private final Long merchantId;
    private final String orderId;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final String pgTransactionId;
    private final LocalDateTime createdAt;
    private final LocalDateTime approvedAt;
    private final LocalDateTime cancelledAt;

    public static PaymentResult from(Payment payment) {
        return PaymentResult.builder()
            .id(payment.getId())
            .walletId(payment.getWalletId())
            .merchantId(payment.getMerchantId())
            .orderId(payment.getOrderId().getValue())
            .amount(payment.getAmount().getAmount())
            .status(payment.getStatus())
            .pgTransactionId(payment.getPgTransactionId())
            .createdAt(payment.getCreatedAt())
            .approvedAt(payment.getApprovedAt())
            .cancelledAt(payment.getCancelledAt())
            .build();
    }
}
