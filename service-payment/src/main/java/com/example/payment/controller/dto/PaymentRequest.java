package com.example.payment.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "walletId is required")
    private Long walletId;

    @NotNull(message = "merchantId is required")
    private Long merchantId;

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "cardToken is required")
    private String cardToken;

    private String productName;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}
