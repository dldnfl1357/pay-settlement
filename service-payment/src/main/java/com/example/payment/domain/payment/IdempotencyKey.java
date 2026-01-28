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
public class IdempotencyKey {

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String value;

    private IdempotencyKey(String value) {
        validate(value);
        this.value = value;
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("Idempotency key must not exceed 64 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
