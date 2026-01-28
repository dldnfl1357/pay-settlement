package com.example.settlement.batch;

import com.example.settlement.application.dto.MerchantDailySummary;
import com.example.settlement.domain.settlement.FeeRate;
import com.example.settlement.domain.settlement.Settlement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
public class SettlementItemProcessor implements ItemProcessor<MerchantDailySummary, Settlement> {

    @Override
    public Settlement process(MerchantDailySummary summary) {
        Settlement settlement = Settlement.createFromSummary(
            summary.getMerchantId(),
            summary.getDate(),
            summary.getTotalSales(),
            summary.getTotalCancel(),
            FeeRate.of(summary.getFeeRate()),
            summary.getTransactionCount()
        );

        log.info("Processing settlement: merchantId={}, sales={}, cancel={}, fee={}, net={}",
            summary.getMerchantId(), settlement.getTotalSales(),
            settlement.getTotalCancel(), settlement.getTotalFee(), settlement.getNetAmount());

        return settlement;
    }
}
