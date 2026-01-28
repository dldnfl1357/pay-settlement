package com.example.settlement.domain.settlement;

import com.example.settlement.domain.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class FeeRate {

    @Column(name = "fee_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal value;

    private FeeRate(BigDecimal value) {
        validate(value);
        this.value = value;
    }

    public static FeeRate of(BigDecimal value) {
        return new FeeRate(value);
    }

    public static FeeRate of(String value) {
        return new FeeRate(new BigDecimal(value));
    }

    public static FeeRate defaultRate() {
        return new FeeRate(new BigDecimal("0.0250"));
    }

    private void validate(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Fee rate must not be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Fee rate must be between 0 and 1 (exclusive)");
        }
    }

    public Money calculateFee(Money amount) {
        return amount.multiplyAndFloor(this.value);
    }

    @Override
    public String toString() {
        return value.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
    }
}
