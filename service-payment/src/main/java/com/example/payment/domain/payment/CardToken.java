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
public class CardToken {

    @Column(name = "card_token", length = 100)
    private String value;

    private CardToken(String value) {
        validate(value);
        this.value = value;
    }

    public static CardToken of(String value) {
        return new CardToken(value);
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Card token must not be blank");
        }
        if (!value.startsWith("tok_")) {
            throw new IllegalArgumentException("Card token must start with 'tok_'");
        }
    }

    public String masked() {
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    public String lastFour() {
        if (value.length() < 4) {
            return "0000";
        }
        return value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
