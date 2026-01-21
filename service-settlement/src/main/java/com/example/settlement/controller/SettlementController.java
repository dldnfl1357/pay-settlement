package com.example.settlement.controller;

import com.example.settlement.domain.Settlement;
import com.example.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@Slf4j
public class SettlementController {

    private final SettlementRepository settlementRepository;
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    /**
     * 정산 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Settlement>> getSettlements(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<Settlement> settlements;

        if (merchantId != null && startDate != null && endDate != null) {
            settlements = settlementRepository.findByMerchantIdAndSettlementDateBetween(
                merchantId, startDate, endDate);
        } else if (merchantId != null) {
            settlements = settlementRepository.findByMerchantId(merchantId);
        } else if (startDate != null && endDate != null) {
            settlements = settlementRepository.findBySettlementDateBetween(startDate, endDate);
        } else {
            settlements = settlementRepository.findAll();
        }

        return ResponseEntity.ok(settlements);
    }

    /**
     * 정산 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Settlement> getSettlement(@PathVariable Long id) {
        return settlementRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 정산 배치 수동 실행
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> runBatch() {
        try {
            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            log.info("Starting settlement batch manually");
            jobLauncher.run(settlementJob, params);

            return ResponseEntity.ok(Map.of(
                "message", "Settlement batch started",
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("Failed to start settlement batch", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to start batch",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 정산 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Settlement> confirmSettlement(@PathVariable Long id) {
        return settlementRepository.findById(id)
            .map(settlement -> {
                settlement.confirm();
                settlementRepository.save(settlement);
                log.info("Settlement confirmed: id={}", id);
                return ResponseEntity.ok(settlement);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 정산 지급 완료 처리
     */
    @PostMapping("/{id}/paid")
    public ResponseEntity<Settlement> markAsPaid(@PathVariable Long id) {
        return settlementRepository.findById(id)
            .map(settlement -> {
                settlement.markAsPaid();
                settlementRepository.save(settlement);
                log.info("Settlement marked as paid: id={}", id);
                return ResponseEntity.ok(settlement);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
