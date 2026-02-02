package com.example.payment.domain.payment;

import com.example.payment.domain.payment.event.PaymentApprovedEvent;
import com.example.payment.domain.payment.event.PaymentCancelledEvent;
import com.example.payment.domain.payment.event.PaymentCreatedEvent;
import com.example.payment.domain.payment.event.PaymentFailedEvent;
import com.example.payment.domain.payment.exception.InvalidPaymentStateException;
import com.example.payment.domain.payment.exception.PaymentExpiredException;
import com.example.payment.domain.shared.BaseAggregateRoot;
import com.example.payment.domain.shared.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_merchant_created", columnList = "merchant_id, created_at"),
    @Index(name = "idx_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Embedded
    private OrderId orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 15, scale = 2, nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Embedded
    private IdempotencyKey idempotencyKey;

    @Embedded
    private CardToken cardToken;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Column(length = 200)
    private String productName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    public static Payment create(Long walletId, Long merchantId, OrderId orderId,
                                 Money amount, CardToken cardToken, IdempotencyKey idempotencyKey,
                                 String productName) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Payment payment = new Payment();
        payment.walletId = walletId;
        payment.merchantId = merchantId;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.cardToken = cardToken;
        payment.idempotencyKey = idempotencyKey;
        payment.productName = productName;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        payment.expiredAt = payment.createdAt.plusMinutes(5);

        payment.registerEvent(new PaymentCreatedEvent(
            payment.id, walletId, merchantId,
            orderId.getValue(), amount.getAmount()
        ));

        return payment;
    }

    public void approve(String pgTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                "Only PENDING payment can be approved. Current status: " + this.status);
        }
        if (isExpired()) {
            throw new PaymentExpiredException("Payment has expired");
        }
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = LocalDateTime.now();

        registerEvent(new PaymentApprovedEvent(
            this.id, this.walletId, this.merchantId,
            this.orderId.getValue(), this.amount.getAmount(), pgTransactionId
        ));
    }

    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                "Only APPROVED payment can be cancelled. Current status: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();

        registerEvent(new PaymentCancelledEvent(
            this.id, this.walletId, this.merchantId,
            this.orderId.getValue(), this.amount.getAmount()
        ));
    }

    public void fail(String reason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                "Only PENDING payment can be marked as failed. Current status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;

        registerEvent(new PaymentFailedEvent(
            this.id, this.walletId, this.merchantId,
            this.orderId.getValue(), this.amount.getAmount(), reason
        ));
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public boolean isApproved() {
        return this.status == PaymentStatus.APPROVED;
    }

    public boolean isCancelled() {
        return this.status == PaymentStatus.CANCELLED;
    }
}
