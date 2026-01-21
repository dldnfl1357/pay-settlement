package com.example.payment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_idempotency", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_merchant_created", columnList = "merchant_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "card_token", length = 100)
    private String cardToken;

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

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
        // 결제 유효시간: 5분
        this.expiredAt = this.createdAt.plusMinutes(5);
    }

    public void approve(String pgTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING payment can be approved. Current status: " + this.status);
        }
        if (isExpired()) {
            throw new IllegalStateException("Payment has expired");
        }
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED payment can be cancelled. Current status: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING payment can be marked as failed. Current status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
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
