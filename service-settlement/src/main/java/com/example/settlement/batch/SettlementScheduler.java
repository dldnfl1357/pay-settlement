package com.example.settlement.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    /**
     * 매일 01:00에 정산 배치 실행
     */
    @Scheduled(cron = "${settlement.schedule.cron:0 0 1 * * *}")
    public void runDailySettlement() {
        log.info("Starting scheduled settlement batch");

        try {
            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            jobLauncher.run(settlementJob, params);

            log.info("Scheduled settlement batch completed");

        } catch (Exception e) {
            log.error("Scheduled settlement batch failed", e);
        }
    }
}
