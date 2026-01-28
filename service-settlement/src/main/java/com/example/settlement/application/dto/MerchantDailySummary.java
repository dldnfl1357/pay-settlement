package com.example.settlement.application.dto;

import com.example.settlement.domain.shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
public class MerchantDailySummary {

    private final Long merchantId;
    private final LocalDate date;
    private final Money totalSales;
    private final Money totalCancel;
    private final int transactionCount;
    private final BigDecimal feeRate;
}
