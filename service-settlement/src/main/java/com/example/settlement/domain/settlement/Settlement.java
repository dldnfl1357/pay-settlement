package com.example.settlement.domain.settlement;

import com.example.settlement.domain.settlement.exception.InvalidSettlementStateException;
import com.example.settlement.domain.shared.BaseAggregateRoot;
import com.example.settlement.domain.shared.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_merchant_date", columnList = "merchant_id, settlement_date", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_sales", precision = 15, scale = 2, nullable = false))
    private Money totalSales;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_cancel", precision = 15, scale = 2, nullable = false))
    private Money totalCancel;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_fee", precision = 15, scale = 2, nullable = false))
    private Money totalFee;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "net_amount", precision = 15, scale = 2, nullable = false))
    private Money netAmount;

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static Settlement createFromSummary(Long merchantId, LocalDate date,
                                               Money totalSales, Money totalCancel,
                                               FeeRate feeRate, int txCount) {
        Money fee = feeRate.calculateFee(totalSales);
        Money netAmount = totalSales.subtract(totalCancel).subtract(fee);

        Settlement settlement = new Settlement();
        settlement.merchantId = merchantId;
        settlement.settlementDate = date;
        settlement.totalSales = totalSales;
        settlement.totalCancel = totalCancel;
        settlement.totalFee = fee;
        settlement.netAmount = netAmount;
        settlement.transactionCount = txCount;
        settlement.status = SettlementStatus.PENDING;
        settlement.createdAt = LocalDateTime.now();
        return settlement;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = SettlementStatus.PENDING;
        }
    }

    public void confirm() {
        if (this.status != SettlementStatus.PENDING) {
            throw new InvalidSettlementStateException(
                "Only PENDING settlement can be confirmed. Current status: " + this.status);
        }
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markAsPaid() {
        if (this.status != SettlementStatus.CONFIRMED) {
            throw new InvalidSettlementStateException(
                "Only CONFIRMED settlement can be marked as paid. Current status: " + this.status);
        }
        this.status = SettlementStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }
}
