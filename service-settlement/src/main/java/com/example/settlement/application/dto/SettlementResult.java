package com.example.settlement.application.dto;

import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class SettlementResult {

    private final Long id;
    private final Long merchantId;
    private final LocalDate settlementDate;
    private final BigDecimal totalSales;
    private final BigDecimal totalCancel;
    private final BigDecimal totalFee;
    private final BigDecimal netAmount;
    private final Integer transactionCount;
    private final SettlementStatus status;
    private final LocalDateTime createdAt;

    public static SettlementResult from(Settlement settlement) {
        return SettlementResult.builder()
            .id(settlement.getId())
            .merchantId(settlement.getMerchantId())
            .settlementDate(settlement.getSettlementDate())
            .totalSales(settlement.getTotalSales().getAmount())
            .totalCancel(settlement.getTotalCancel().getAmount())
            .totalFee(settlement.getTotalFee().getAmount())
            .netAmount(settlement.getNetAmount().getAmount())
            .transactionCount(settlement.getTransactionCount())
            .status(settlement.getStatus())
            .createdAt(settlement.getCreatedAt())
            .build();
    }
}
