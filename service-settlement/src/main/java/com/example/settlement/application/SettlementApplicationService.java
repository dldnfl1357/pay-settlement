package com.example.settlement.application;

import com.example.settlement.application.dto.SettlementResult;
import com.example.settlement.domain.settlement.Settlement;
import com.example.settlement.domain.settlement.SettlementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementApplicationService {

    private final SettlementRepository settlementRepository;

    @Transactional(readOnly = true)
    public List<SettlementResult> getSettlements(Long merchantId, LocalDate startDate, LocalDate endDate) {
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

        return settlements.stream().map(SettlementResult::from).toList();
    }

    @Transactional(readOnly = true)
    public SettlementResult getSettlement(Long id) {
        Settlement settlement = settlementRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Settlement not found: " + id));
        return SettlementResult.from(settlement);
    }

    @Transactional
    public SettlementResult confirmSettlement(Long id) {
        Settlement settlement = settlementRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Settlement not found: " + id));
        settlement.confirm();
        settlementRepository.save(settlement);
        log.info("Settlement confirmed: id={}", id);
        return SettlementResult.from(settlement);
    }

    @Transactional
    public SettlementResult markAsPaid(Long id) {
        Settlement settlement = settlementRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Settlement not found: " + id));
        settlement.markAsPaid();
        settlementRepository.save(settlement);
        log.info("Settlement marked as paid: id={}", id);
        return SettlementResult.from(settlement);
    }
}
