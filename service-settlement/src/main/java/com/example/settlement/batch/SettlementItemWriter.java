package com.example.settlement.batch;

import com.example.settlement.domain.paymentsummary.PaymentSummaryRepository;
import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

@RequiredArgsConstructor
@Slf4j
public class SettlementItemWriter implements ItemWriter<Settlement> {

    private final SettlementRepository settlementRepository;
    private final PaymentSummaryRepository paymentSummaryRepository;

    @Override
    public void write(Chunk<? extends Settlement> items) {
        for (Settlement settlement : items) {
            settlementRepository.findByMerchantIdAndSettlementDate(
                settlement.getMerchantId(), settlement.getSettlementDate()
            ).ifPresentOrElse(
                existing -> log.warn("Settlement already exists: merchantId={}, date={}",
                    settlement.getMerchantId(), settlement.getSettlementDate()),
                () -> {
                    settlementRepository.save(settlement);
                    paymentSummaryRepository.markAsSettled(
                        settlement.getMerchantId(), settlement.getSettlementDate());
                    log.info("Settlement saved: id={}, merchantId={}, date={}, netAmount={}",
                        settlement.getId(), settlement.getMerchantId(),
                        settlement.getSettlementDate(), settlement.getNetAmount());
                }
            );
        }
    }
}
