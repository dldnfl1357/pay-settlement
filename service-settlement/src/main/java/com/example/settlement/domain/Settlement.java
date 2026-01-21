package com.example.settlement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_merchant_date", columnList = "merchant_id, settlement_date", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "total_sales", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalSales;  // 총 결제액

    @Column(name = "total_cancel", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCancel;  // 총 취소액

    @Column(name = "total_fee", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalFee;  // 총 수수료

    @Column(name = "net_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal netAmount;  // 순정산액

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;  // 거래 건수

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = SettlementStatus.PENDING;
        }
    }

    public void confirm() {
        if (this.status != SettlementStatus.PENDING) {
            throw new IllegalStateException("Only PENDING settlement can be confirmed");
        }
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markAsPaid() {
        if (this.status != SettlementStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED settlement can be marked as paid");
        }
        this.status = SettlementStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }
}
