package com.example.payment.domain.payment.event;

import com.example.payment.domain.shared.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class PaymentFailedEvent implements DomainEvent {

    private final String eventId;
    private final LocalDateTime occurredAt;
    private final Long paymentId;
    private final Long walletId;
    private final Long merchantId;
    private final String orderId;
    private final BigDecimal amount;
    private final String reason;

    public PaymentFailedEvent(Long paymentId, Long walletId, Long merchantId,
                              String orderId, BigDecimal amount, String reason) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.paymentId = paymentId;
        this.walletId = walletId;
        this.merchantId = merchantId;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
    }
}
