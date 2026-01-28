package com.example.settlement.batch;

import com.example.settlement.application.dto.MerchantDailySummary;
import com.example.settlement.domain.paymentsummary.PaymentSummary;
import com.example.settlement.domain.paymentsummary.PaymentSummaryRepository;
import com.example.settlement.domain.paymentsummary.PaymentSummaryStatus;
import com.example.settlement.domain.shared.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class SettlementItemReader implements ItemReader<MerchantDailySummary> {

    private final PaymentSummaryRepository paymentSummaryRepository;

    @Value("${settlement.fee-rate:0.025}")
    private BigDecimal defaultFeeRate;

    private Iterator<MerchantDailySummary> iterator;

    @Override
    public MerchantDailySummary read() {
        if (iterator == null) {
            initialize();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private void initialize() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        List<PaymentSummary> summaries = paymentSummaryRepository.findUnsettledByDate(targetDate);

        Map<Long, List<PaymentSummary>> byMerchant = summaries.stream()
            .collect(Collectors.groupingBy(PaymentSummary::getMerchantId));

        List<MerchantDailySummary> merchantSummaries = byMerchant.entrySet().stream()
            .map(entry -> {
                Long merchantId = entry.getKey();
                List<PaymentSummary> payments = entry.getValue();

                Money totalSales = payments.stream()
                    .filter(PaymentSummary::isApproved)
                    .map(PaymentSummary::getAmount)
                    .reduce(Money.zero(), Money::add);

                Money totalCancel = payments.stream()
                    .filter(PaymentSummary::isCancelled)
                    .map(PaymentSummary::getAmount)
                    .reduce(Money.zero(), Money::add);

                int txCount = (int) payments.stream()
                    .filter(PaymentSummary::isApproved)
                    .count();

                return MerchantDailySummary.builder()
                    .merchantId(merchantId)
                    .date(targetDate)
                    .totalSales(totalSales)
                    .totalCancel(totalCancel)
                    .transactionCount(txCount)
                    .feeRate(defaultFeeRate)
                    .build();
            })
            .toList();

        log.info("Settlement batch: found {} merchants for date {}", merchantSummaries.size(), targetDate);
        iterator = merchantSummaries.iterator();
    }
}
