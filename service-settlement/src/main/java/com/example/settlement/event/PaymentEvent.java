package com.example.settlement.event;

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
}
