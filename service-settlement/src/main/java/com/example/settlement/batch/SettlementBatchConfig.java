package com.example.settlement.batch;

import com.example.settlement.application.dto.MerchantDailySummary;
import com.example.settlement.domain.paymentsummary.PaymentSummaryRepository;
import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementRepository;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SettlementBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PaymentSummaryRepository paymentSummaryRepository;
    private final SettlementRepository settlementRepository;

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
        return new SettlementItemReader(paymentSummaryRepository);
    }

    @Bean
    public ItemProcessor<MerchantDailySummary, Settlement> settlementProcessor() {
        return new SettlementItemProcessor();
    }

    @Bean
    public ItemWriter<Settlement> settlementWriter() {
        return new SettlementItemWriter(settlementRepository, paymentSummaryRepository);
    }
}
