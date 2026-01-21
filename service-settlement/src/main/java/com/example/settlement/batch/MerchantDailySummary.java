package com.example.settlement.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantDailySummary {

    private Long merchantId;
    private LocalDate date;
    private BigDecimal totalSales;
    private BigDecimal totalCancel;
    private int transactionCount;
    private BigDecimal feeRate;
}
