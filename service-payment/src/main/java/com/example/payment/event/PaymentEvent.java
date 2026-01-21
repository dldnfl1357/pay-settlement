package com.example.payment.event;

import com.example.payment.domain.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private PaymentPayload payload;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentPayload {
        private Long paymentId;
        private Long merchantId;
        private Long walletId;
        private String orderId;
        private BigDecimal amount;
        private String status;
        private String pgTransactionId;
    }

    public static PaymentEvent fromPayment(Payment payment, EventType type) {
        return PaymentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(type.name())
            .timestamp(LocalDateTime.now())
            .payload(PaymentPayload.builder()
                .paymentId(payment.getId())
                .merchantId(payment.getMerchantId())
                .walletId(payment.getWalletId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .pgTransactionId(payment.getPgTransactionId())
                .build())
            .build();
    }

    public enum EventType {
        PAYMENT_REQUESTED,
        PAYMENT_APPROVED,
        PAYMENT_CANCELLED,
        PAYMENT_FAILED
    }
}
