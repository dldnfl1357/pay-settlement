package com.example.payment.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class OrderId {

    @Column(name = "order_id", nullable = false, length = 100)
    private String value;

    private OrderId(String value) {
        validate(value);
        this.value = value;
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Order ID must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
