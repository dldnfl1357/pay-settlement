package com.example.settlement.interfaces.rest;

import com.example.settlement.application.SettlementApplicationService;
import com.example.settlement.application.dto.SettlementResult;
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

    private final SettlementApplicationService settlementService;
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    @GetMapping
    public ResponseEntity<List<SettlementResult>> getSettlements(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SettlementResult> results = settlementService.getSettlements(merchantId, startDate, endDate);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementResult> getSettlement(@PathVariable Long id) {
        SettlementResult result = settlementService.getSettlement(id);
        return ResponseEntity.ok(result);
    }

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

    @PostMapping("/{id}/confirm")
    public ResponseEntity<SettlementResult> confirmSettlement(@PathVariable Long id) {
        SettlementResult result = settlementService.confirmSettlement(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/paid")
    public ResponseEntity<SettlementResult> markAsPaid(@PathVariable Long id) {
        SettlementResult result = settlementService.markAsPaid(id);
        return ResponseEntity.ok(result);
    }
}
