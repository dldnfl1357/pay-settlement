package com.example.payment.domain.merchant;

import com.example.payment.domain.shared.BaseAggregateRoot;
import com.example.payment.domain.shared.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant extends BaseAggregateRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Embedded
    private FeeRate feeRate;

    @Column(name = "api_key", length = 64)
    private String apiKey;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Merchant create(String name, String businessNumber, FeeRate feeRate,
                                  String apiKey) {
        Merchant merchant = new Merchant();
        merchant.name = name;
        merchant.businessNumber = businessNumber;
        merchant.feeRate = feeRate != null ? feeRate : FeeRate.defaultRate();
        merchant.apiKey = apiKey;
        merchant.active = true;
        merchant.createdAt = LocalDateTime.now();
        return merchant;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.feeRate == null) {
            this.feeRate = FeeRate.defaultRate();
        }
    }

    public Money calculateFee(Money amount) {
        return this.feeRate.calculateFee(amount);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(this.active);
    }
}
