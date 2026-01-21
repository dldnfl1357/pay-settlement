package com.example.payment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(name = "fee_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal feeRate;  // 예: 0.0250 = 2.5%

    @Column(name = "api_key", length = 64)
    private String apiKey;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.active == null) {
            this.active = true;
        }
        if (this.feeRate == null) {
            this.feeRate = new BigDecimal("0.0250");  // 기본 2.5%
        }
    }

    public BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(this.feeRate).setScale(0, java.math.RoundingMode.DOWN);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(this.active);
    }
}
