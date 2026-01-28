package com.example.payment.interfaces.rest.dto;

import com.example.payment.application.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
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

    public static PaymentResponse from(PaymentResult result) {
        return PaymentResponse.builder()
            .id(result.getId())
            .walletId(result.getWalletId())
            .merchantId(result.getMerchantId())
            .orderId(result.getOrderId())
            .amount(result.getAmount())
            .status(result.getStatus())
            .pgTransactionId(result.getPgTransactionId())
            .createdAt(result.getCreatedAt())
            .approvedAt(result.getApprovedAt())
            .cancelledAt(result.getCancelledAt())
            .build();
    }
}
