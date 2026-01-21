package com.example.settlement.batch;

import com.example.settlement.domain.PaymentSummary;
import com.example.settlement.domain.Settlement;
import com.example.settlement.domain.SettlementStatus;
import com.example.settlement.repository.PaymentSummaryRepository;
import com.example.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SettlementBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PaymentSummaryRepository paymentSummaryRepository;
    private final SettlementRepository settlementRepository;

    // 기본 수수료율 (가맹점별 수수료율은 실제로는 가맹점 테이블에서 조회)
    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.025");

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
            .start(settlementStep())
            .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
            .<MerchantDailySummary, Settlement>chunk(100, transactionManager)
            .reader(merchantSummaryReader())
            .processor(settlementProcessor())
            .writer(settlementWriter())
            .build();
    }

    @Bean
    public ItemReader<MerchantDailySummary> merchantSummaryReader() {
        // 전일 데이터 집계
        LocalDate targetDate = LocalDate.now().minusDays(1);

        List<PaymentSummary> summaries = paymentSummaryRepository.findUnsettledByDate(targetDate);

        // 가맹점별로 그룹핑하여 집계
        Map<Long, List<PaymentSummary>> byMerchant = summaries.stream()
            .collect(Collectors.groupingBy(PaymentSummary::getMerchantId));

        List<MerchantDailySummary> merchantSummaries = byMerchant.entrySet().stream()
            .map(entry -> {
                Long merchantId = entry.getKey();
                List<PaymentSummary> merchantPayments = entry.getValue();

                BigDecimal totalSales = merchantPayments.stream()
                    .filter(p -> "APPROVED".equals(p.getStatus()))
                    .map(PaymentSummary::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalCancel = merchantPayments.stream()
                    .filter(p -> "CANCELLED".equals(p.getStatus()))
                    .map(PaymentSummary::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                int txCount = (int) merchantPayments.stream()
                    .filter(p -> "APPROVED".equals(p.getStatus()))
                    .count();

                return MerchantDailySummary.builder()
                    .merchantId(merchantId)
                    .date(targetDate)
                    .totalSales(totalSales)
                    .totalCancel(totalCancel)
                    .transactionCount(txCount)
                    .feeRate(DEFAULT_FEE_RATE)
                    .build();
            })
            .collect(Collectors.toList());

        log.info("Settlement batch: found {} merchants for date {}", merchantSummaries.size(), targetDate);

        return new ListItemReader<>(merchantSummaries);
    }

    @Bean
    public ItemProcessor<MerchantDailySummary, Settlement> settlementProcessor() {
        return summary -> {
            // 수수료 계산: 총 결제액 × 수수료율 (원 미만 절사)
            BigDecimal fee = summary.getTotalSales()
                .multiply(summary.getFeeRate())
                .setScale(0, RoundingMode.DOWN);

            // 순정산액: 총 결제액 - 총 취소액 - 수수료
            BigDecimal netAmount = summary.getTotalSales()
                .subtract(summary.getTotalCancel())
                .subtract(fee);

            log.info("Processing settlement: merchantId={}, sales={}, cancel={}, fee={}, net={}",
                summary.getMerchantId(), summary.getTotalSales(), summary.getTotalCancel(), fee, netAmount);

            return Settlement.builder()
                .merchantId(summary.getMerchantId())
                .settlementDate(summary.getDate())
                .totalSales(summary.getTotalSales())
                .totalCancel(summary.getTotalCancel())
                .totalFee(fee)
                .netAmount(netAmount)
                .transactionCount(summary.getTransactionCount())
                .status(SettlementStatus.PENDING)
                .build();
        };
    }

    @Bean
    public ItemWriter<Settlement> settlementWriter() {
        return items -> {
            for (Settlement settlement : items) {
                // 기존 정산 내역 확인 (중복 방지)
                settlementRepository.findByMerchantIdAndSettlementDate(
                    settlement.getMerchantId(), settlement.getSettlementDate()
                ).ifPresentOrElse(
                    existing -> log.warn("Settlement already exists: merchantId={}, date={}",
                        settlement.getMerchantId(), settlement.getSettlementDate()),
                    () -> {
                        settlementRepository.save(settlement);

                        // 정산 처리 완료된 결제 요약 마킹
                        paymentSummaryRepository.markAsSettled(
                            settlement.getMerchantId(), settlement.getSettlementDate());

                        log.info("Settlement saved: id={}, merchantId={}, date={}, netAmount={}",
                            settlement.getId(), settlement.getMerchantId(),
                            settlement.getSettlementDate(), settlement.getNetAmount());
                    }
                );
            }
        };
    }
}
