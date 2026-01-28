package com.example.settlement.domain.paymentsummary;

import com.example.settlement.domain.shared.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_summaries", indexes = {
    @Index(name = "idx_merchant_date_status", columnList = "merchant_id, payment_date, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 15, scale = 2, nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentSummaryStatus status;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "settled", nullable = false)
    private Boolean settled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PaymentSummary create(Long paymentId, Long merchantId, Long walletId,
                                        String orderId, Money amount,
                                        PaymentSummaryStatus status, LocalDate paymentDate) {
        PaymentSummary summary = new PaymentSummary();
        summary.paymentId = paymentId;
        summary.merchantId = merchantId;
        summary.walletId = walletId;
        summary.orderId = orderId;
        summary.amount = amount;
        summary.status = status;
        summary.paymentDate = paymentDate;
        summary.settled = false;
        summary.createdAt = LocalDateTime.now();
        return summary;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.settled == null) {
            this.settled = false;
        }
    }

    public void markAsSettled() {
        this.settled = true;
    }

    public boolean isApproved() {
        return this.status == PaymentSummaryStatus.APPROVED;
    }

    public boolean isCancelled() {
        return this.status == PaymentSummaryStatus.CANCELLED;
    }
}
