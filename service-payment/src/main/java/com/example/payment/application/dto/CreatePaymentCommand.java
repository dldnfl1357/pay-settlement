package com.example.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class CreatePaymentCommand {

    private final Long walletId;
    private final Long merchantId;
    private final String orderId;
    private final BigDecimal amount;
    private final String cardToken;
    private final String productName;
    private final String idempotencyKey;
}
