package com.example.settlement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kafka에서 수신한 결제 이벤트를 저장하는 임시 테이블
 * 정산 배치에서 집계에 사용
 */
@Entity
@Table(name = "payment_summaries", indexes = {
    @Index(name = "idx_merchant_date_status", columnList = "merchant_id, payment_date, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
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

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 20, nullable = false)
    private String status;  // APPROVED, CANCELLED

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "settled", nullable = false)
    private Boolean settled;  // 정산 완료 여부

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.settled == null) {
            this.settled = false;
        }
    }

    public void markAsSettled() {
        this.settled = true;
    }
}
